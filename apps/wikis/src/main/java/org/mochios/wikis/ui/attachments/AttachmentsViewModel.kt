package org.mochios.wikis.ui.attachments

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
import org.mochios.android.auth.SessionManager
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import java.io.File
import java.io.FileOutputStream
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

/**
 * UI state for [AttachmentsScreen]. Mirrors the local state held by web's
 * `AttachmentsPage` plus the loaded wiki info that backs the per-wiki
 * `baseURL` used in thumbnail / download URLs.
 */
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
)

/**
 * One-shot events surfaced to the screen — toast / snackbar messages for
 * success / failure feedback. Kept as a channel so the screen consumes each
 * event exactly once even across configuration changes.
 */
sealed interface AttachmentsEvent {
    data class Toast(val message: String) : AttachmentsEvent
    data class Error(val error: MochiError) : AttachmentsEvent
}

/**
 * ViewModel for [AttachmentsScreen]. Reads `wikiId` and `page` from
 * [SavedStateHandle] (the nav graph wires both as `NavType.StringType`) and
 * fires two parallel loads on init:
 *
 *  1. `loadInfo()` — `/-/info` for the wiki itself (needed for the
 *     `WikiContextValue.baseURL` that the screen passes to `AsyncImage`
 *     and `DownloadManager`).
 *  2. `loadAttachments()` — `/-/<slug>/attachments` for the list itself.
 *
 * Mutation methods (`uploadAttachments`, `deleteAttachment`) refresh the list
 * after every successful round-trip — web does the same via React Query's
 * invalidation. Errors emit an [AttachmentsEvent.Error] for the screen's
 * snackbar.
 *
 * Filter / sort / view-mode state lives on the ViewModel so configuration
 * changes (rotation, theme switch) preserve user intent without round-
 * tripping through the URL — Android navigation arguments are typed and
 * the list is too dynamic to encode there.
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
     * Bearer token for this app's session. Captured here so the screen can
     * thread it into a `DownloadManager.Request.addRequestHeader` without
     * touching [SessionManager] from the UI layer. `null` if the user is
     * somehow on this screen without a token — the request will 401 and
     * the system download notification will surface the failure.
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
     * Upload one or more files picked by the system file picker. Mirrors
     * `PageEditorViewModel.uploadAttachments` — copies each URI to a temp
     * file under the cache dir so the repository call stays file-based
     * (matches the comment-attachments and editor flows), then refreshes
     * the list on success.
     */
    fun uploadAttachments(
        uris: List<Uri>,
        contentResolver: ContentResolver,
        cacheDir: File,
        uploadFailed: String,
        uploadSuccess: String,
    ) {
        if (uris.isEmpty()) return
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
                val uploaded = repository.uploadAttachments(wikiId, slug, tempFiles)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    attachments = mergeAttachments(_uiState.value.attachments, uploaded),
                )
                _events.send(AttachmentsEvent.Toast(uploadSuccess))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isUploading = false)
                _events.send(AttachmentsEvent.Toast(e.toMochiError().messageOrFallback(uploadFailed)))
            } finally {
                tempFiles.forEach { runCatching { it.delete() } }
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

    // ---------------- Helpers ----------------

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
 * default. Mirrors the helper in PageEditorViewModel.
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
