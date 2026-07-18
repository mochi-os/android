// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.ui.components.LastViewedStore
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

/**
 * One-shot side effects emitted by [ForumViewModel]. Navigation stays on the
 * composable side, so this channel only carries results the ViewModel produces
 * asynchronously — an RSS URL ready for the clipboard, a finished unsubscribe,
 * and transient errors.
 */
sealed class ForumEvent {

    /** Copy this URL to the clipboard and confirm with a snackbar. */
    data class CopyRssUrl(val url: String) : ForumEvent()

    /** Hand this link to the system share sheet. */
    data class ShareLink(val link: String) : ForumEvent()

    /** The user is no longer subscribed; the screen navigates away. */
    data object Unsubscribed : ForumEvent()

    /** Show a transient error snackbar. */
    data class ShowError(val error: MochiError) : ForumEvent()
}

@HiltViewModel
class ForumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository,
    private val savedRepository: SavedRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val forumId: String = savedStateHandle["forumId"] ?: ""

    /** True for the aggregate "All forums" view (posts across every subscribed
     *  forum), served first-page-only by the class-level `-/list` endpoint —
     *  no per-forum entity, tags, sort persistence, or live subscription. */
    val isAll: Boolean = forumId == LastViewedStore.ALL

    private val _uiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = _uiState.asStateFlow()

    /** Set of post ids the user has saved, mirrored from [SavedRepository] so
     *  each post card can show its bookmark filled/empty without awaiting. */
    val savedIds: StateFlow<Set<String>> = savedRepository.savedIds

    /** Count of real-time new posts queued behind the "new posts" pill rather
     *  than injected into the list while the user is reading. */
    private val _newPostsCount = MutableStateFlow(0)
    val newPostsCount: StateFlow<Int> = _newPostsCount.asStateFlow()

    /** One-shot side effects for the screen — see [ForumEvent]. */
    private val _events = MutableSharedFlow<ForumEvent>()
    val events: SharedFlow<ForumEvent> = _events.asSharedFlow()

    private var subscriptionId: String? = null

    /** Set while the list is catching up with a post this user just made — see
     *  [observeRefreshRequests]. Cleared as soon as the post lands in the list. */
    private var awaitingOwnPost = false

    init {
        load()
        loadTags()
        clearNotifications()
        viewModelScope.launch { savedRepository.load() }
        observeOwnPosts()
    }

    /**
     * Pull the list in as soon as this user posts to the forum on screen, so the
     * composer closes onto a list that already has the post.
     *
     * The create call can return before the post is visible to the list query,
     * so [awaitingOwnPost] keeps the door open for the `post/create` socket event
     * that follows — otherwise the author's own post sits behind the "new posts"
     * pill, waiting for a tap.
     */
    private fun observeOwnPosts() {
        viewModelScope.launch {
            repository.postCreated.collect { postedForum ->
                if (isAll || postedForum == forumId || postedForum == uiState.value.forum.id) {
                    awaitingOwnPost = true
                    refreshSilently()
                }
            }
        }
    }

    // Mark this forum's notifications read on the server (clear/object) when the
    // forum is opened, so the bell clears on web / other devices — matching
    // web's entity-forum-page. Local tray dismissal happens separately in
    // ForumScreen.
    fun clearNotifications() {
        if (forumId.isBlank() || isAll) return
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
                // The user's own post shouldn't hide behind a pill they'd have to
                // tap. It arrives here when the create response beat the list
                // query — pull it in rather than counting it.
                if (awaitingOwnPost) {
                    viewModelScope.launch { refreshSilently() }
                } else {
                    _newPostsCount.value += 1
                }
            } else {
                viewModelScope.launch { refreshSilently() }
            }
        }
    }

    /** Pull the latest list silently (no spinner) and clear the new-posts pill,
     *  since the fresh list already incorporates any queued posts. */
    private suspend fun refreshSilently() {
        try {
            val previousCount = _uiState.value.posts.size
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
            // The awaited post has landed once the list grows; anything arriving
            // after this belongs to somebody else and gets the pill.
            if (r.posts.size > previousCount) awaitingOwnPost = false
            _newPostsCount.value = 0
            loadTags()
        } catch (_: Exception) {}
    }

    /** Reveal the queued new posts: refresh the list and clear the pill. The
     *  screen also scrolls to the top when this is invoked. */
    fun showNewPosts() {
        viewModelScope.launch { refreshSilently() }
    }

    /**
     * Silently reload the forum when the screen returns to the foreground — e.g.
     * after saving a new banner in forum settings — so the change shows on
     * return without a manual pull-to-refresh. The aggregate "all" view has no
     * per-forum banner, so it's skipped.
     */
    fun reloadOnForeground() {
        if (isAll || forumId.isBlank()) return
        viewModelScope.launch { refreshSilently() }
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }

    fun load(sort: String? = null) {
        if (isAll) {
            loadAll(sort, refreshing = false)
            return
        }
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
        if (isAll) {
            loadAll(_uiState.value.sort.ifEmpty { null }, refreshing = true)
            return
        }
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

    /** Load the aggregate "All forums" feed from the class-level `-/list`
     *  endpoint. First page only — the endpoint takes no cursor, so there is no
     *  load-more, live subscription, or per-forum tag filter. */
    private fun loadAll(sort: String?, refreshing: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !refreshing,
                isRefreshing = refreshing,
                error = null,
            )
            try {
                val r = repository.listForums(sort)
                _uiState.value = _uiState.value.copy(
                    forum = Forum(),
                    posts = r.posts,
                    canManage = false,
                    canModerate = false,
                    sort = sort ?: r.settings.sort,
                    hasMore = false,
                    nextCursor = null,
                    tags = emptyList(),
                    currentTag = null,
                    isLoading = false,
                    isRefreshing = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
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

    /**
     * Persist the chosen [sort] server-side, then reload with it. The aggregate
     * has no forum entity of its own, so it writes the class-level default
     * (`-/sort/set`) that the `-/list` endpoint reads back; a single forum
     * writes its own override. Persisting either way is what keeps the choice
     * from resetting when the user switches forums in the drawer, since each
     * forum gets a fresh ViewModel that re-reads the sort from the server.
     */
    fun setSort(sort: String) {
        viewModelScope.launch {
            try {
                if (isAll) {
                    repository.setDefaultSort(sort)
                } else {
                    repository.setForumSort(forumId, sort)
                }
            } catch (_: Exception) {
                // A failed persist still leaves the list sorted for this session.
            }
        }
        load(sort)
    }

    fun setTagFilter(tag: String?) {
        if (_uiState.value.currentTag == tag) return
        _uiState.value = _uiState.value.copy(currentTag = tag)
        load(sort = _uiState.value.sort.ifEmpty { null })
    }

    private fun loadTags() {
        if (isAll) return
        viewModelScope.launch {
            try {
                // Await the tags BEFORE reading the state to copy from. Inline as
                // a copy() argument the receiver `_uiState.value` is evaluated
                // first, so a tag fetch that lands after the post fetch writes
                // back the pre-load snapshot and strands the screen on its
                // loading spinner.
                val tags = repository.getForumTags(forumId)
                _uiState.value = _uiState.value.copy(tags = tags)
            } catch (_: Exception) {
            }
        }
    }

    fun votePost(postId: String, vote: String) {
        // In the aggregate each post belongs to a different forum, so vote against
        // the post's own forum rather than the (synthetic) screen forum id.
        val targetForum = if (isAll) {
            _uiState.value.posts.firstOrNull { post -> post.id == postId }?.forum ?: forumId
        } else {
            forumId
        }
        viewModelScope.launch {
            try {
                repository.votePost(targetForum, postId, vote)
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

    /**
     * Mint an RSS token in [mode] (`"posts"` for posts only, `"all"` for posts
     * and comments) and emit the subscription URL for the screen to place on
     * the clipboard. The "All forums" aggregate tokenises the `*` entity and
     * uses the class-level feed; a single forum uses its own. The server
     * returns the absolute URL; older servers only return the token, so fall
     * back to assembling it against the bound server origin.
     */
    fun copyRssUrl(mode: String) {
        if (forumId.isBlank()) return
        viewModelScope.launch {
            try {
                val entity = if (isAll) "*" else forumId
                val response = repository.getRssToken(entity, mode)
                val url = response.url.ifBlank {
                    val serverUrl = sessionManager.getServerUrlBlocking()
                    val path = if (isAll) "forums/-/rss" else "forums/$forumId/-/rss"
                    "$serverUrl/$path?token=${response.token}"
                }
                _events.emit(ForumEvent.CopyRssUrl(url))
            } catch (e: Exception) {
                _events.emit(ForumEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /**
     * Fetch the forum's `mochi://<peer>/<forum>` link and hand it to the screen
     * for the system share sheet. The server assembles the link, so the peer id
     * never has to be resolved client-side.
     */
    fun shareLink() {
        if (forumId.isBlank()) return
        viewModelScope.launch {
            try {
                _events.emit(ForumEvent.ShareLink(repository.shareForum(forumId)))
            } catch (e: Exception) {
                _events.emit(ForumEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /** Unsubscribe from this forum, then signal the screen to navigate away. */
    fun unsubscribe() {
        if (forumId.isBlank() || isAll) return
        viewModelScope.launch {
            try {
                repository.unsubscribe(forumId)
                _events.emit(ForumEvent.Unsubscribed)
            } catch (e: Exception) {
                _events.emit(ForumEvent.ShowError(e.toMochiError()))
            }
        }
    }
}
