package org.mochios.feeds.ui.feed

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.api.InterestSuggestion
import org.mochios.feeds.model.Tag
import org.mochios.feeds.repository.FeedsRepository
import javax.inject.Inject

private const val PREFS = "mochi_feeds"
private const val KEY_UNREAD_ONLY = "unread_only"

@HiltViewModel
class FeedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeedsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val feedId: String = savedStateHandle.get<String>("feedId") ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _feedInfo = MutableStateFlow<Feed?>(null)
    val feedInfo: StateFlow<Feed?> = _feedInfo.asStateFlow()

    private val _permissions = MutableStateFlow(Permissions())
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    private val _suggestedInterests = MutableStateFlow<List<InterestSuggestion>>(emptyList())
    val suggestedInterests: StateFlow<List<InterestSuggestion>> = _suggestedInterests.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var nextCursor: Long = 0

    private val _error = MutableStateFlow<MochiError?>(null)
    val error: StateFlow<MochiError?> = _error.asStateFlow()

    private val _isNotFound = MutableStateFlow(false)
    val isNotFound: StateFlow<Boolean> = _isNotFound.asStateFlow()

    private val _currentSort = MutableStateFlow("interests")
    val currentSort: StateFlow<String> = _currentSort.asStateFlow()

    private val _currentTag = MutableStateFlow<String?>(null)
    val currentTag: StateFlow<String?> = _currentTag.asStateFlow()

    private val _unreadOnly = MutableStateFlow(prefs.getBoolean(KEY_UNREAD_ONLY, false))
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    val isAllFeeds: Boolean = feedId == "__all__"

    private var subscriptionId: String? = null
    private var markReadJob: Job? = null
    private val pendingReadIds = mutableSetOf<String>()

    init {
        loadFeed()
        subscribeToWebSocket()
    }

    // Mark this feed's notifications read on the server (clear/object), so the
    // bell clears on web and other devices when the feed is opened — matching
    // web's entity-feed-page. The local system tray is dismissed separately in
    // FeedScreen. Skipped for the all-feeds aggregate (no real feed entity).
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
                loadTags()
                return@launch
            }

            _isLoading.value = true
            try {
                val info = repository.getFeedInfo(feedId)
                _feedInfo.value = info.feed
                _permissions.value = info.permissions
                // Pick up per-feed sort override (or fall back to global
                // default) before fetching posts so the first query honors
                // the user's saved preference.
                applyFeedSort(info.feed)

                val result = repository.getPosts(
                    feedId = feedId,
                    sort = _currentSort.value,
                    tag = _currentTag.value,
                    unreadOnly = _unreadOnly.value
                )
                _posts.value = result.posts
                _hasMore.value = result.hasMore
                nextCursor = result.nextCursor

                loadTags()
            } catch (e: Exception) {
                val err = e.toMochiError()
                _error.value = err
                if (err is MochiError.NotFoundError) _isNotFound.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadGlobalSort() {
        try {
            val sort = repository.getGlobalSort()
            if (sort.isNotEmpty()) {
                _currentSort.value = sort
            }
        } catch (_: Exception) {
            // Non-critical — keep the existing default.
        }
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
        val feeds = repository.listFeeds()
        _feedInfo.value = Feed(name = "All feeds")
        _permissions.value = Permissions()

        val feedIds = feeds.mapNotNull { feed ->
            feed.fingerprint.ifEmpty { feed.id }.ifEmpty { null }
        }
        val deferred = feedIds.map { fid ->
            viewModelScope.async {
                try {
                    repository.getPosts(
                        feedId = fid,
                        sort = _currentSort.value,
                        limit = 10,
                        unreadOnly = _unreadOnly.value
                    ).posts
                } catch (_: Exception) {
                    emptyList<Post>()
                }
            }
        }
        val allPosts = deferred.awaitAll().flatten()

        _posts.value = allPosts.sortedByDescending { it.created }
        _hasMore.value = false
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
                        unreadOnly = _unreadOnly.value
                    )
                    _posts.value = result.posts
                    _hasMore.value = result.hasMore
                    nextCursor = result.nextCursor

                    loadTags()
                }
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private val isRelevanceSort: Boolean
        get() = _currentSort.value in listOf("interests", "ai", "relevant")

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val result = if (isRelevanceSort) {
                    repository.getPosts(
                        feedId = feedId,
                        offset = nextCursor,
                        sort = _currentSort.value,
                        tag = _currentTag.value,
                        unreadOnly = _unreadOnly.value
                    )
                } else {
                    val lastPost = _posts.value.lastOrNull() ?: return@launch
                    repository.getPosts(
                        feedId = feedId,
                        before = lastPost.id,
                        sort = _currentSort.value,
                        tag = _currentTag.value,
                        unreadOnly = _unreadOnly.value
                    )
                }
                _posts.value = _posts.value + result.posts
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

    fun setGlobalDefaultSort(sort: String) {
        viewModelScope.launch {
            try {
                repository.setGlobalSort(sort)
            } catch (_: Exception) {
            }
        }
    }

    fun setTagFilter(tag: String?) {
        if (_currentTag.value == tag) return
        _currentTag.value = tag
        reloadPosts()
    }

    fun setUnreadOnly(unreadOnly: Boolean) {
        if (_unreadOnly.value == unreadOnly) return
        _unreadOnly.value = unreadOnly
        prefs.edit().putBoolean(KEY_UNREAD_ONLY, unreadOnly).apply()
        reloadPosts()
    }

    fun reactToPost(postId: String, reaction: String) {
        viewModelScope.launch {
            try {
                repository.reactToPost(feedId, postId, reaction)
                // Optimistically update the post's reaction
                _posts.value = _posts.value.map { post ->
                    if (post.id == postId) {
                        val newReaction = if (post.myReaction == reaction) "" else reaction
                        post.copy(myReaction = newReaction)
                    } else post
                }
            } catch (_: Exception) {
                // Revert on failure by refreshing
                refresh()
            }
        }
    }

    fun addTag(postId: String, label: String, qid: String? = null) {
        viewModelScope.launch {
            try {
                repository.addTag(feedId, postId, label, qid)
                // Refresh just this post's tags so the card's tag list reflects
                // the addition without reloading the whole feed.
                val updated = repository.getPostTags(feedId, postId)
                _posts.value = _posts.value.map { post ->
                    if (post.id == postId) post.copy(tags = updated) else post
                }
            } catch (_: Exception) {
                // Best-effort, like reactToPost — a failed add simply doesn't appear.
            }
        }
    }

    // Interest is user-global, but the action is entity-scoped to a feed, so it
    // must route through a real feed entity. In the all-feeds view feedId is the
    // "__all__" sentinel (not an entity, so the action 404s and silently does
    // nothing), so the caller passes the post's own feed — always valid — as the
    // routing context. Mirrors web, which routes interest through a subscribed feed.
    fun adjustInterest(feed: String, tag: Tag, direction: String) {
        val target = feed.takeIf { it.isNotBlank() && it != "__all__" } ?: return
        viewModelScope.launch {
            try {
                repository.adjustInterest(
                    target,
                    qid = tag.qid?.takeIf { it.isNotEmpty() },
                    label = if (tag.qid.isNullOrEmpty()) tag.label else null,
                    direction = direction
                )
            } catch (_: Exception) {
                // Best-effort; silent on failure.
            }
        }
    }

    /**
     * Called when the bottom of [postId] has been continuously visible for
     * the threshold (currently 1s, enforced in the UI layer). Adds the post
     * to a pending batch that flushes to the server after a short window so
     * a fast scroll past several posts becomes one HTTP call.
     */
    fun onPostBottomViewed(postId: String) {
        if (_posts.value.find { it.id == postId }?.read != 0L) return
        pendingReadIds.add(postId)
        if (markReadJob?.isActive == true) return
        markReadJob = viewModelScope.launch {
            delay(200)
            val idsToMark = pendingReadIds.toList()
            pendingReadIds.clear()
            if (idsToMark.isEmpty()) return@launch
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
            } catch (e: Exception) {
                android.util.Log.e("FeedViewModel", "markPostsRead failed for ${idsToMark.size} ids", e)
            }
        }
    }

    /**
     * Delete a post from the feed list overflow menu. Removes the post
     * from local state on success; refreshes the feed on failure to
     * recover the canonical state.
     */
    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                repository.deletePost(feedId, postId)
                _posts.value = _posts.value.filterNot { it.id == postId }
            } catch (_: Exception) {
                refresh()
            }
        }
    }

    /**
     * Lazy og:image fetch for an RSS post that arrived without an inline
     * image. Idempotent — the repository's server-side handler caches the
     * fetch outcome, and we skip the call entirely when image is already
     * set, when there's no link to scrape, or when we've already attempted
     * a fetch for this post in this session.
     */
    private val lazyImageAttempted = mutableSetOf<String>()
    fun loadPostImageIfMissing(postId: String) {
        val post = _posts.value.firstOrNull { it.id == postId } ?: return
        val rss = post.data?.rss ?: return
        if (rss.image.isNotEmpty()) return
        if (rss.link.isEmpty()) return
        if (!lazyImageAttempted.add(postId)) return
        viewModelScope.launch {
            val image = repository.getPostImage(feedId, postId)
            if (image.isBlank()) return@launch
            _posts.value = _posts.value.map { p ->
                if (p.id != postId) p
                else p.copy(data = p.data?.copy(rss = p.data.rss?.copy(image = image)))
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                repository.markAllRead(feedId)
                val now = System.currentTimeMillis() / 1000
                // In "unread only" mode every visible post just became read,
                // so they should disappear from the list immediately rather
                // than wait for the next reload. In "all" mode keep the
                // posts but stamp them as read so any unread-badge UI drops
                // to zero without a re-fetch.
                _posts.value = if (_unreadOnly.value) {
                    emptyList()
                } else {
                    _posts.value.map { it.copy(read = now) }
                }
                _feedInfo.value = _feedInfo.value?.copy(unread = 0)
            } catch (_: Exception) {
                // Refresh on failure
                refresh()
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
                        unreadOnly = _unreadOnly.value
                    )
                    _posts.value = result.posts
                    _hasMore.value = result.hasMore
                    nextCursor = result.nextCursor
                }
            } catch (_: Exception) {
                // Keep existing data
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun adjustTagInterest(tag: Tag, direction: String) {
        viewModelScope.launch {
            try {
                repository.adjustInterest(feedId, qid = tag.qid, label = tag.label, direction = direction)
                _tags.value = repository.getTags(feedId)
            } catch (_: Exception) {
            }
        }
    }

    private fun loadTags() {
        if (isAllFeeds) return
        viewModelScope.launch {
            try {
                _tags.value = repository.getTags(feedId)
            } catch (_: Exception) {
                // Tags are non-critical
            }
        }
        viewModelScope.launch {
            try {
                _suggestedInterests.value = repository.getSuggestedInterests(feedId)
            } catch (_: Exception) {
                // Suggestions are non-critical
            }
        }
    }

    fun addInterest(suggestion: InterestSuggestion) {
        viewModelScope.launch {
            try {
                repository.adjustInterest(feedId, qid = suggestion.qid, label = null, direction = "up")
                // Remove from suggestions once added
                _suggestedInterests.value = _suggestedInterests.value - suggestion
            } catch (_: Exception) {
                // Silent — user can retry
            }
        }
    }

    fun dismissInterest(suggestion: InterestSuggestion) {
        _suggestedInterests.value = _suggestedInterests.value - suggestion
    }

    private fun subscribeToWebSocket() {
        if (feedId.isEmpty() || isAllFeeds) return
        val serverUrl = sessionManager.getServerUrlBlocking()
        subscriptionId = webSocket.subscribe(serverUrl, feedId) { event ->
            when (event.type) {
                "post_created", "post_deleted", "post_updated", "comment_created",
                "comment_deleted", "reaction", "source_polled" -> {
                    viewModelScope.launch { refreshSilently() }
                }
            }
        }
    }

    private suspend fun refreshSilently() {
        try {
            val result = repository.getPosts(
                feedId = feedId,
                sort = _currentSort.value,
                tag = _currentTag.value,
                unreadOnly = _unreadOnly.value
            )
            _posts.value = result.posts
            _hasMore.value = result.hasMore
            nextCursor = result.nextCursor
        } catch (_: Exception) {
            // Silent failure
        }
    }

    override fun onCleared() {
        super.onCleared()
        markReadJob?.cancel()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }
}
