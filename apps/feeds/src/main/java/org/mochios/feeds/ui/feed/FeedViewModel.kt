// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.appendDistinct
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.REOPEN
import org.mochios.android.model.WebSocketEvent
import org.mochios.feeds.R
import org.mochios.feeds.api.FeedNotificationSettings
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.model.Tag
import org.mochios.feeds.model.articleImage
import org.mochios.feeds.repository.FeedsInfoResult
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.PostListResult
import org.mochios.feeds.repository.SavedRepository
import org.mochios.feeds.repository.listed
import org.mochios.feeds.repository.unwrap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration
import org.mochios.feeds.ui.component.applied
import org.mochios.feeds.ui.component.boostedFrom
import org.mochios.feeds.ui.component.boostAccount
import org.mochios.feeds.ui.component.boostAccounts
import org.mochios.feeds.ui.component.onFediverse

private const val PREFS = "mochi_feeds"
private const val KEY_UNREAD_ONLY = "unread_only"

/** One-shot feedback for an interest thumb tap, surfaced to the user as a toast. */
sealed interface InterestFeedback {
    data class Success(val direction: String) : InterestFeedback
    data class Failure(val error: MochiError?) : InterestFeedback
}

/**
 * One-shot side effect from a feed overflow-menu action, which the screen turns
 * into a clipboard write, a toast, or navigation.
 */
sealed interface FeedActionEvent {

    /** An RSS feed URL is ready for the screen to copy to the clipboard. */
    data class RssUrlReady(val url: String) : FeedActionEvent

    /** A share link is ready for the screen to hand to the system share sheet. */
    data class ShareLinkReady(val link: String) : FeedActionEvent

    /** Unsubscribe succeeded; the screen should leave this feed. */
    data object Unsubscribed : FeedActionEvent

    /** An action failed; [error] carries the user-facing message. */
    data class Failure(val error: MochiError?) : FeedActionEvent

    /** An action worked; [message] says so, as a string resource. */
    data class Done(val message: Int) : FeedActionEvent
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeedsRepository,
    private val savedRepository: SavedRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    /** Set of post ids the user has saved, mirrored from [SavedRepository] so
     *  each post card can show its bookmark filled/empty without awaiting. */
    val savedIds: StateFlow<Set<String>> = savedRepository.savedIds

    /** Count of real-time new posts queued behind the refresh button's badge
     *  rather than injected into the pager while the user is reading. */
    private val newPosts = NewPosts()
    val newPostsCount: StateFlow<Int> = newPosts.count


    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val feedId: String = savedStateHandle.get<String>("feedId") ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _feedInfo = MutableStateFlow<Feed?>(null)
    val feedInfo: StateFlow<Feed?> = _feedInfo.asStateFlow()

    /** The user's notification switches for this feed; null until loaded, for
     *  the aggregate, and for a feed they do not hold. */
    private val _notifications = MutableStateFlow<FeedNotificationSettings?>(null)
    val notifications: StateFlow<FeedNotificationSettings?> = _notifications.asStateFlow()

    private val _permissions = MutableStateFlow(Permissions())
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    // True while an unsubscribe is in flight; the confirmation stays open with
    // its button spinning until the server answers.
    private val _isUnsubscribing = MutableStateFlow(false)
    val isUnsubscribing: StateFlow<Boolean> = _isUnsubscribing.asStateFlow()

    // One-shot interest-thumb feedback (boosted/reduced/removed, or the error).
    private val _interestFeedback = MutableSharedFlow<InterestFeedback>(extraBufferCapacity = 8)
    val interestFeedback: SharedFlow<InterestFeedback> = _interestFeedback.asSharedFlow()

    // One-shot overflow-menu side effects (RSS URL ready, unsubscribed, errors).
    private val _actionEvents = MutableSharedFlow<FeedActionEvent>(extraBufferCapacity = 4)
    val actionEvents: SharedFlow<FeedActionEvent> = _actionEvents.asSharedFlow()

    /**
     * The user's hosted accounts on the fediverse and Bluesky, read once a
     * post from or with a copy on the fediverse is shown: a boost is made from
     * the one on the post's own network.
     */
    private val _hosted = MutableStateFlow<List<PublishAccount>>(emptyList())
    val hosted: StateFlow<List<PublishAccount>> = _hosted.asStateFlow()
    private var hostedRead = false

    /** The post whose vote is on its way. */
    private val _voting = MutableStateFlow<String?>(null)
    val voting: StateFlow<String?> = _voting.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Spinner for the top-bar button, which refreshes only the post in view.
    // Kept separate from [isRefreshing] so it doesn't drive the pull-to-refresh
    // indicator.

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var nextCursor: Long = 0

    private val _error = MutableStateFlow<MochiError?>(null)
    val error: StateFlow<MochiError?> = _error.asStateFlow()

    private val _isNotFound = MutableStateFlow(false)
    val isNotFound: StateFlow<Boolean> = _isNotFound.asStateFlow()

