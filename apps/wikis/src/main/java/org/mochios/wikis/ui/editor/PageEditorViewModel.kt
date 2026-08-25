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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.android.util.slugify
import org.mochios.android.util.slugifyPartial
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

data class PageEditorUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isUploading: Boolean = false,
    val isAttachmentsLoading: Boolean = false,
    val title: String = "",
    val slug: String = "",
    val originalTitle: String = "",
    val content: String = "",
    val comment: String = "",
    val showPreview: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    /** The wiki the page belongs to, once `/-/info` has answered. */
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
)

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

    private val _events = Channel<PageEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadWiki()
        if (!isNew) {
            loadPage()
        }
    }

    /**
     * The wiki behind the page, for the context the preview renders inside -
     * markdown resolves attachment URLs against it - and for whether this user
     * may delete. A failure leaves both at their defaults: the editor still
     * writes and saves perfectly well without them.
     */
    private fun loadWiki() {
        viewModelScope.launch {
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = _uiState.value.copy(
                    wiki = response.wiki,
                    permissions = response.permissions ?: WikiPermissions(),
                )
            } catch (_: Exception) {
                // Nothing to say: the page itself reports its own failures.
            }
        }
    }

    /** Reload the page after a transient failure. No-op for new pages. */
    fun retry() {
        if (!isNew) loadPage()
    }

    private fun loadPage() {
        val slug = initialSlug ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                when (val r = repository.getPage(wikiId, slug)) {
                    is PageFetchResponse.Page -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            title = r.page.title,
                            originalTitle = r.page.title,
                            content = r.page.content,
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
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.send(PageEditorEvent.Saved(r.slug.ifEmpty { slug }))
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.send(PageEditorEvent.Toast(e.toMochiError().messageOrFallback(createFailed)))
                }
            }
        } else {
            val slug = initialSlug ?: return
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isSaving = true, error = null)
                try {
                    repository.editPage(wikiId, slug, title, state.content, state.comment.trim())
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.send(PageEditorEvent.Saved(slug))
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.send(PageEditorEvent.Toast(e.toMochiError().messageOrFallback(editFailed)))
                }
            }
        }
    }

    fun delete(deleteFailed: String) {
        if (isNew) return
        val slug = initialSlug ?: return
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

    fun loadAttachments() {
        val slug = _uiState.value.slug.ifEmpty { initialSlug ?: return }
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
     */
    fun uploadAttachments(
        uris: List<Uri>,
        uploadFailed: String,
    ) {
        if (uris.isEmpty()) return
        // Attachments are wiki-scoped on the server, but the editor only allows
        // uploads once the page exists (matching web), so guard on a known slug.
        if (_uiState.value.slug.isEmpty() && initialSlug == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val tempFiles = repository.stageFiles(uris)
            try {
                val uploaded = repository.uploadAttachments(wikiId, tempFiles)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    attachments = mergeAttachments(_uiState.value.attachments, uploaded),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploading = false)
                _events.send(PageEditorEvent.Toast(e.toMochiError().messageOrFallback(uploadFailed)))
            } finally {
                repository.discardStaged(tempFiles)
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

private fun MochiError.messageOrFallback(fallback: String): String {
    return when (this) {
        is MochiError.AuthError -> message ?: fallback
        is MochiError.ForbiddenError -> message ?: fallback
        is MochiError.NotFoundError -> message ?: fallback
        is MochiError.ServerError -> message ?: fallback
        is MochiError.Unknown -> message ?: fallback
        is MochiError.NetworkError -> userMessage()
        is MochiError.Local -> userMessage()
    }
}
