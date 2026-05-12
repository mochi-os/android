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
    val deleted: Boolean = false
)

@HiltViewModel
class PostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository,
    sessionManager: SessionManager
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""
    val postId: String = savedStateHandle["postId"] ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(PostUiState())
    val uiState: StateFlow<PostUiState> = _uiState.asStateFlow()

    init {
        load()
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
}