    // Set when the owner removes this user from the feed, or deletes it, while
    // the screen is open: the message shown in place of the feed. The local
    // copy is already gone, so a reload would only fail.
    private val _gone = MutableStateFlow<Int?>(null)
    val gone: StateFlow<Int?> = _gone.asStateFlow()

    private val _currentSort = MutableStateFlow("interests")
    val currentSort: StateFlow<String> = _currentSort.asStateFlow()

    // Always null: no tag-filter control is offered, and the repository
    // reads below pass it through as "no filter".
    private val _currentTag = MutableStateFlow<String?>(null)

    private val _unreadOnly = MutableStateFlow(prefs.getBoolean(KEY_UNREAD_ONLY, false))
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    // The viewer's own entity id, so comment items can offer edit/delete on the
    // viewer's own comments. Resolved once from the bound session identity.
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    val isAllFeeds: Boolean = feedId == "__all__"

    private val subscriptions = mutableListOf<String>()

    /** The keys [subscriptions] follow, so a return can tell whether they are open. */
    @Volatile private var socketKeys: Set<String> = emptySet()

    /** The pending catch-up; see [catchUp]. */
    private var catchUpJob: Job? = null
    private var markReadJob: Job? = null
    private val pendingReads = PendingReads()

    /** The furthest post the reader has landed on in the list as it stands;
     *  a reload they did not ask for leaves the list alone up to it. */
    private var reached: String? = null

    /** The pending re-fetch of each post a frame has touched; see [patch]. */
    private val patches = mutableMapOf<String, Job>()

    // Upgraded hero image URLs per post id, resolved lazily as pages come
    // into view. "" = resolved, nothing better than the stored thumbnail.
    private val _postImages = MutableStateFlow<Map<String, String>>(emptyMap())
    val postImages: StateFlow<Map<String, String>> = _postImages.asStateFlow()
    private val resolvingImages = mutableSetOf<String>()

    /**
     * Upgrade an RSS post's thumbnail to the article's og:image, which the
     * server fetches and caches on demand. Only posts already showing an image
     * are upgraded - a late image on a post with no hero region would shift the
     * layout.
     */
    fun resolvePostImage(post: Post) {
        val rss = post.data?.rss ?: return
        if (rss.image.isEmpty() || rss.link.isEmpty()) return
        if (_postImages.value.containsKey(post.id) || !resolvingImages.add(post.id)) return
        viewModelScope.launch {
            val feed = post.feedFingerprint.ifEmpty { post.feed }.ifEmpty { feedId }
            val url = try {
                repository.getPostImage(feed, post.id)
            } catch (_: Exception) {
                ""
            }
            _postImages.value = _postImages.value + (post.id to url)
        }
    }

    init {
        loadFeed()
        loadNotifications()
        subscribeToWebSocket()
        viewModelScope.launch { savedRepository.load() }
        viewModelScope.launch { _currentUserId.value = sessionManager.getBoundIdentity() }
    }

    /**
     * Mint an RSS URL for [mode] (`"posts"` or `"all"`) and hand it to the
     * screen to copy.
     */
    fun copyRssUrl(mode: String) {
        viewModelScope.launch {
            try {
                val url = if (isAllFeeds) {
                    val token = repository.getRssToken("*", mode)
                    "$serverUrl/feeds/rss?token=$token"
                } else {
                    val token = repository.getRssToken(feedId, mode)
                    "$serverUrl/feeds/$feedId/rss?token=$token"
                }
                _actionEvents.emit(FeedActionEvent.RssUrlReady(url))
            } catch (e: Exception) {
                _actionEvents.emit(FeedActionEvent.Failure(e.toMochiError()))
            }
        }
    }

    /**
     * Fetch the feed's `mochi://<peer>/<feed>` share link; the server assembles
     * it.
     */
    fun shareLink() {
        viewModelScope.launch {
            try {
                _actionEvents.emit(FeedActionEvent.ShareLinkReady(repository.shareFeed(feedId)))
            } catch (e: Exception) {
                _actionEvents.emit(FeedActionEvent.Failure(e.toMochiError()))
            }
        }
    }

    /** Unsubscribe the viewer from this feed, then signal the screen to leave it. */
    fun unsubscribe() {
        if (_isUnsubscribing.value) return
        _isUnsubscribing.value = true
        viewModelScope.launch {
            try {
                val feed = feedInfo.value
                val ident = when {
                    feed == null -> feedId
                    feed.id.isNotEmpty() -> feed.id
                    feed.fingerprint.isNotEmpty() -> feed.fingerprint
                    else -> feedId
                }
                repository.unsubscribeFeed(ident)
                _actionEvents.emit(FeedActionEvent.Unsubscribed)
            } catch (e: Exception) {
                _actionEvents.emit(FeedActionEvent.Failure(e.toMochiError()))
            } finally {
                _isUnsubscribing.value = false
            }
        }
    }

