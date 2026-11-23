// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.messageOrFallback
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.android.util.slugify
import org.mochios.android.util.slugifyPartial
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.components.WikiInfoLoader
import javax.inject.Inject

data class PageEditorUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isUploading: Boolean = false,
    val isAttachmentsLoading: Boolean = false,
    val title: String = "",
    /** The page's slug: as the user types it for a new page, as loaded for an existing one. */
    val slug: String = "",
    /** The title and content an existing page was loaded with, which edits are measured against. */
    val originalTitle: String = "",
    val originalContent: String = "",
    val content: String = "",
    val comment: String = "",
    /** The page was saved: what the editor holds is the page's, with nothing left to lose. */
    val saved: Boolean = false,
    val showPreview: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    /** The wiki the page belongs to, once `/-/info` has answered. */
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
)

/**
 * Whether leaving the editor would lose what was typed, by the web editor's
 * rule: a new page holds anything once its title or content is not blank; an
 * existing page once its title, trimmed, or its content differs from what was
 * loaded. The edit summary does not count, and nothing is unsaved once the
 * page is saved.
 */
internal fun unsaved(isNew: Boolean, state: PageEditorUiState): Boolean = when {
    state.saved -> false
    isNew -> state.title.isNotBlank() || state.content.isNotBlank()
    else -> state.title.trim() != state.originalTitle.trim() || state.content != state.originalContent
}

/**
 * One-shot events for the editor screen. Collected once and not replayed
 * across recompositions.
 */
sealed interface PageEditorEvent {
    /** Show a success toast then navigate to the saved/created page. */
    data class Saved(val slug: String) : PageEditorEvent

    /** Show a success toast then navigate to the wiki home. */
    object Deleted : PageEditorEvent

    /** Show a toast with a localised message. */
    data class Toast(val message: String) : PageEditorEvent
}

