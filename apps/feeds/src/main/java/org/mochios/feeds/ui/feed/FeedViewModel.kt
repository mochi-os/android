// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.ui.components.MentionSuggestion
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.api.InterestSuggestion
import org.mochios.feeds.model.Tag
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.PostListResult
import org.mochios.feeds.repository.SavedRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

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
}

/**
 * The post (and optional parent comment) the comment-composer bottom sheet is
 * targeting. [parentId] non-null means the sheet is composing a reply to that
 * comment; [parentName] is the author shown in the sheet's "Replying to…" line.
 */
data class CommentTarget(
    val feedId: String,
    val postId: String,
    val parentId: String? = null,
    val parentName: String? = null,
    val parentBody: String? = null,
    // When set, the composer is editing this existing comment rather than
    // creating a new one (or a reply).
    val editCommentId: String? = null,
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeedsRepository,
    private val savedRepository: SavedRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Set of post ids the user has saved, mirrored from [SavedRepository] so
     *  each post card can show its bookmark filled/empty without awaiting. */
    val savedIds: StateFlow<Set<String>> = savedRepository.savedIds

    /** Count of real-time new posts queued behind the "new posts" pill rather
     *  than injected into the pager while the user is reading. */
    private val _newPostsCount = MutableStateFlow(0)
    val newPostsCount: StateFlow<Int> = _newPostsCount.asStateFlow()

    /** Post ids the pill has already counted, so repeated post/create events
     *  for the same post count once. Concurrent set: written from the OkHttp
     *  websocket thread, cleared from viewModelScope. */
    private val pendingPosts = ConcurrentHashMap.newKeySet<String>()

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

    // One-shot interest-thumb feedback (boosted/reduced/removed, or the error).
    private val _interestFeedback = MutableSharedFlow<InterestFeedback>(extraBufferCapacity = 8)
    val interestFeedback: SharedFlow<InterestFeedback> = _interestFeedback.asSharedFlow()

    // One-shot overflow-menu side effects (RSS URL ready, unsubscribed, errors).
    private val _actionEvents = MutableSharedFlow<FeedActionEvent>(extraBufferCapacity = 4)
    val actionEvents: SharedFlow<FeedActionEvent> = _actionEvents.asSharedFlow()

    private val _suggestedInterests = MutableStateFlow<List<InterestSuggestion>>(emptyList())
    val suggestedInterests: StateFlow<List<InterestSuggestion>> = _suggestedInterests.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Spinner for the top-bar button, which refreshes only the post in view.
    // Kept separate from [isRefreshing] so it doesn't drive the pull-to-refresh
    // indicator.
    private val _isPostRefreshing = MutableStateFlow(false)
    val isPostRefreshing: StateFlow<Boolean> = _isPostRefreshing.asStateFlow()

    // Comment composer bottom-sheet target: non-null while the sheet is open.
    // A non-null [CommentTarget.parentId] means the sheet is composing a reply.
    private val _commentTarget = MutableStateFlow<CommentTarget?>(null)
    val commentTarget: StateFlow<CommentTarget?> = _commentTarget.asStateFlow()

    private val _commentDraft = MutableStateFlow("")
    val commentDraft: StateFlow<String> = _commentDraft.asStateFlow()

    private val _commentAttachments = MutableStateFlow<List<Uri>>(emptyList())
    val commentAttachments: StateFlow<List<Uri>> = _commentAttachments.asStateFlow()

    private val _isSendingComment = MutableStateFlow(false)
    val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

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

    // The viewer's own entity id, so comment items can offer edit/delete on the
    // viewer's own comments. Resolved once from the bound session identity.
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    val isAllFeeds: Boolean = feedId == "__all__"

    private var subscriptionId: String? = null
    private var markReadJob: Job? = null
    private val pendingReadIds = mutableSetOf<String>()

    init {
        loadFeed()
        subscribeToWebSocket()
        observePendingInterestSuggestions()
        viewModelScope.launch { savedRepository.load() }
        viewModelScope.launch { _currentUserId.value = sessionManager.getBoundIdentity() }
    }

    /**
     * Generate an RSS feed URL for [mode] (`"posts"` or `"all"`) and hand it to
     * the screen to copy to the clipboard. The "All feeds" aggregate uses the
     * class-level RSS endpoint; a single feed uses its own.
     */
    fun copyRssUrl(mode: String) {
        viewModelScope.launch {
            try {
                val url = if (isAllFeeds) {
                    val token = repository.getRssToken("*", mode)
                    "$serverUrl/feeds/-/rss?token=$token"
                } else {
                    val token = repository.getRssToken(feedId, mode)
                    "$serverUrl/feeds/$feedId/-/rss?token=$token"
                }
                _actionEvents.emit(FeedActionEvent.RssUrlReady(url))
            } catch (e: Exception) {
                _actionEvents.emit(FeedActionEvent.Failure(e.toMochiError()))
            }
        }
    }

    /**
     * Fetch the feed's `mochi://<peer>/<feed>` link and hand it to the screen for
     * the system share sheet. The server assembles the link, so the peer id never
     * has to be resolved client-side.
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
            }
        }
    }

    // Show interest suggestions raised by a just-completed subscribe exactly
    // once: consume the repository's pending prompt when it targets this feed,
    // then clear it so it never reappears (e.g. on a return visit).
    private fun observePendingInterestSuggestions() {
        if (isAllFeeds || feedId.isEmpty()) return
        viewModelScope.launch {
            repository.pendingInterestSuggestion.collect { pending ->
                if (pending != null && pending.feedId == feedId) {
                    _suggestedInterests.value = pending.suggestions
                    repository.clearPendingInterestSuggestion()
                }
            }
        }
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
        _feedInfo.value = Feed(name = "All feeds")
        _permissions.value = Permissions()
        // The "All feeds" aggregate is served by the class-level -/posts
        // endpoint (posts across every subscribed feed in one indexed query),
        // so it pages exactly like a single feed via nextCursor.
        val result = fetchPosts()
        _posts.value = result.posts
        _hasMore.value = result.hasMore
        nextCursor = result.nextCursor
    }

    // Fetch one page for the current view. The "All feeds" aggregate hits the
    // class-level all-subscribed-feeds endpoint; a single feed hits its own.
    // Both paginate identically — a chronological `before` cursor (the last
    // post's created timestamp, which the server filters `created < before`) or
    // an `offset` for relevance sorts.
    private suspend fun fetchPosts(before: String? = null, offset: Long? = null): PostListResult {
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
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // A manual refresh dismisses the one-shot post-subscribe suggestions.
            _suggestedInterests.value = emptyList()
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

                    loadTags()
                }
                // The fresh list incorporates any queued posts — a pill left
                // up would just re-show posts the user now has.
                pendingPosts.clear()
                _newPostsCount.value = 0
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Re-fetch a single post and patch it in place, leaving the rest of the
     * list and the reader's position untouched. Backs the top-bar refresh
     * button, which refreshes only the post currently in view. [postFeedId] is
     * the post's own feed — it differs per card in the all-feeds aggregate, so
     * it must come from the post rather than [feedId].
     */
    fun refreshPost(postFeedId: String, postId: String) {
        viewModelScope.launch {
            _isPostRefreshing.value = true
            // The top-bar refresh button also dismisses the suggestions prompt.
            _suggestedInterests.value = emptyList()
            try {
                val fresh = repository.getPost(postFeedId, postId).post
                _posts.value = _posts.value.map { existing ->
                    if (existing.id != postId) existing
                    else mergeRefreshedPost(existing, fresh)
                }
            } catch (_: Exception) {
                // Silent — best-effort single-post refresh.
            } finally {
                _isPostRefreshing.value = false
            }
        }
    }

    /** Open the comment-composer sheet for [postId] (optionally replying to a
     *  comment). [postFeedId] is the post's own feed. */
    fun openCommentComposer(
        postFeedId: String,
        postId: String,
        parentId: String? = null,
        parentName: String? = null,
        parentBody: String? = null,
    ) {
        _commentDraft.value = ""
        _commentAttachments.value = emptyList()
        _commentTarget.value = CommentTarget(postFeedId, postId, parentId, parentName, parentBody)
    }

    /** Open the composer to edit an existing comment, prefilled with [body]. */
    fun openCommentEditor(postFeedId: String, postId: String, commentId: String, body: String) {
        _commentDraft.value = body
        _commentAttachments.value = emptyList()
        _commentTarget.value = CommentTarget(postFeedId, postId, editCommentId = commentId)
    }

    fun closeCommentComposer() {
        _commentTarget.value = null
        _commentDraft.value = ""
        _commentAttachments.value = emptyList()
    }

    fun setCommentDraft(text: String) {
        _commentDraft.value = text
    }

    fun addCommentAttachment(uri: Uri) {
        _commentAttachments.value = _commentAttachments.value + uri
    }

    fun removeCommentAttachment(uri: Uri) {
        _commentAttachments.value = _commentAttachments.value - uri
    }

    /** Search the composer target's feed for @-mention suggestions. */
    suspend fun searchMembers(query: String): List<MentionSuggestion> {
        val feedId = _commentTarget.value?.feedId ?: return emptyList()
        return try {
            repository.searchMembers(feedId, query).map { member ->
                MentionSuggestion(id = member.id, name = member.name)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Post the drafted comment for the open composer target — creating a new
     * comment/reply, or saving an edit when [CommentTarget.editCommentId] is set
     * — then refresh just that post so the card's preview reflects the change.
     */
    fun sendComment() {
        val target = _commentTarget.value ?: return
        val body = _commentDraft.value.trim()
        if (body.isEmpty() || _isSendingComment.value) return
        viewModelScope.launch {
            _isSendingComment.value = true
            try {
                if (target.editCommentId != null) {
                    repository.editComment(target.feedId, target.postId, target.editCommentId, body)
                } else {
                    repository.createComment(
                        feedId = target.feedId,
                        postId = target.postId,
                        body = body,
                        parent = target.parentId,
                        files = _commentAttachments.value,
                        contentResolver = context.contentResolver,
                    )
                }
                _commentTarget.value = null
                _commentDraft.value = ""
                _commentAttachments.value = emptyList()
                refreshPostQuietly(target.feedId, target.postId)
            } catch (_: Exception) {
                // Keep the draft so the user can retry.
            } finally {
                _isSendingComment.value = false
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
                refreshPostQuietly(postFeedId, postId)
            } catch (_: Exception) {
                // Silent — best-effort.
            }
        }
    }

    // Re-fetch a single post and patch it in place without the refresh spinner.
    private suspend fun refreshPostQuietly(postFeedId: String, postId: String) {
        val fresh = repository.getPost(postFeedId, postId).post
        _posts.value = _posts.value.map { existing ->
            if (existing.id != postId) existing else mergeRefreshedPost(existing, fresh)
        }
    }

    /**
     * React to a comment shown in a feed card, then refresh just that post so
     * the comment's reaction pills update in place. [postFeedId] is the post's
     * own feed (cards span feeds in the all-feeds aggregate).
     */
    fun reactToComment(postFeedId: String, postId: String, commentId: String, reaction: String) {
        viewModelScope.launch {
            try {
                repository.reactToComment(postFeedId, postId, commentId, reaction)
                val fresh = repository.getPost(postFeedId, postId).post
                _posts.value = _posts.value.map { existing ->
                    if (existing.id != postId) existing
                    else mergeRefreshedPost(existing, fresh)
                }
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
     * Mirror a successful interest adjustment into local state so the tag
     * colour updates immediately (web does the same optimistic shift). Uses
     * the server's own deltas — up +15, down -20, clamped to ±100; remove
     * clears the weight. Interest is global per qid, so every tag with the
     * same qid updates, across all posts and the feed tag bar.
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
        _tags.value = update(_tags.value)
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

    fun markAllRead(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                if (isAllFeeds) {
                    // No aggregate read-all endpoint exists ("__all__" isn't a
                    // feed); mark each subscribed feed read, mirroring the
                    // loadAllFeeds fan-out.
                    repository.listFeeds().forEach { feed ->
                        val fid = feed.fingerprint.ifEmpty { feed.id }
                        if (fid.isNotEmpty()) {
                            try { repository.markAllRead(fid) } catch (_: Exception) {}
                        }
                    }
                } else {
                    repository.markAllRead(feedId)
                }
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
                // Server has committed the read state — let the caller refresh
                // any dependent UI (e.g. the drawer's unread badges) silently.
                onComplete()
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
                        unreadOnly = _unreadOnly.value,
                        forceRefresh = true
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

    /**
     * Adds every selected suggestion in one pass (the prompt's "Add N interests"
     * action) and clears the suggestion list so the dialog dismisses.
     */
    fun addInterests(suggestions: List<InterestSuggestion>) {
        if (suggestions.isEmpty()) return
        viewModelScope.launch {
            suggestions.forEach { suggestion ->
                try {
                    repository.adjustInterest(feedId, qid = suggestion.qid, label = null, direction = "up")
                } catch (_: Exception) {
                    // Silent — user can retry from feed settings
                }
            }
            _suggestedInterests.value = emptyList()
        }
    }

    fun dismissInterest(suggestion: InterestSuggestion) {
        _suggestedInterests.value = _suggestedInterests.value - suggestion
    }

    private fun subscribeToWebSocket() {
        if (feedId.isEmpty() || isAllFeeds) return
        val serverUrl = sessionManager.getServerUrlBlocking()
        subscriptionId = webSocket.subscribe(serverUrl, feedId) { event ->
            // Server event types are slash-namespaced (feeds.star commit hook
            // + handlers); the old underscore names never matched anything.
            when (event.type) {
                // A brand-new post is queued behind the "new posts" pill so the
                // pager doesn't shift under the reader; tapping it refreshes.
                // The event can arrive for a post the list already shows: RSS
                // ingestion inserts the row immediately but defers post/create
                // until AI tagging completes, so a load in between sees the
                // post before its event. Count only posts genuinely absent,
                // once each.
                "post/create" -> {
                    val postId = event.post
                    if (postId.isNullOrEmpty()) {
                        // Batch form (no post id; sent when AI tagging is off)
                        // — nothing to reconcile against, count it.
                        _newPostsCount.value += 1
                    } else if (pendingPosts.add(postId) &&
                        _posts.value.none { it.id == postId }
                    ) {
                        _newPostsCount.value += 1
                    }
                }
                "post/edit", "post/delete",
                "comment/create", "comment/edit", "comment/delete",
                "react/post", "react/comment", "tag/add", "tag/remove" -> {
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
                unreadOnly = _unreadOnly.value,
                forceRefresh = true
            )
            _posts.value = result.posts
            _hasMore.value = result.hasMore
            nextCursor = result.nextCursor
            // The fresh list incorporates any queued posts — clear the pill.
            pendingPosts.clear()
            _newPostsCount.value = 0
        } catch (_: Exception) {
            // Silent failure
        }
    }

    /** Reveal the queued new posts: refresh the list and clear the pill. The
     *  screen also scrolls the pager to the top when this is invoked. */
    fun showNewPosts() {
        viewModelScope.launch { refreshSilently() }
    }

    /**
     * Silently re-fetch the feed's info (name, banner, permissions) so edits made
     * elsewhere — e.g. saving a new banner in feed settings — show on return
     * without a manual pull-to-refresh. Keeps the current sort untouched.
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

    /**
     * Silently reload the feed when the screen returns to the foreground — e.g.
     * after the user created a post here — so a newly added (or first) post
     * shows without a manual pull-to-refresh.
     */
    fun reloadOnForeground() {
        viewModelScope.launch {
            if (isAllFeeds) {
                try {
                    loadAllFeeds()
                } catch (_: Exception) {
                }
            } else if (feedId.isNotBlank()) {
                refreshFeedInfo()
                refreshSilently()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        markReadJob?.cancel()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }
}