    // Mark this feed's notifications read on the server (clear/object), so the
    // bell clears on web and other devices when the feed is opened — matching
    // web's entity-feed-page. The local system tray is dismissed separately in
    // FeedScreen. Skipped for the all-feeds aggregate (no real feed entity).
    private fun loadNotifications() {
        if (isAllFeeds || feedId.isBlank()) return
        viewModelScope.launch {
            try {
                _notifications.value = repository.getNotifications(feedId)
            } catch (_: Exception) {
                // The menu leaves the switches out until they load.
            }
        }
    }

    /**
     * Turn this feed's new-post notifications on or off. The tick moves on the
     * tap and goes back if the server refuses; the server's answer replaces it.
     */
    fun setPostNotifications(enabled: Boolean) {
        if (isAllFeeds || feedId.isBlank()) return
        val before = _notifications.value
        if (before != null) _notifications.value = before.copy(post = enabled)
        viewModelScope.launch {
            try {
                _notifications.value = repository.setNotification(feedId, "post", enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A later tap has moved it since, and its own answer settles it.
                if (_notifications.value?.post == enabled) _notifications.value = before
                _actionEvents.emit(FeedActionEvent.Failure(e.toMochiError()))
            }
        }
    }

    fun clearNotifications() {
        if (isAllFeeds || feedId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.clearNotifications(feedId)
            } catch (_: Exception) {
                // Best-effort — a failed clear shouldn't disrupt the feed view.
            }
        }
    }

