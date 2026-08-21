// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.attachments

import android.content.Context
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
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import java.io.File
import javax.inject.Inject

/**
 * View modes the grid/list toggle switches between. Mirrors the
 * `viewMode` state in `apps/wikis/web/src/features/wiki/attachments-page.tsx`.
 */
enum class AttachmentsViewMode { GRID, LIST }

/**
 * Filter dropdown options. `ALL` shows everything; `IMAGES` keeps only
 * `image/` MIME types; `DOCUMENTS` keeps the inverse.
 */
enum class AttachmentsFilter { ALL, IMAGES, DOCUMENTS }

/**
 * Sort dropdown options. Date is newest-first, name is locale-undefined
 * via `naturalCompare`, size is largest-first.
 */
enum class AttachmentsSort { DATE, NAME, SIZE }

data class AttachmentsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isUploading: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,

    // Filter / sort / view controls
    val viewMode: AttachmentsViewMode = AttachmentsViewMode.GRID,
    val filter: AttachmentsFilter = AttachmentsFilter.ALL,
    val sort: AttachmentsSort = AttachmentsSort.DATE,
    val searchQuery: String = "",

    /** Attachment currently pending a delete-confirm dialog. */
    val pendingDelete: Attachment? = null,
    /** ID of the attachment currently being deleted. */
    val deletingId: String? = null,
    /** Attachment currently open in the caption editor dialog. */
    val captioning: Attachment? = null,
)

sealed interface AttachmentsEvent {
    data class Toast(val message: String) : AttachmentsEvent
    data class Error(val error: MochiError) : AttachmentsEvent
}

/**
 * ViewModel for [AttachmentsScreen]. Reads `wikiId` and `page` from
 * [SavedStateHandle]; filter, sort and view mode live here so they survive
 * configuration changes.
 */
@HiltViewModel
class AttachmentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    /**
     * Bearer token for this session, so the screen can add it to a
     * [DownloadManager] request without touching [SessionManager].
     */
    val token: String? = sessionManager.getTokenBlocking("wikis")

    private val _uiState = MutableStateFlow(AttachmentsUiState())
    val uiState: StateFlow<AttachmentsUiState> = _uiState.asStateFlow()

    private val _events = Channel<AttachmentsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadInfo()
        loadAttachments()
    }

    private fun loadInfo() {
        viewModelScope.launch {
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = _uiState.value.copy(
                    wiki = response.wiki,
                    permissions = response.permissions ?: WikiPermissions(),
                )
            } catch (e: Exception) {
                // Non-fatal: the attachments load below will surface the real
                // error if both fail. The screen renders an inline retry once
                // [uiState.error] is set and the list is empty.
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Initial / retry load of the attachments list. */
    fun loadAttachments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val list = repository.getAttachments(wikiId, slug)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    attachments = list,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Pull-to-refresh handler. Keeps the existing list visible while loading. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val list = repository.getAttachments(wikiId, slug)
                _uiState.value = _uiState.value.copy(
                    attachments = list,
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
                _events.send(AttachmentsEvent.Error(e.toMochiError()))
            }
        }
    }

    // ---------------- Filter / sort / view-mode setters ----------------

    fun setViewMode(mode: AttachmentsViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun setFilter(filter: AttachmentsFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun setSort(sort: AttachmentsSort) {
        _uiState.value = _uiState.value.copy(sort = sort)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchQuery = "")
    }

    // ---------------- Upload ----------------

    /**
     * Upload files picked by the system picker. Staging keeps each file's real
     * name, which is what the server records.
     */
    fun uploadAttachments(
        uris: List<Uri>,
        uploadFailed: String,
        uploadSuccess: String,
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val tempFiles = repository.stageFiles(uris)
            try {
                val uploaded = repository.uploadAttachments(wikiId, tempFiles)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    attachments = mergeAttachments(_uiState.value.attachments, uploaded),
                )
                _events.send(AttachmentsEvent.Toast(uploadSuccess))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploading = false)
                _events.send(AttachmentsEvent.Toast(e.toMochiError().messageOrFallback(uploadFailed)))
            } finally {
                repository.discardStaged(tempFiles)
            }
        }
    }

    // ---------------- Delete ----------------

    /** Open the delete confirmation dialog for [attachment]. */
    fun requestDelete(attachment: Attachment) {
        _uiState.value = _uiState.value.copy(pendingDelete = attachment)
    }

    /** Cancel the pending delete (e.g. dismiss the confirm dialog). */
    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = null)
    }

    /**
     * Confirm the pending delete. Removes the row from local state on success
     * (so the grid/list updates immediately) and emits a success toast.
     */
    fun confirmDelete(deleteSuccess: String, deleteFailed: String) {
        val attachment = _uiState.value.pendingDelete ?: return
        _uiState.value = _uiState.value.copy(deletingId = attachment.id)
        viewModelScope.launch {
            try {
                repository.deleteAttachment(wikiId, attachment.id)
                _uiState.value = _uiState.value.copy(
                    attachments = _uiState.value.attachments.filterNot { it.id == attachment.id },
                    pendingDelete = null,
                    deletingId = null,
                )
                _events.send(AttachmentsEvent.Toast(deleteSuccess))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    pendingDelete = null,
                    deletingId = null,
                )
                _events.send(AttachmentsEvent.Toast(e.toMochiError().messageOrFallback(deleteFailed)))
            }
        }
    }

    // ---------------- Caption ----------------

    /** Open the caption editor for [attachment]. */
    fun requestCaption(attachment: Attachment) {
        _uiState.value = _uiState.value.copy(captioning = attachment)
    }

    /** Close the caption editor without saving. */
    fun cancelCaption() {
        _uiState.value = _uiState.value.copy(captioning = null)
    }

    fun saveCaption(caption: String, saveFailed: String) {
        val attachment = _uiState.value.captioning ?: return
        _uiState.value = _uiState.value.copy(captioning = null)
        if (caption == attachment.caption) return
        viewModelScope.launch {
            try {
                val updated = repository.updateAttachment(wikiId, attachment.id, caption)
                _uiState.value = _uiState.value.copy(
                    attachments = _uiState.value.attachments.map {
                        if (it.id == attachment.id) it.copy(caption = updated.caption) else it
                    },
                )
            } catch (e: Exception) {
                _events.send(AttachmentsEvent.Toast(e.toMochiError().messageOrFallback(saveFailed)))
            }
        }
    }

    // ---------------- Helpers ----------------

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
