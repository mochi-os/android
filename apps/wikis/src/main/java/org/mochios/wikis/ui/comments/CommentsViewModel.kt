// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.ui.components.quoteInto
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.REOPEN
import org.mochios.wikis.model.WikiComment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.components.WikiInfoLoader
import org.mochios.wikis.ui.page.wikiUpdateNamed
import java.io.File
import javax.inject.Inject

/**
 * UI state for [CommentsScreen]. `replyingTo` / `replyDraft` live here so the
 * recursive thread composables read one source.
 */
data class CommentsUiState(
    val isLoading: Boolean = true,
    val comments: List<WikiComment> = emptyList(),
    /**
     * The server capped the thread page and there are more. Shown as a note
     * under the list, as web does; the app has no "load more" for comments.
     */
    val truncated: Boolean = false,
    val pageTitle: String = "",
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    /** Identity of the signed-in account (used to gate Edit). */
    val currentUserId: String? = null,
    val error: MochiError? = null,

    /** ID of the comment whose reply textarea is currently active, or null. */
    val replyingTo: String? = null,
    /** Working draft for the active reply textarea. */
    val replyDraft: String = "",
    /**
     * Files picked for the comment being written, kept until the server has
     * the comment so a refused one keeps them.
     */
    val files: List<Uri> = emptyList(),
    /** Whether a comment is on its way to the server. */
    val isSending: Boolean = false,
)

sealed interface CommentsEvent {
    data class Error(val error: MochiError) : CommentsEvent
}