    fun loadFeed() {
        viewModelScope.launch {
            _error.value = null
            _isNotFound.value = false

            if (isAllFeeds) {
                _isLoading.value = true
                try {
                    // Fetch the user's saved global sort once before loading posts
                    // so the all-feeds view honors the persisted preference.
                    loadGlobalSort()
                    loadAllFeeds()
                } catch (e: Exception) {
                    val err = e.toMochiError()
                    _error.value = err
                    if (err is MochiError.NotFoundError) _isNotFound.value = true
                } finally {
                    _isLoading.value = false
                }
                return@launch
            }

            // Show cached data immediately if available
            val cachedInfo = repository.getCachedFeedInfo(feedId)
            val cachedPosts = repository.getCachedPosts(feedId, _currentSort.value, _currentTag.value, _unreadOnly.value)
            if (cachedInfo != null && cachedPosts != null) {
                _feedInfo.value = cachedInfo.feed
                // Cached path: don't block on a network round-trip for global
                // sort. Apply the per-feed override eagerly, then refresh in
                // the background.
                applyFeedSortEager(cachedInfo.feed)
                _permissions.value = cachedInfo.permissions
                _posts.value = cachedPosts.posts
                _hasMore.value = cachedPosts.hasMore
                nextCursor = cachedPosts.nextCursor
                _isLoading.value = false
                // Refresh in background
                refreshSilently()
                return@launch
            }

            _isLoading.value = true
            try {
                coroutineScope {
                    // The feed's information is asked for beside its posts and
                    // shows as it lands. It is awaited before the posts show, so
                    // a feed that is gone or closed reports that, whatever the
                    // posts said.
                    val information = async {
                        repository.getFeedInfo(feedId).also { info ->
                            _feedInfo.value = info.feed
                            _permissions.value = info.permissions
                        }
                    }
                    // Pick up the per-feed sort override (or fall back to the
                    // global default) before fetching posts so the first query
                    // honours the user's saved preference. Both come from the
                    // class `-/info` answer the drawer shares; a feed it does not
                    // hold says its own sort in its information.
                    val start = _currentSort.value
                    val roster = roster()
                    val held = roster?.feeds?.firstOrNull { feed -> feed.id == feedId || feed.fingerprint == feedId }
                    val saved = preferred(held?.sort ?: information.await().feed.sort, roster)
                    // A sort the reader picks while this is under way is theirs:
                    // its own reload lists the feed in it, and nothing here
                    // changes it or lists over it.
                    val picked = _currentSort.value != start
                    if (saved != null && !picked) _currentSort.value = saved
                    var asked = _currentSort.value
                    var result = try {
                        fetchPosts()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        information.await()
                        throw e
                    }
                    // The information has the feed's own sort as it is now: one
                    // saved elsewhere since the roster was read lists again in it.
                    val sort = preferred(information.await().feed.sort, roster)
                    if (!picked && sort != null && sort != asked && _currentSort.value == asked) {
                        _currentSort.value = sort
                        asked = sort
                        result = fetchPosts(forceRefresh = true)
                    }
                    if (_currentSort.value == asked) {
                        _posts.value = result.posts
                        _hasMore.value = result.hasMore
                        nextCursor = result.nextCursor
                    }
                }
            } catch (e: Exception) {
                val err = e.toMochiError()
                _error.value = err
                if (err is MochiError.NotFoundError) _isNotFound.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** The class `-/info` answer the drawer shares, or null when it cannot be
     *  read: a missing default sort is not worth failing the feed over. */
    private suspend fun roster(): FeedsInfoResult? = try {
        repository.getFeedsInfo()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** The sort saved for a feed whose own is [own]: that, else the account's
     *  default in [roster], else null to keep the one in use. */
    private fun preferred(own: String, roster: FeedsInfoResult?): String? =
        own.ifEmpty { roster?.sort.orEmpty() }.ifEmpty { null }

    private suspend fun loadGlobalSort() {
        // Non-critical: an unreadable default keeps the existing one.
        preferred("", roster())?.let { sort -> _currentSort.value = sort }
    }

    // Apply a feed's stored sort. Per-feed overrides win; an empty value
    // means "no override", in which case we fall back to the user's saved
    // global default. Suspends for the global lookup so callers can fetch
    // posts with the resolved sort.
    private suspend fun applyFeedSort(feed: Feed) {
        val perFeed = feed.sort
        if (perFeed.isNotEmpty()) {
            _currentSort.value = perFeed
            return
        }
        loadGlobalSort()
    }

    // Cached-path variant: never blocks. Per-feed override wins; otherwise
    // kick off a background fetch of the global default.
    private fun applyFeedSortEager(feed: Feed) {
        val perFeed = feed.sort
        if (perFeed.isNotEmpty()) {
            _currentSort.value = perFeed
            return
        }
        viewModelScope.launch { loadGlobalSort() }
    }

    private suspend fun loadAllFeeds() {
        // No name: the screen supplies the localised R.string.feeds_all_feeds,
        // which is what the drawer entry already uses. A literal here rendered
        // English in the title while the drawer showed the translation.
        _feedInfo.value = Feed()
        _permissions.value = Permissions()
        // The "All feeds" aggregate is served by the class-level -/posts
        // endpoint (posts across every subscribed feed in one indexed query),
        // so it pages exactly like a single feed via nextCursor.
        val result = fetchPosts()
        _posts.value = result.posts
        _hasMore.value = result.hasMore
        nextCursor = result.nextCursor
    }

    // One page for the current view. Pages by a `before` cursor (last post's
    // created; server filters `created < before`) or by `offset` for relevance
    // sorts.
    private suspend fun fetchPosts(
        before: String? = null,
        offset: Long? = null,
        forceRefresh: Boolean = false,
    ): PostListResult {
        return if (isAllFeeds) {
            repository.getAllPosts(
                before = before,
                offset = offset,
                sort = _currentSort.value,
                unreadOnly = _unreadOnly.value,
            )
        } else {
            repository.getPosts(
                feedId = feedId,
                before = before,
                offset = offset,
                sort = _currentSort.value,
                tag = _currentTag.value,
                unreadOnly = _unreadOnly.value,
                forceRefresh = forceRefresh,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                if (isAllFeeds) {
                    loadAllFeeds()
                } else {
                    val info = repository.getFeedInfo(feedId)
                    _feedInfo.value = info.feed
                    _permissions.value = info.permissions

                    val result = repository.getPosts(
                        feedId = feedId,
                        sort = _currentSort.value,
                        tag = _currentTag.value,
                        unreadOnly = _unreadOnly.value,
                        forceRefresh = true
                    )
                    _posts.value = result.posts
                    _hasMore.value = result.hasMore
                    nextCursor = result.nextCursor

                }
                // The reader asked for the list as it now is, from the top.
                reached = null
                // The fresh list incorporates any queued posts — a count left
                // up would just promise posts the user now has.
                newPosts.clear()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Delete a comment from a post shown in the feed, then refresh that post so
     * it drops out of the card's preview.
     */
    fun deleteComment(postFeedId: String, postId: String, commentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteComment(postFeedId, postId, commentId)
                patch(postId, postFeedId)
            } catch (_: Exception) {
                // Silent — best-effort.
            }
        }
    }

    // Re-fetch a single post and patch it in place without the refresh spinner.
    private suspend fun refreshPostQuietly(postFeedId: String, postId: String) {
        val fresh = repository.getPost(postFeedId, postId, shared = true).post
        _posts.value = _posts.value.map { existing ->
            if (existing.id != postId) existing else mergeRefreshedPost(existing, fresh)
        }
    }

    fun reactToComment(postFeedId: String, postId: String, commentId: String, reaction: String) {
        viewModelScope.launch {
            try {
                repository.reactToComment(postFeedId, postId, commentId, reaction)
                patch(postId, postFeedId)
            } catch (_: Exception) {
                // Silent — best-effort.
            }
        }
    }

    // Fold a freshly fetched post onto the visible card, keeping the lazily
    // loaded og:image when the detail response doesn't carry one (so the
    // picture doesn't blink out on refresh).
    private fun mergeRefreshedPost(existing: Post, fresh: Post): Post {
        val freshRss = fresh.data?.rss ?: return fresh
        if (freshRss.image.isNotEmpty()) return fresh
        val existingImage = existing.data?.rss?.image
        if (existingImage.isNullOrEmpty()) return fresh
        return fresh.copy(data = fresh.data.copy(rss = freshRss.copy(image = existingImage)))
    }

    private val isRelevanceSort: Boolean
        get() = _currentSort.value in listOf("interests", "ai", "relevant")

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                // Relevance sorts page by opaque offset; chronological sorts by
                // the `before` cursor (nextCursor is the last post's created
                // timestamp — sending the post id instead once dead-ended the
                // feed after one page). Both apply to the aggregate too.
                val result = if (isRelevanceSort) {
                    fetchPosts(offset = nextCursor)
                } else {
                    fetchPosts(before = nextCursor.toString())
                }
                // Relevance paging is over a score column the server rescores
                // as page 1 is fetched, so a later page legitimately repeats
                // posts. Appended bare they become duplicate LazyColumn keys,
                // which Compose throws on rather than rendering.
                _posts.value = appendDistinct(_posts.value, result.posts) { it.id }
                _hasMore.value = result.hasMore
                nextCursor = result.nextCursor
            } catch (_: Exception) {
                // Silent failure for pagination
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun setSort(sort: String) {
        if (_currentSort.value == sort) return
        _currentSort.value = sort
        // Persist the choice. The all-feeds view writes the global default;
        // a single feed writes a per-feed override.
        viewModelScope.launch {
            try {
                if (isAllFeeds) {
                    repository.setGlobalSort(sort)
                } else {
                    repository.setFeedSort(feedId, sort)
                    // Reflect the new override in cached feed info so a later
                    // re-render doesn't snap back to the previous value.
                    _feedInfo.value = _feedInfo.value?.copy(sort = sort)
                }
            } catch (_: Exception) {
                // Non-critical — UI state already reflects the change.
            }
        }
        reloadPosts()
    }



    fun setUnreadOnly(unreadOnly: Boolean) {
        if (_unreadOnly.value == unreadOnly) return
        _unreadOnly.value = unreadOnly
        prefs.edit().putBoolean(KEY_UNREAD_ONLY, unreadOnly).apply()
        reloadPosts()
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

    fun reactToPost(feed: String, postId: String, reaction: String) {
        viewModelScope.launch {
            try {
                repository.reactToPost(feed, postId, reaction)
                // Optimistically update the post's reaction
                _posts.value = _posts.value.map { post ->
                    if (post.id == postId) post.copy(myReaction = applied(reaction)) else post
                }
            } catch (_: Exception) {
                // Revert on failure by refreshing
                refresh()
            }
        }
    }

    /**
     * [feed] is the post's own feed, not the screen's: the endpoint is
     * entity-scoped and the "__all__" sentinel 404s.
     */
    fun addTag(feed: String, postId: String, label: String, qid: String? = null) {
        val target = feed.takeIf { it.isNotBlank() && it != "__all__" } ?: return
        viewModelScope.launch {
            try {
                repository.addTag(target, postId, label, qid)
                patch(postId, target)
            } catch (_: Exception) {
                // Best-effort, like reactToPost — a failed add simply doesn't appear.
            }
        }
    }

    // Interest is user-global but the action is entity-scoped, so [feed] must
    // be a real feed - the post's own, never the "__all__" sentinel.
    fun adjustInterest(feed: String, tag: Tag, direction: String) {
        val target = feed.takeIf { it.isNotBlank() && it != "__all__" }
        if (target == null) {
            _interestFeedback.tryEmit(InterestFeedback.Failure(null))
            return
        }
        viewModelScope.launch {
            try {
                repository.adjustInterest(
                    target,
                    qid = tag.qid?.takeIf { it.isNotEmpty() },
                    label = if (tag.qid.isNullOrEmpty()) tag.label else null,
                    direction = direction
                )
                applyInterestLocally(tag.qid, direction)
                _interestFeedback.tryEmit(InterestFeedback.Success(direction))
            } catch (e: Exception) {
                _interestFeedback.tryEmit(InterestFeedback.Failure(e.toMochiError()))
            }
        }
    }

    /**
     * Optimistic local mirror of a server interest adjustment; the deltas and
     * clamp match the server's.
     */
    private fun applyInterestLocally(qid: String?, direction: String) {
        if (qid.isNullOrEmpty()) return
        fun adjust(current: Double?): Double? = when (direction) {
            "up" -> ((current ?: 0.0) + 15.0).coerceAtMost(100.0)
            "down" -> ((current ?: 0.0) - 20.0).coerceAtLeast(-100.0)
            else -> null
        }
        fun update(tags: List<Tag>): List<Tag> =
            tags.map { if (it.qid == qid) it.copy(interest = adjust(it.interest)) else it }
        _posts.value = _posts.value.map { post ->
            if (post.tags.any { it.qid == qid }) post.copy(tags = update(post.tags)) else post
        }
    }

    /**
     * Called once [postId]'s bottom has been visible for the UI layer's
     * threshold; ids batch so a fast scroll is one request.
     */
    fun onPostBottomViewed(postId: String) {
        val posts = _posts.value
        if (posts.indexOfFirst { it.id == postId } > posts.indexOfFirst { it.id == reached }) {
            reached = postId
        }
        if (posts.find { it.id == postId }?.read != 0L) return
        pendingReads.add(postId)
        if (markReadJob?.isActive == true) return
        markReadJob = viewModelScope.launch {
            drainReads(pendingReads) { idsToMark ->
                try {
                    repository.markPostsRead(feedId, idsToMark)
                    _posts.value = _posts.value.map { post ->
                        if (post.id in idsToMark && post.read == 0L) {
                            post.copy(read = System.currentTimeMillis() / 1000)
                        } else post
                    }
                    _feedInfo.value = _feedInfo.value?.let { feed ->
                        feed.copy(unread = maxOf(0, feed.unread - idsToMark.size))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("FeedViewModel", "markPostsRead failed for ${idsToMark.size} ids", e)
                }
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                repository.deletePost(feedId, postId)
                drop(postId)
            } catch (_: Exception) {
                refresh()
            }
        }
    }

    /**
     * Read the hosted accounts once the shown posts include one a boost could
     * be made of.
     */
    fun loadHostedIfNeeded(posts: List<Post>) {
        if (hostedRead || posts.none { onFediverse(it) }) return
        hostedRead = true
        viewModelScope.launch {
            try {
                _hosted.value = boostAccounts(repository.networkAccounts())
            } catch (_: Exception) {
                hostedRead = false
            }
        }
    }

    /** Vote in a post's poll; [feed] is the post's own feed. */
    fun vote(feed: String, postId: String, options: List<Int>) {
        if (_voting.value != null || options.isEmpty()) return
        _voting.value = postId
        viewModelScope.launch {
            try {
                val (poll, voted) = repository.votePoll(feed, postId, options)
                _posts.value = _posts.value.map { post ->
                    if (post.id != postId) post
                    else post.copy(
                        voted = voted.ifEmpty { options },
                        data = poll?.let { post.data?.copy(poll = it) } ?: post.data,
                    )
                }
            } catch (e: Exception) {
                _actionEvents.tryEmit(FeedActionEvent.Failure(e.toMochiError()))
            } finally {
                _voting.value = null
            }
        }
    }

    /** Pin a post of the owner's feed, or unpin it. */
    fun togglePin(feed: String, postId: String) {
        val post = _posts.value.firstOrNull { it.id == postId } ?: return
        viewModelScope.launch {
            try {
                val pinned = repository.pinPost(feed, postId, post.pinned <= 0)
                _posts.value = _posts.value.map { if (it.id == postId) it.copy(pinned = pinned) else it }
                _actionEvents.tryEmit(
                    FeedActionEvent.Done(if (pinned > 0) R.string.feeds_post_pinned else R.string.feeds_post_unpinned),
                )
            } catch (e: Exception) {
                _actionEvents.tryEmit(FeedActionEvent.Failure(e.toMochiError()))
            }
        }
    }

    /** Boost a post from another network from the hosted account on its own network, or take the boost back. */
    fun toggleBoost(feed: String, postId: String) {
        val post = _posts.value.firstOrNull { it.id == postId } ?: return
        val account = boostAccount(post, _hosted.value) ?: return
        val boosted = boostedFrom(post, account)
        viewModelScope.launch {
            try {
                val copies = repository.boostPost(feed, postId, account.id, !boosted)
                _posts.value = _posts.value.map { if (it.id == postId) it.copy(copies = copies) else it }
                _actionEvents.tryEmit(
                    FeedActionEvent.Done(if (boosted) R.string.feeds_boost_taken_back else R.string.feeds_boosted),
                )
            } catch (e: Exception) {
                _actionEvents.tryEmit(FeedActionEvent.Failure(e.toMochiError()))
            }
        }
    }

    /**
     * Lazy og:image fetch for an RSS post that arrived without one; the server
     * caches the outcome.
     */
    private val lazyImageAttempted = mutableSetOf<String>()
    fun loadPostImageIfMissing(postId: String) {
        val post = _posts.value.firstOrNull { it.id == postId } ?: return
        val rss = post.data?.rss ?: return
        if (rss.image.isNotEmpty()) return
        if (rss.link.isEmpty()) return
        if (!lazyImageAttempted.add(postId)) return
        viewModelScope.launch {
            // The post's own feed, not the screen's — resolvePostImage above
            // already routes this way. Against the "__all__" sentinel the
            // endpoint answers 200 with an empty image, so in the aggregate
            // view a post that arrived without one never acquired one.
            val feed = post.feedFingerprint.ifEmpty { post.feed }.ifEmpty { feedId }
            val image = repository.getPostImage(feed, postId)
            if (articleImage(image) == null) return@launch
            _posts.value = _posts.value.map { p ->
                if (p.id != postId) p
                else p.copy(data = p.data?.copy(rss = p.data.rss?.copy(image = image)))
            }
        }
    }

    /**
     * Every post read and the badges cleared on the tap, as the web has it.
     * [clear] clears the drawer's badges and hands back how to settle them. A
     * refusal puts back only the posts this marked, gives the counts back and
     * says why.
     */
    fun markAllRead(clear: () -> (accepted: Boolean) -> Unit = { {} }) {
        val now = System.currentTimeMillis() / 1000
        val marked = _posts.value.filter { it.read == 0L }.map { it.id }.toSet()
        _posts.value = _posts.value.map { if (it.id in marked) it.copy(read = now) else it }
        val taken = _feedInfo.value?.unread ?: 0
        _feedInfo.value = _feedInfo.value?.copy(unread = 0)
        val settle = clear()
        viewModelScope.launch {
            try {
                if (isAllFeeds) {
                    // "__all__" isn't a feed: the class-level read-all marks
                    // every feed the user holds at once.
                    repository.markEveryFeedRead()
                } else {
                    repository.markAllRead(feedId)
                }
                // Unread-only mode: every post shown is read now, so they go.
                if (_unreadOnly.value) _posts.value = emptyList()
                settle(true)
            } catch (e: CancellationException) {
                // Left before the answer: the drawer reads what the server has.
                settle(true)
                throw e
            } catch (e: Exception) {
                _posts.value = _posts.value.map {
                    if (it.id in marked && it.read == now) it.copy(read = 0L) else it
                }
                // A count fetched since is the server's own, and stays.
                _feedInfo.value = _feedInfo.value?.let { feed ->
                    if (feed.unread == 0) feed.copy(unread = taken) else feed
                }
                settle(false)
                _actionEvents.emit(FeedActionEvent.Failure(e.toMochiError()))
            }
        }
    }

    private fun reloadPosts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (isAllFeeds) {
                    // The aggregate view fans out per feed (and honours
                    // unreadOnly / sort there); "__all__" is not a real entity,
                    // so a single getPosts() would just fail and silently keep
                    // the stale list — making the toggles look like no-ops.
                    loadAllFeeds()
                } else {
                    val result = repository.getPosts(
                        feedId = feedId,
                        sort = _currentSort.value,
                        tag = _currentTag.value,
                        unreadOnly = _unreadOnly.value,
                        forceRefresh = true
                    )
                    _posts.value = result.posts
                    _hasMore.value = result.hasMore
                    nextCursor = result.nextCursor
                }
                reached = null
            } catch (_: Exception) {
                // Keep existing data
            } finally {
                _isLoading.value = false
            }
        }
    }



    private fun subscribeToWebSocket() {
        val serverUrl = sessionManager.getServerUrlBlocking()
        if (isAllFeeds) {
            // The aggregate has no entity of its own, so it follows every
            // feed the roster lists: on the one key the server sends all their
            // frames to, or on each feed's own from a server that names none.
            // Without this the count never appears and the timeline only moves
            // on a manual refresh.
            viewModelScope.launch {
                val roster = try {
                    repository.getFeedsInfo()
                } catch (_: Exception) {
                    return@launch
                }
                socketKeys = roster.keys()
                for (key in roster.keys()) {
                    subscriptions.add(
                        webSocket.subscribe(serverUrl, key, app = "feeds") { wrapped ->
                            val event = unwrap(wrapped) ?: return@subscribe
                            // As on each feed's own key: only the feeds listed.
                            if (listed(roster.feeds, event.feed)) onFeedEvent(event)
                        }
                    )
                }
            }
            return
        }
        if (feedId.isEmpty()) return
        socketKeys = setOf(feedId)
        subscriptions.add(
            webSocket.subscribe(serverUrl, feedId, app = "feeds") { event ->
                onFeedEvent(event)
            }
        )
    }

    private fun onFeedEvent(event: WebSocketEvent) {
        // Server event types are slash-namespaced (feeds.star commit hook
        // + handlers); the old underscore names never matched anything.
        when (event.type) {
            // The socket was down: whatever it carried meanwhile is gone.
            REOPEN -> catchUp()
            // The feed is no longer the user's: the held class `-/info` answer
            // still lists it. Every feed's view stays, without that feed's
            // posts - taken out here, since a reload keeps whatever the reader
            // has already passed.
            "feed/removed", "feed/deleted" -> {
                repository.forget()
                when {
                    isAllFeeds -> event.feed?.let { feed -> viewModelScope.launch { dropFeed(feed) } }
                    event.type == "feed/removed" -> _gone.value = R.string.feeds_removed_from_feed
                    else -> _gone.value = R.string.feeds_feed_deleted
                }
            }
            "post/create" -> newPosts.record(event.post) { id ->
                _posts.value.any { it.id == id }
            }
            "post/delete" -> {
                val post = event.post
                viewModelScope.launch { if (post.isNullOrEmpty()) refreshSilently() else drop(post) }
            }
            "post/edit",
            "comment/create", "comment/edit", "comment/delete",
            "react/post", "react/comment", "tag/add", "tag/remove" -> {
                val post = event.post
                viewModelScope.launch { if (post.isNullOrEmpty()) refreshSilently() else patch(post) }
            }
        }
    }

    /**
     * Re-fetch [postId] in place, for a frame saying something about it has
     * changed: reloading the list instead would reorder it under the reader.
     * Waits [REFRESH_DEBOUNCE] first, so a burst of frames about one post - a
     * frame per tag as it is tagged - makes one fetch. A post that is not in
     * the list has nothing to patch. An action of the reader's own patches
     * through here too, naming the post's [feed], so its frame's echo and the
     * action make one fetch between them.
     */
    private fun patch(postId: String, feed: String? = null) {
        patches.remove(postId)?.cancel()
        patches[postId] = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            patches.remove(postId)
            val post = _posts.value.find { listed -> listed.id == postId } ?: return@launch
            val source = feed ?: post.feedFingerprint.ifEmpty { post.feed }.ifEmpty { feedId }
            try {
                refreshPostQuietly(source, postId)
            } catch (_: Exception) {
                // The post keeps what it showed.
            }
        }
    }

    /**
     * Take [postId] out of the list. The post that moves onto its page, if the
     * reader was on it, is where they now are.
     */
    private fun drop(postId: String) {
        val posts = _posts.value
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) return
        if (reached == postId) {
            reached = (posts.getOrNull(index + 1) ?: posts.getOrNull(index - 1))?.id
        }
        _posts.value = posts.filterNot { it.id == postId }
    }

    /**
     * Take every post of [feed] out of the list, for a feed gone from every
     * feed's view. The reader's place moves as [drop] moves it.
     */
    private fun dropFeed(feed: String) {
        _posts.value
            .filter { post -> post.feed == feed || post.feedFingerprint == feed }
            .forEach { post -> drop(post.id) }
    }

    /**
     * Reload the list without the reader having asked: on returning to the
     * app, or behind a cached list. What they have reached stays as it is -
     * see [mergeReload].
     */
    private suspend fun refreshSilently() {
        try {
            val result = fetchPosts(forceRefresh = true)
            val merged = mergeReload(_posts.value, result.posts, reached) ?: return
            _posts.value = merged
            _hasMore.value = result.hasMore
            nextCursor = result.nextCursor
            // The fresh list incorporates any queued posts — clear the count.
            newPosts.clear()
        } catch (_: Exception) {
            // Silent failure
        }
    }

    /**
     * Silently re-fetch feed info so edits made in settings (e.g. a new banner)
     * show on return.
     */
    private suspend fun refreshFeedInfo() {
        if (isAllFeeds || feedId.isBlank()) return
        try {
            val info = repository.getFeedInfo(feedId)
            _feedInfo.value = info.feed
            _permissions.value = info.permissions
        } catch (_: Exception) {
            // Silent failure — keep showing the current info.
        }
    }

    // Set by the first reloadOnForeground call; see the guard there.
    private var foregroundReloadArmed = false

    fun reloadOnForeground() {
        // The screen's first ON_RESUME lands right behind init's own load, so
        // honouring it would fetch the feed twice - and the two are racing on
        // different sorts, since the second reads _currentSort before the
        // first has resolved the feed's stored one.
        if (!foregroundReloadArmed) {
            foregroundReloadArmed = true
            return
        }
        if (!isAllFeeds && feedId.isBlank()) return
        // A socket of the screen's opening again says so once it is open, and
        // the catch-up that sets off reads then: one read for the return. If
        // none is, the return reads now; if core refuses it, the wait ends
        // and the return reads then.
        if (socketKeys.any { key -> webSocket.reopening(key) }) catchUp(REOPEN_GRACE) else catchUp()
    }

    /**
     * Read again what the feed shows: on a return to the screen, or a socket
     * of its opening again. Waits [wait] and cancels the pending one, so the
     * several feeds' sockets of a server with no aggregate key, opening again
     * together, read once, and a reopen arriving within a return's wait reads
     * in its place.
     */
    private fun catchUp(wait: Duration = REFRESH_DEBOUNCE) {
        synchronized(this) {
            catchUpJob?.cancel()
            catchUpJob = viewModelScope.launch {
                delay(wait)
                // A sort saved on another device since the class `-/info` answer
                // was read must show, here and in the next feed opened.
                repository.forget()
                // A sort saved elsewhere in the meantime, this feed's own or the
                // account's default behind it, lists the posts afresh in that order.
                val before = _currentSort.value
                if (isAllFeeds) {
                    loadGlobalSort()
                } else {
                    refreshFeedInfo()
                    _feedInfo.value?.let { feed -> applyFeedSort(feed) }
                }
                if (_currentSort.value != before) reloadPosts() else refreshSilently()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        markReadJob?.cancel()
        subscriptions.forEach { webSocket.unsubscribe(it) }
        subscriptions.clear()
    }
}
