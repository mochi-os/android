// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.forums.model.Forum
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.Post
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class PostUiState(
    val forum: Forum = Forum(),
    val post: Post = Post(),
    val comments: List<ForumComment> = emptyList(),
    val canVote: Boolean = false,
    val canComment: Boolean = false,
    val canModerate: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSending: Boolean = false,
    val error: MochiError? = null,
    val replyTo: ForumComment? = null,
    val deleted: Boolean = false,
    /** Bound identity for the current session — used to gate the Edit
     *  menu items on (author == me) || canModerate. */
    val identity: String = "",
)

@HiltViewModel
class PostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository,
    private val sessionManager: SessionManager,
    private val webSocket: MochiWebSocket,
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""
    val postId: String = savedStateHandle["postId"] ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(PostUiState())
    val uiState: StateFlow<PostUiState> = _uiState.asStateFlow()

    private var subscriptionId: String? = null

    init {
        viewModelScope.launch {
            val id = sessionManager.getBoundIdentity().orEmpty()
            _uiState.value = _uiState.value.copy(identity = id)
        }
        load()
    }

    private fun subscribeWebSocket(forumKey: String) {
        if (forumKey.isBlank() || subscriptionId != null) return
        subscriptionId = webSocket.subscribe(sessionManager.getServerUrlBlocking(), forumKey) { _ ->
            viewModelScope.launch {
                try {
                    val r = repository.viewPost(forumId, postId)
                    _uiState.value = _uiState.value.copy(
                        forum = r.forum,
                        post = r.post,
                        comments = r.comments,
                    )
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val r = repository.viewPost(forumId, postId)
                _uiState.value = _uiState.value.copy(
                    forum = r.forum,
                    post = r.post,
                    comments = r.comments,
                    canVote = r.can_vote,
                    canComment = r.can_comment,
                    canModerate = r.can_moderate,
                    isLoading = false
                )
                subscribeWebSocket(r.forum.fingerprint)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val r = repository.viewPost(forumId, postId)
                _uiState.value = _uiState.value.copy(
                    forum = r.forum,
                    post = r.post,
                    comments = r.comments,
                    canVote = r.can_vote,
                    canComment = r.can_comment,
                    canModerate = r.can_moderate,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    fun votePost(vote: String) {
        viewModelScope.launch {
            try {
                repository.votePost(forumId, postId, vote)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun voteComment(commentId: String, vote: String) {
        viewModelScope.launch {
            try {
                repository.voteComment(forumId, postId, commentId, vote)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun setReplyTo(comment: ForumComment?) {
        _uiState.value = _uiState.value.copy(replyTo = comment)
    }

    fun submitComment(body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                val parent = _uiState.value.replyTo?.id
                repository.createComment(forumId, postId, trimmed, parent)
                _uiState.value = _uiState.value.copy(replyTo = null)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun deletePost() {
        viewModelScope.launch {
            try {
                repository.deletePost(forumId, postId)
                _uiState.value = _uiState.value.copy(deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(forumId, postId, commentId)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun editComment(commentId: String, newBody: String) {
        viewModelScope.launch {
            try {
                repository.editComment(forumId, postId, commentId, newBody)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun editCommentWithAttachments(
        commentId: String,
        newBody: String,
        keptAttachmentIds: List<String>,
        newFileUris: List<android.net.Uri>,
        contentResolver: android.content.ContentResolver,
    ) {
        viewModelScope.launch {
            try {
                repository.editCommentFromUris(
                    forumId, postId, commentId,
                    newBody, keptAttachmentIds, newFileUris, contentResolver
                )
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun editPost(newTitle: String, newBody: String) {
        viewModelScope.launch {
            try {
                repository.editPost(forumId, postId, newTitle, newBody)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun pinPost() = moderate { repository.pinPost(forumId, postId) }
    fun unpinPost() = moderate { repository.unpinPost(forumId, postId) }
    fun lockPost() = moderate { repository.lockPost(forumId, postId) }
    fun unlockPost() = moderate { repository.unlockPost(forumId, postId) }
    fun approvePost() = moderate { repository.approvePost(forumId, postId) }
    fun removePost() = moderate { repository.removePost(forumId, postId) }
    fun restorePost() = moderate { repository.restorePost(forumId, postId) }
    fun reportPost(reason: String, details: String) =
        moderate { repository.reportPost(forumId, postId, reason, details) }

    fun removeComment(commentId: String) =
        moderate { repository.removeComment(forumId, postId, commentId) }
    fun restoreComment(commentId: String) =
        moderate { repository.restoreComment(forumId, postId, commentId) }
    fun approveComment(commentId: String) =
        moderate { repository.approveComment(forumId, postId, commentId) }
    fun reportComment(commentId: String, reason: String, details: String) =
        moderate { repository.reportComment(forumId, postId, commentId, reason, details) }

    fun addPostTag(label: String) = moderate {
        repository.addPostTag(forumId, postId, label)
    }

    fun removePostTag(tagId: String) = moderate {
        repository.removePostTag(forumId, postId, tagId)
    }

    fun adjustTagInterest(qid: String, direction: String) {
        viewModelScope.launch {
            try {
                repository.adjustTagInterest(forumId, qid, direction)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun moderate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
