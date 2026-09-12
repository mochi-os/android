// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.appendDistinct
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.forums.api.ForumTagCount
import org.mochios.forums.model.Forum
import org.mochios.forums.model.Post
import org.mochios.forums.model.rejectMessage
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
    /** The server has an AI account; the sort menu offers "AI" only then. */
    val hasAi: Boolean = false,
)

/**
 * One-shot side effects from [ForumViewModel]; navigation stays on the
 * composable side.
 */
sealed class ForumEvent {

    /** Copy this URL to the clipboard and confirm with a snackbar. */
    data class CopyRssUrl(val url: String) : ForumEvent()

    /** Hand this link to the system share sheet. */
    data class ShareLink(val link: String) : ForumEvent()

    /** The user is no longer subscribed; the screen navigates away. */
    data object Unsubscribed : ForumEvent()

    /** The forum's RSS token was cleared; confirm with a snackbar. */
    data object RssRevoked : ForumEvent()

    /** Show a transient error snackbar. */
    data class ShowError(val error: MochiError) : ForumEvent()

    /** Show a transient snackbar for an already-translated message. */
    data class ShowMessage(@StringRes val message: Int) : ForumEvent()
}

/** What the server orders by when a request carries no sort. */
private const val DEFAULT_SORT = "new"

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

    /** The pending or in-flight list refresh; cancelled when a newer one starts. */
    private var refreshJob: Job? = null

    private var resumedBefore = false

    init {
        load()
        loadTags()
        clearNotifications()
        viewModelScope.launch { savedRepository.load() }
        observeOwnPosts()
    }

    /**
     * Refresh as soon as this user posts to the forum on screen. The create
     * call can return before the list query sees the post, so [awaitingOwnPost]
     * lets the following `post/create` event refresh instead of counting.
     */
    private fun observeOwnPosts() {
        viewModelScope.launch {
            repository.postCreated.collect { postedForum ->
                if (isAll || postedForum == forumId || postedForum == uiState.value.forum.id) {
                    awaitingOwnPost = true
                    refreshLatest()
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
        subscriptionId = webSocket.subscribe(serverUrl, forumKey, app = "forums") { event ->
            // New posts queue behind the pill so the list doesn't shift;
            // everything else mutates visible items, so refresh silently.
            if (event.type == "post/create") {
                // The user's own post shouldn't hide behind a pill they'd have to
                // tap. It arrives here when the create response beat the list
                // query — pull it in rather than counting it.
                if (awaitingOwnPost) {
                    refreshLatest()
                } else {
                    _newPostsCount.value += 1
                }
            } else {
                // A rejection deletes the author's optimistic copy on the
                // subscriber's server, so a silent refresh would just make
                // their post vanish. Name the owner's reason first.
                if (event.type == "post/reject" || event.type == "comment/reject") {
                    val message = rejectMessage(event.reason, event.type == "comment/reject")
                    viewModelScope.launch { _events.emit(ForumEvent.ShowMessage(message)) }
                }
                refreshLatest()
            }
        }
    }

    /**
     * [refreshSilently] in the background after [REFRESH_DEBOUNCE], cancelling
     * the pending refresh, so a burst of frames - our own post or vote's echo
     * lands with its response - makes one fetch, and only the latest one
     * updates the list.
     */
    private fun refreshLatest() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            refreshSilently()
        }
    }

    /** The account-wide default sort, or null when the account has none or the
     *  lookup fails - a missing default is not worth failing the forum over. */
    private suspend fun defaultSort(): String? = try {
        repository.forumsInformation().settings.sort.ifEmpty { null }
    } catch (_: Exception) {
        null
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
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshSilently()
        }
    }

    /**
     * The screen's ON_RESUME. The first arrives as the screen opens, while
     * init is already loading, so only a later one - back from settings, say -
     * refreshes.
     */
    fun reloadOnForeground() {
        if (isAll || forumId.isBlank()) return
        if (!resumedBefore) {
            resumedBefore = true
            return
        }
        refreshLatest()
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
                var r = repository.viewForum(forumId, sort = sort, tag = _uiState.value.currentTag)
                // The saved sort only comes back in the response, and the server
                // orders by its own "new" default until it is asked for. Resolve
                // it the way web does - this forum's override, then the account
                // default - and re-read when the first fetch used another order,
                // so the list matches the checkmark the menu will show.
                var resolved = sort
                if (resolved == null) {
                    resolved = r.forum.sort.ifEmpty { defaultSort() }
                    if (resolved != null && resolved != DEFAULT_SORT) {
                        r = repository.viewForum(
                            forumId, sort = resolved, tag = _uiState.value.currentTag,
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(
                    forum = r.forum,
                    posts = r.posts,
                    canManage = r.can_manage,
                    canModerate = r.can_moderate,
                    hasAi = r.hasAi,
                    sort = resolved ?: r.forum.sort,
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
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
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
                ensureActive()
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
                // The aggregate mixes every subscribed forum, and the rows
                // carry only the forum id — the card's "which forum is this?"
                // line needs the name, which the same response already lists.
                val names = r.forums.associate { forum -> forum.id to forum.name }
                _uiState.value = _uiState.value.copy(
                    forum = Forum(),
                    posts = r.posts.map { post ->
                        if (post.forumName.isNotBlank()) post
                        else post.copy(forumName = names[post.forum].orEmpty())
                    },
                    canManage = false,
                    canModerate = false,
                    sort = sort ?: r.settings.sort,
                    hasAi = r.hasAi,
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
                    // Pinned posts repeat on every page and score ordering on a
                    // `created` cursor repeats earlier posts; duplicate ids
                    // would be duplicate LazyColumn keys, which Compose throws
                    // on.
                    posts = appendDistinct(_uiState.value.posts, r.posts) { it.id },
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
     * Persist [sort] server-side, then reload. The aggregate writes the
     * class-level default that `-/list` reads back; a forum writes its own
     * override. Each forum gets a fresh ViewModel that re-reads the sort, so an
     * unpersisted choice would reset on switching.
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
                // Await before reading the state to copy from: as a copy()
                // argument the receiver `_uiState.value` is captured before the
                // suspend, and a late tag fetch would write back the pre-load
                // snapshot.
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
                if (isAll) {
                    refresh()
                } else {
                    refreshLatest()
                }
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
     * Mint an RSS token in [mode] (`"posts"` or `"all"`) and emit the URL for
     * the clipboard; the aggregate tokenises the `*` entity. `-/rss/token`
     * answers the token alone, so the URL is assembled here.
     */
    fun copyRssUrl(mode: String) {
        if (forumId.isBlank()) return
        viewModelScope.launch {
            try {
                val entity = if (isAll) "*" else forumId
                val response = repository.getRssToken(entity, mode)
                val serverUrl = sessionManager.getServerUrlBlocking()
                val path = if (isAll) "forums/-/rss" else "forums/$forumId/-/rss"
                val url = "$serverUrl/$path?token=${response.token}"
                _events.emit(ForumEvent.CopyRssUrl(url))
            } catch (e: Exception) {
                _events.emit(ForumEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /**
     * Clear the forum's RSS token, so every URL already handed out stops
     * working. Also the only way to rotate one: minting returns the existing
     * token unchanged.
     */
    fun revokeRssToken() {
        if (forumId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.revokeRssToken(if (isAll) "*" else forumId)
                _events.emit(ForumEvent.RssRevoked)
            } catch (e: Exception) {
                _events.emit(ForumEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /**
     * Fetch the forum's `mochi://<peer>/<forum>` share link; the server
     * assembles it.
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
