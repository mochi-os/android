// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import org.mochios.forums.repository.SavedRepository
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
    private val savedRepository: SavedRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _uiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = _uiState.asStateFlow()

    /** Set of post ids the user has saved, mirrored from [SavedRepository] so
     *  each post card can show its bookmark filled/empty without awaiting. */
    val savedIds: StateFlow<Set<String>> = savedRepository.savedIds

    /** Count of real-time new posts queued behind the "new posts" pill rather
     *  than injected into the list while the user is reading. */
    private val _newPostsCount = MutableStateFlow(0)
    val newPostsCount: StateFlow<Int> = _newPostsCount.asStateFlow()

    private var subscriptionId: String? = null

    init {
        load()
        loadTags()
        clearNotifications()
        viewModelScope.launch { savedRepository.load() }
    }

    // Mark this forum's notifications read on the server (clear/object) when the
    // forum is opened, so the bell clears on web / other devices — matching
    // web's entity-forum-page. Local tray dismissal happens separately in
    // ForumScreen.
    fun clearNotifications() {
        if (forumId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.clearNotifications(forumId)
            } catch (_: Exception) {
                // Best-effort — a failed clear shouldn't disrupt the forum view.
            }
        }
    }

    private fun subscribeWebSocket(forumKey: String) {
        if (forumKey.isBlank() || subscriptionId != null) return
        val serverUrl = sessionManager.getServerUrlBlocking()
        subscriptionId = webSocket.subscribe(serverUrl, forumKey) { event ->
            // A brand-new post is queued behind the "new posts" pill so the list
            // doesn't shift under the reader; everything else (edits, deletes,
            // comments, votes, tags) mutates already-visible items, so refresh
            // silently. A refresh incorporates any queued posts and clears the
            // pill (see refreshSilently).
            if (event.type == "post/create") {
                _newPostsCount.value += 1
            } else {
                viewModelScope.launch { refreshSilently() }
            }
        }
    }

    /** Pull the latest list silently (no spinner) and clear the new-posts pill,
     *  since the fresh list already incorporates any queued posts. */
    private suspend fun refreshSilently() {
        try {
            val r = repository.viewForum(
                forumId,
                sort = _uiState.value.sort.ifEmpty { null },
                tag = _uiState.value.currentTag,
            )
            _uiState.value = _uiState.value.copy(
                forum = r.forum,
                posts = r.posts,
                canManage = r.can_manage,
                canModerate = r.can_moderate,
                hasMore = r.hasMore,
                nextCursor = r.nextCursor,
            )
            _newPostsCount.value = 0
            loadTags()
        } catch (_: Exception) {}
    }

    /** Reveal the queued new posts: refresh the list and clear the pill. The
     *  screen also scrolls to the top when this is invoked. */
    fun showNewPosts() {
        viewModelScope.launch { refreshSilently() }
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

    /** Toggle the saved ("read-later") state of a post. The bookmark fill
     *  updates optimistically via [savedIds]; a failed call reverts it. */
    fun toggleSave(post: Post) {
        viewModelScope.launch {
            try {
                savedRepository.toggle(post)
            } catch (_: Exception) {
                // SavedRepository already reverted the optimistic mirror update.
            }
        }
    }
}