/**
 * ViewModel for [CommentsScreen]. Reads `wikiId` and `page` from
 * [SavedStateHandle]; loads the wiki's information and then the comments in
 * [load], and the page's title beside them.
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
    private val webSocket: MochiWebSocket,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private val tracker = UploadTracker()

    /** The files of the comment being sent, for the composer. */
    val upload: StateFlow<Upload?> = tracker.state

    private val _events = Channel<CommentsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var subscription: String? = null

    /** The pending or in-flight comments load; cancelled when a newer one starts. */
    private var commentsJob: Job? = null

    private val information = WikiInfoLoader(
        fetch = { repository.getInfo(wikiId) },
        loaded = { response ->
            _uiState.value = _uiState.value.copy(
                wiki = response.wiki,
                permissions = response.permissions ?: WikiPermissions(),
            )
            watch(response.wiki?.fingerprint ?: wikiId)
        },
    )

    init {
        loadIdentity()
        loadPage()
        load()
    }

    override fun onCleared() {
        super.onCleared()
        subscription?.let { webSocket.unsubscribe(it) }
    }

    /**
     * Watch the wiki so a comment written on another replica appears without
     * leaving the screen. Keyed on the wiki's fingerprint, which is what the
     * server writes on; an entity-id deep link only learns it from the info
     * response, so this runs once that lands. Idempotent.
     */
    private fun watch(fingerprint: String) {
        if (subscription != null || fingerprint.isEmpty()) {
            return
        }
        subscription = webSocket.subscribe(serverUrl, fingerprint, app = "wikis") { event ->
            handle(event)
        }
    }

    /**
     * A change in the wiki. The comments are read again when one of this
     * page's changed, when the page itself was renamed or deleted, and when
     * what changed is not said - including a reopened socket, which missed
     * whatever was sent while it was down.
     */
    private fun handle(event: WebSocketEvent) {
        when (event.type) {
            "wiki/resynced", REOPEN -> refreshComments()
            "wiki/update" -> {
                if (commentsConcerned(event.kind, event.page, event.previous, slug)) {
                    refreshComments()
                }
            }
        }
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            // Capture the signed-in entity so Edit affordances can gate on
            // ownership of each comment — `comment.author == currentUserId`.
            val identity = sessionManager.getBoundIdentity()
            _uiState.value = _uiState.value.copy(currentUserId = identity)
        }
    }

    private fun loadPage() {
        viewModelScope.launch {
            try {
                val response = repository.getPage(wikiId, slug)
                val title = when (response) {
                    is org.mochios.wikis.model.PageFetchResponse.Page -> response.page.title
                    is org.mochios.wikis.model.PageFetchResponse.NotFound -> slug
                }
                _uiState.value = _uiState.value.copy(pageTitle = title)
            } catch (_: Exception) {
                // Title fallback to slug — the comments thread is the main
                // content and is independently fetched below.
                _uiState.value = _uiState.value.copy(pageTitle = slug)
            }
        }
    }

    /**
     * The wiki's information, unless it is already held, then the thread, with
     * the full-screen spinner: on open, and from Retry, so one retry restores
     * both.
     */
    fun load() {
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                information.ensure()
                val response = repository.getComments(wikiId, slug)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    comments = response.comments,
                    truncated = response.truncated,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /**
     * Reload the thread after a change, without the spinner while it has
     * comments to show. Each call cancels the last and waits
     * [REFRESH_DEBOUNCE], so this user's own edit and the `wiki/update` echo
     * it causes make one fetch.
     */
    private fun refreshComments() {
        if (_uiState.value.comments.isEmpty() && _uiState.value.error != null) {
            load()
            return
        }
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            try {
                val response = repository.getComments(wikiId, slug)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    comments = response.comments,
                    truncated = response.truncated,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /** Adds picked files to the comment being written, each once. */
    fun addFiles(uris: List<Uri>) {
        val files = _uiState.value.files
        _uiState.value = _uiState.value.copy(files = files + uris.filterNot { uri -> uri in files })
    }

    /** Takes a picked file off the comment being written. */
    fun removeFile(uri: Uri) {
        _uiState.value = _uiState.value.copy(files = _uiState.value.files - uri)
    }

    /**
     * Stages the picked files, then posts the comment. [onSent] runs once the
     * server has it, so the composer keeps its words until then, and keeps
     * them, with the picked files, when it is refused.
     */
    fun createComment(body: String, parent: String? = null, onSent: () -> Unit = {}) {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || _uiState.value.isSending) return
        val picked = _uiState.value.files
        _uiState.value = _uiState.value.copy(isSending = true)
        viewModelScope.launch {
            try {
                tracker.track(picked) {
                    val files = repository.stageFiles(picked)
                    try {
                        repository.createComment(wikiId, slug, trimmed, parent, files.ifEmpty { null }, tracker)
                    } finally {
                        repository.discardStaged(files)
                    }
                }
                _uiState.value = _uiState.value.copy(files = _uiState.value.files - picked.toSet())
                if (parent != null) cancelReply()
                onSent()
                refreshComments()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    /**
     * Edits a comment's body. [onSaved] runs once the server has the edit, so
     * the edit box stays open, with what was typed, when it is refused.
     */
    fun editComment(id: String, body: String, onSaved: () -> Unit = {}) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.editComment(wikiId, slug, id, trimmed)
                onSaved()
                refreshComments()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun deleteComment(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(wikiId, slug, id)
                refreshComments()
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            }
        }
    }

    // ---------------- reply textarea state ----------------

    fun requestStartReply(commentId: String, selectedText: String? = null) {
        val draft = quoteInto("", selectedText)
        _uiState.value = _uiState.value.copy(
            replyingTo = commentId,
            replyDraft = draft,
        )
    }

    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingTo = null, replyDraft = "")
    }

    fun updateReplyDraft(text: String) {
        _uiState.value = _uiState.value.copy(replyDraft = text)
    }

    /**
     * Submit the current reply draft, with the picked files, as a reply under
     * [parentId]; [onSent] as for [createComment].
     */
    fun submitReply(parentId: String, onSent: () -> Unit = {}) {
        val draft = _uiState.value.replyDraft
        createComment(body = draft, parent = parentId, onSent = onSent)
    }
}

/**
 * Whether a wiki/update frame concerns the comments of the page opened by
 * [opened]: one of its comments changed, the page was renamed from or to it
 * or deleted, an attachment changed (the comments list each one's files), or
 * the frame does not say what changed.
 */
internal fun commentsConcerned(kind: String?, page: String?, previous: String?, opened: String): Boolean =
    when {
        !wikiUpdateNamed(kind) -> true
        kind == "comment" -> page == opened
        kind == "pages" -> page == opened || previous == opened
        kind == "attachment" -> true
        else -> false
    }