@HiltViewModel
class PageEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle["wikiId"] ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')
    private val initialSlug: String? = savedStateHandle["page"]
    val isNew: Boolean = initialSlug == null
    /**
     * A slug the route suggests for a new page — the wiki's home when this is
     * the wiki's first page. The field stays editable; this only saves the
     * user typing what the wiki already expects.
     */
    private val suggestedSlug: String = savedStateHandle["slug"] ?: ""

    private val _uiState = MutableStateFlow(
        PageEditorUiState(
            // For new pages we seed the slug field with an empty string;
            // the user types one. For edits we don't expose a slug field,
            // but stash the route slug so save calls have it on hand.
            slug = initialSlug ?: suggestedSlug,
            isLoading = !isNew,
        )
    )
    val uiState: StateFlow<PageEditorUiState> = _uiState.asStateFlow()

    private val tracker = UploadTracker()

    /** The files being uploaded from the insert dialog, while they go. */
    val upload: StateFlow<Upload?> = tracker.state

    private val _events = Channel<PageEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * The wiki behind the page, for the context the preview renders inside -
     * markdown resolves attachment URLs against it - and for whether this user
     * may delete. An existing page loads it with the page, so a failure shows
     * with the page's Retry rather than silently leaving Delete out.
     */
    private val information = WikiInfoLoader(
        fetch = { repository.getInfo(wikiId) },
        loaded = { response ->
            _uiState.value = _uiState.value.copy(
                wiki = response.wiki,
                permissions = response.permissions ?: WikiPermissions(),
            )
        },
    )

    init {
        if (isNew) {
            loadWiki()
        } else {
            loadPage()
        }
    }

    /**
     * A new page's wiki. It has nothing to delete, and writes and saves
     * without it, so a failure leaves the defaults.
     */
    private fun loadWiki() {
        viewModelScope.launch {
            try {
                information.ensure()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** Reload the page and its wiki after a failure. A new page asks for its wiki again. */
    fun retry() {
        if (isNew) loadWiki() else loadPage()
    }

    private fun loadPage() {
        val slug = initialSlug ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                information.ensure()
                when (val r = repository.getPage(wikiId, slug)) {
                    is PageFetchResponse.Page -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            title = r.page.title,
                            originalTitle = r.page.title,
                            content = r.page.content,
                            originalContent = r.page.content,
                            // The page a redirect served may have another
                            // slug than the route's; saving and deleting act
                            // on the page shown.
                            slug = r.page.slug.ifEmpty { slug },
                        )
                    }
                    is PageFetchResponse.NotFound -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = MochiError.NotFoundError(),
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    // ---- Form mutations ----

    /**
     * Whether the address is the user's own rather than something derived. A
     * slug the route suggested counts as set, so typing a title never
     * overwrites the page the caller asked to create.
     */
    private var slugEdited: Boolean = suggestedSlug.isNotBlank()

    fun setTitle(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            title = value,
            // A new page's address follows its title until the user takes the
            // field over; an existing page's address never moves.
            slug = if (isNew && !slugEdited) slugify(value) else current.slug,
        )
    }

    fun setSlug(value: String) {
        slugEdited = true
        // Typed input is held to the same alphabet, but a trailing hyphen is
        // left alone - it is a word separator the user has not finished yet.
        _uiState.value = _uiState.value.copy(slug = slugifyPartial(value))
    }
    fun setContent(value: String) { _uiState.value = _uiState.value.copy(content = value) }
    fun setComment(value: String) { _uiState.value = _uiState.value.copy(comment = value) }
    fun togglePreview() {
        _uiState.value = _uiState.value.copy(showPreview = !_uiState.value.showPreview)
    }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun insertAtCursor(snippet: String, cursor: Int): Int {
        val current = _uiState.value.content
        val safe = cursor.coerceIn(0, current.length)
        val next = current.substring(0, safe) + snippet + current.substring(safe)
        _uiState.value = _uiState.value.copy(content = next)
        return safe + snippet.length
    }

    // ---- Save / create / delete ----

    fun save(
        invalidTitle: String,
        invalidSlug: String,
        createFailed: String,
        editFailed: String,
    ) {
        val state = _uiState.value
        val title = state.title.trim()
        if (title.isEmpty()) {
            viewModelScope.launch { _events.send(PageEditorEvent.Toast(invalidTitle)) }
            return
        }
        if (isNew) {
            val slug = state.slug.trim()
            if (slug.isEmpty()) {
                viewModelScope.launch { _events.send(PageEditorEvent.Toast(invalidSlug)) }
                return
            }
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isSaving = true, error = null)
                try {
                    val r = repository.createPage(wikiId, slug, title, state.content)
                    _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                    _events.send(PageEditorEvent.Saved(r.slug.ifEmpty { slug }))
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.send(PageEditorEvent.Toast(e.toMochiError().messageOrFallback(createFailed)))
                }
            }
        } else {
            // The page as loaded, not the route's slug: a redirect may have
            // served another page, and saving under the route's name would
            // make a second page the redirect hides.
            val slug = state.slug.ifEmpty { return }
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isSaving = true, error = null)
                try {
                    val r = repository.editPage(wikiId, slug, title, state.content, state.comment.trim())
                    _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                    // The server answers with the page it saved.
                    _events.send(PageEditorEvent.Saved(r.slug.ifEmpty { slug }))
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.send(PageEditorEvent.Toast(e.toMochiError().messageOrFallback(editFailed)))
                }
            }
        }
    }

    fun delete(deleteFailed: String) {
        if (isNew) return
        // The page as loaded, which the confirmation names.
        val slug = _uiState.value.slug.ifEmpty { return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deletePage(wikiId, slug)
                _uiState.value = _uiState.value.copy(isDeleting = false)
                _events.send(PageEditorEvent.Deleted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDeleting = false)
                _events.send(PageEditorEvent.Toast(e.toMochiError().messageOrFallback(deleteFailed)))
            }
        }
    }

    // ---- Attachments (for InsertAttachmentDialog) ----

    /**
     * The wiki's files, for the insert dialog to pick from. Attachments belong
     * to the wiki, not to one page - the list route takes a page only to keep
     * the shape the web client uses, and ignores it - so a page that has no
     * address yet still has files to insert. Bailing out on an empty slug left
     * the dialog empty the first time it was opened on a page being written.
     */
    fun loadAttachments() {
        val slug = _uiState.value.slug.ifEmpty { initialSlug.orEmpty() }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAttachmentsLoading = true)
            try {
                val list = repository.getAttachments(wikiId, slug)
                _uiState.value = _uiState.value.copy(
                    attachments = list,
                    isAttachmentsLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAttachmentsLoading = false)
                _events.send(PageEditorEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }

    /**
     * Uploads are staged as temp files so the repository stays file-based; the
     * copy keeps the original filename, which is what the server records.
     *
     * The upload route names a wiki and no page, so a page still being written
     * uploads as well as a saved one does. Turning the pick away on an empty
     * slug left the dialog's own upload button doing nothing at all, with
     * nothing said about why.
     */
    fun uploadAttachments(
        uris: List<Uri>,
        uploadFailed: String,
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            tracker.track {
                try {
                    val tempFiles = repository.stageFiles(uris)
                    try {
                        val uploaded = repository.uploadAttachments(wikiId, tempFiles, tracker)
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            attachments = mergeAttachments(_uiState.value.attachments, uploaded),
                        )
                    } finally {
                        repository.discardStaged(tempFiles)
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    _events.send(PageEditorEvent.Toast(e.toMochiError().messageOrFallback(uploadFailed)))
                }
            }
        }
    }

    private fun mergeAttachments(
        existing: List<Attachment>,
        added: List<Attachment>,
    ): List<Attachment> {
        if (added.isEmpty()) return existing
        val ids = existing.map { it.id }.toSet()
        return existing + added.filter { it.id !in ids }
    }
}
