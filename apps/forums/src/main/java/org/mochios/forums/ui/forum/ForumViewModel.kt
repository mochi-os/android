package org.mochios.forums.ui.forum

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
import org.mochios.forums.api.ForumTagCount
import org.mochios.forums.model.Forum
import org.mochios.forums.model.Post
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class ForumUiState(
    val forum: Forum = Forum(),
    val posts: List<Post> = emptyList(),
    val canManage: Boolean = false,
    val canModerate: Boolean = false,
    val sort: String = "",
    val hasMore: Boolean = false,
    val nextCursor: Long? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: MochiError? = null,
    val tags: List<ForumTagCount> = emptyList(),
    val currentTag: String? = null,
)

@HiltViewModel
class ForumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _uiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = _uiState.asStateFlow()

    private var subscriptionId: String? = null

    init {
        load()
        loadTags()
    }

    private fun subscribeWebSocket(forumKey: String) {
        if (forumKey.isBlank() || subscriptionId != null) return
        val serverUrl = sessionManager.getServerUrlBlocking()
        subscriptionId = webSocket.subscribe(serverUrl, forumKey) { _ ->
            // forums.star broadcasts post/*, comment/*, tag/* — every event
            // is a hint that the visible post list / tag chip counts may have
            // changed. Refresh silently so the user sees the update without a
            // spinner. (Could be narrowed per-event later if profiling
            // shows it's too eager.)
            viewModelScope.launch {
                try {
                    val r = repository.viewForum(forumId, sort = _uiState.value.sort.ifEmpty { null }, tag = _uiState.value.currentTag)
                    _uiState.value = _uiState.value.copy(
                        forum = r.forum,
                        posts = r.posts,
                        canManage = r.can_manage,
                        canModerate = r.can_moderate,
                        hasMore = r.hasMore,
                        nextCursor = r.nextCursor,
                    )
                    loadTags()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }

    fun load(sort: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val r = repository.viewForum(forumId, sort = sort, tag = _uiState.value.currentTag)
                _uiState.value = _uiState.value.copy(
                    forum = r.forum,
                    posts = r.posts,
                    canManage = r.can_manage,
                    canModerate = r.can_moderate,
                    sort = sort ?: r.forum.sort,
                    hasMore = r.hasMore,
                    nextCursor = r.nextCursor,
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
                val r = repository.viewForum(forumId, sort = _uiState.value.sort.ifEmpty { null }, tag = _uiState.value.currentTag)
                _uiState.value = _uiState.value.copy(
                    forum = r.forum,
                    posts = r.posts,
                    canManage = r.can_manage,
                    canModerate = r.can_moderate,
                    hasMore = r.hasMore,
                    nextCursor = r.nextCursor,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    fun loadMore() {
        val cursor = _uiState.value.nextCursor ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val r = repository.viewForum(forumId, before = cursor, sort = _uiState.value.sort.ifEmpty { null }, tag = _uiState.value.currentTag)
                _uiState.value = _uiState.value.copy(
                    posts = _uiState.value.posts + r.posts,
                    hasMore = r.hasMore,
                    nextCursor = r.nextCursor,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false, error = e.toMochiError())
            }
        }
    }

    fun setSort(sort: String) {
        viewModelScope.launch {
            try { repository.setForumSort(forumId, sort) } catch (_: Exception) { }
        }
        load(sort)
    }

    fun setTagFilter(tag: String?) {
        if (_uiState.value.currentTag == tag) return
        _uiState.value = _uiState.value.copy(currentTag = tag)
        load(sort = _uiState.value.sort.ifEmpty { null })
    }

    private fun loadTags() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(tags = repository.getForumTags(forumId))
            } catch (_: Exception) {
            }
        }
    }

    fun votePost(postId: String, vote: String) {
        viewModelScope.launch {
            try {
                repository.votePost(forumId, postId, vote)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
