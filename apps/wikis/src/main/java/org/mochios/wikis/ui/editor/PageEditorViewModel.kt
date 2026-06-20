// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.editor

import android.content.ContentResolver
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
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.repository.WikisRepository
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Editor state. Holds the form fields, derived flags, and the most-recent
 * cursor position inside the body textarea so an inline insert from
 * [InsertAttachmentDialog] knows where to splice the markdown snippet.
 *
 * Mirrors `apps/wikis/web/src/features/wiki/page-editor.tsx`'s local state.
 */
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

/**
 * ViewModel for [PageEditorScreen].
 *
 * Reads `wikiId` and the optional `page` slug from [SavedStateHandle] —
 * the new-page route omits the slug, the edit route supplies it.
 *
 * The "new" boolean is derived from the presence of the slug arg, matching
 * the web `isNew` prop in `page-editor.tsx`. The repository calls map onto
 * the existing [WikisRepository.editPage] / [WikisRepository.createPage] /
 * [WikisRepository.deletePage] flows.
 */
@HiltViewModel
class PageEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle["wikiId"] ?: ""
    private val initialSlug: String? = savedStateHandle["page"]
    val isNew: Boolean = initialSlug == null

    private val _uiState = MutableStateFlow(
        PageEditorUiState(
            // For new pages we seed the slug field with an empty string;
            // the user types one. For edits we don't expose a slug field,
            // but stash the route slug so save calls have it on hand.
            slug = initialSlug ?: "",
            isLoading = !isNew,
        )
    )
    val uiState: StateFlow<PageEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<PageEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (!isNew) {
            loadPage()
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

    fun setTitle(value: String) { _uiState.value = _uiState.value.copy(title = value) }
    fun setSlug(value: String) { _uiState.value = _uiState.value.copy(slug = value) }
    fun setContent(value: String) { _uiState.value = _uiState.value.copy(content = value) }
    fun setComment(value: String) { _uiState.value = _uiState.value.copy(comment = value) }
    fun togglePreview() {
        _uiState.value = _uiState.value.copy(showPreview = !_uiState.value.showPreview)
    }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    /**
     * Splice [snippet] at [cursor] in the current body content. Used after
     * the insert-attachment dialog dismisses. Returns the new cursor
     * position so the caller can move the text-field selection past the
     * inserted text — mirrors the web `insertMarkdown` helper.
     */
    fun insertAtCursor(snippet: String, cursor: Int): Int {
        val current = _uiState.value.content
        val safe = cursor.coerceIn(0, current.length)
        val next = current.substring(0, safe) + snippet + current.substring(safe)
        _uiState.value = _uiState.value.copy(content = next)
        return safe + snippet.length
    }

    // ---- Save / create / delete ----

    /**
     * Persist the current draft. Dispatches to [WikisRepository.editPage]
     * or [WikisRepository.createPage] based on [isNew]. On success emits a
     * [PageEditorEvent.Saved] for the screen to handle navigation.
     */
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

    /**
     * Delete the page being edited. Only meaningful when [isNew] is false —
     * the caller should hide the trigger otherwise. Emits
     * [PageEditorEvent.Deleted] so the screen can navigate to wiki home.
     */
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

    /**
     * List the page's existing attachments for the insert dialog. The
     * web equivalent uses a React-Query hook; here we expose a one-shot
     * load + refresh.
     */
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
     * Upload one or more files picked by the system file picker, then
     * refresh the attachments list. URIs are materialised into temp files
     * via the supplied [ContentResolver]; we deliberately copy the bytes
     * rather than streaming straight to the multipart body so the repository
     * stays file-based (matches the existing comment-attachments flow).
     */
    fun uploadAttachments(
        uris: List<Uri>,
        contentResolver: ContentResolver,
        cacheDir: File,
        uploadFailed: String,
    ) {
        if (uris.isEmpty()) return
        // Attachments are wiki-scoped on the server, but the editor only allows
        // uploads once the page exists (matching web), so guard on a known slug.
        if (_uiState.value.slug.isEmpty() && initialSlug == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val tempFiles = mutableListOf<File>()
            try {
                for (uri in uris) {
                    val name = displayName(contentResolver, uri) ?: uri.lastPathSegment ?: "file"
                    val temp = File(cacheDir, "wiki_upload_${System.nanoTime()}_$name")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { out -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Cannot open $uri")
                    tempFiles.add(temp)
                }
                val uploaded = repository.uploadAttachments(wikiId, tempFiles)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    attachments = mergeAttachments(_uiState.value.attachments, uploaded),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploading = false)
                _events.send(PageEditorEvent.Toast(e.toMochiError().messageOrFallback(uploadFailed)))
            } finally {
                tempFiles.forEach { runCatching { it.delete() } }
            }
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
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

/**
 * Map a [MochiError] to a user-facing string, preferring a server-supplied
 * message when present and falling back to the caller-supplied localised
 * default. Network errors always render the lib's generic network string,
 * matching the rest of the app's error UX.
 */
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
