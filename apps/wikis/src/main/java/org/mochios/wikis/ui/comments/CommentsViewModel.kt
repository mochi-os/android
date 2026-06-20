// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

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
import org.mochios.android.auth.SessionManager
import org.mochios.wikis.model.WikiComment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import java.io.File
import javax.inject.Inject

/**
 * UI state for [CommentsScreen]. Mirrors web's `PageComments`
 * (`apps/wikis/web/src/features/wiki/page-comments.tsx`) — a flat top-level
 * list of root comments (each with `children`), plus the wiki info needed to
 * gate the compose surface behind the `permissions.edit` flag.
 *
 * `replyingTo` / `replyDraft` live on the ViewModel rather than the screen so
 * the quote-on-select seeding flows through a single source of truth that the
 * recursive [WikiCommentThread] composables can read on the way down.
 */
data class CommentsUiState(
    val isLoading: Boolean = true,
    val comments: List<WikiComment> = emptyList(),
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
)

/**
 * One-shot events surfaced to the screen — snackbar toasts for success /
 * failure feedback. Kept as a channel so the screen consumes each event
 * exactly once even across configuration changes.
 */
sealed interface CommentsEvent {
    data class Toast(val message: String) : CommentsEvent
    data class Error(val error: MochiError) : CommentsEvent
}

/**
 * ViewModel for [CommentsScreen]. Reads `wikiId` and `page` from
 * [SavedStateHandle] (the nav graph wires both as `NavType.StringType`) and
 * fires three parallel loads on init:
 *
 *  1. `loadInfo()` — `/-/info` for the wiki itself (name, permissions,
 *     `WikiInfo.fingerprint`). Drives the title + the `permissions.edit`
 *     gate on the top-level compose form.
 *  2. `loadPage()` — `/-/<slug>` for the page title in the top-app-bar.
 *  3. `loadComments()` — `/-/<slug>/comments` for the thread itself.
 *
 * Mutation methods (`createComment`, `editComment`, `deleteComment`) refresh
 * the thread after every successful round-trip — web does the same via
 * React Query's invalidation. Errors emit a [CommentsEvent.Error] for the
 * screen's snackbar.
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private val _events = Channel<CommentsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadIdentity()
        loadInfo()
        loadPage()
        loadComments()
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            // Capture the signed-in entity so Edit affordances can gate on
            // ownership of each comment — `comment.author == currentUserId`.
            _uiState.value = _uiState.value.copy(
                currentUserId = sessionManager.getBoundIdentity(),
            )
        }
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
                // Non-fatal: the screen still renders, just without a fully
                // populated WikiContextValue — the comment refresh below will
                // surface the real error.
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
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

    fun loadComments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.getComments(wikiId, slug)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    comments = response.comments,
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

    /**
     * Create a new comment. When [parent] is null the comment is a top-level
     * post; otherwise it's a reply to the existing comment with that id.
     *
     * Refreshes the thread on success so the new comment appears (and, for
     * replies, the reply textarea closes via [cancelReply]).
     */
    fun createComment(body: String, parent: String? = null, files: List<File>? = null) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.createComment(wikiId, slug, trimmed, parent, files)
                if (parent != null) cancelReply()
                loadComments()
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun editComment(id: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.editComment(wikiId, slug, id, trimmed)
                loadComments()
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun deleteComment(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(wikiId, slug, id)
                loadComments()
            } catch (e: Exception) {
                _events.send(CommentsEvent.Error(e.toMochiError()))
            }
        }
    }

    // ---------------- reply textarea state ----------------

    /**
     * Open the reply textarea under [commentId]. If [selectedText] is non-null
     * and non-blank the textarea is pre-seeded with each selected line
     * prefixed by `"> "`, plus a trailing blank line, mirroring web's
     * `window.getSelection()` quote-on-select behaviour in `page-comments.tsx`.
     */
    fun requestStartReply(commentId: String, selectedText: String? = null) {
        val draft = selectedText?.trim()?.takeIf { it.isNotEmpty() }?.let { sel ->
            sel.lineSequence().joinToString("\n") { "> $it" } + "\n\n"
        } ?: ""
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
     * Submit the current reply draft as a reply under [parentId]. Files are
     * the optional list of attachments picked through the reply form.
     */
    fun submitReply(parentId: String, files: List<File>? = null) {
        val draft = _uiState.value.replyDraft
        createComment(body = draft, parent = parentId, files = files)
    }
}
