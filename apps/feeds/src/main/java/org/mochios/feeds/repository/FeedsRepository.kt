// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MultipartBody
import org.mochios.android.api.ApiError
import org.mochios.android.api.ApiException
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.android.api.unwrapRaw
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.UploadTracker
import org.mochios.android.launcher.Shortcuts
import org.mochios.android.model.AccessRule
import org.mochios.android.model.Comment
import org.mochios.android.model.PlaceData
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.files.FileRepository
import org.mochios.android.files.FileStore
import org.mochios.feeds.api.FeedNotificationSettings
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.api.PostDetailResponse
import org.mochios.feeds.api.AccessRevokeRequest
import org.mochios.feeds.api.AccessSetRequest
import org.mochios.feeds.api.AddSourceRequest
import org.mochios.feeds.api.CreateFeedRequest
import org.mochios.feeds.api.SubscribeRequest
import org.mochios.feeds.api.UnsubscribeRequest
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Group
import org.mochios.feeds.model.Member
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Lookup
import org.mochios.feeds.model.Person
import org.mochios.feeds.model.PollInput
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PostCopy
import org.mochios.feeds.model.PostFeatures
import org.mochios.feeds.model.PostPoll
import org.mochios.feeds.model.PublishAccount
import org.mochios.feeds.model.PublishSaved
import org.mochios.feeds.model.PublishState
import org.mochios.feeds.model.Filter
import org.mochios.feeds.model.Imported
import org.mochios.feeds.model.Source
import org.mochios.feeds.model.Tag
import org.mochios.feeds.model.User
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class FeedInfoResult(
    val feed: Feed,
    val permissions: Permissions
)

/**
 * Class-level `-/info`: subscribed feeds and whether any AI provider is
 * configured (gates the AI sort option).
 *
 * @property websocket the websocket key every feed's frames also reach, so a
 *   view of all of them holds one socket; empty from an older server, where
 *   each feed's own key is the only one.
 */
data class FeedsInfoResult(
    val feeds: List<Feed>,
    val hasAi: Boolean,
    val sort: String = "",
    val websocket: String = "",
) {
    /** The websocket keys carrying every listed feed's frames: [websocket]
     *  alone when the server names it, else each feed's own. */
    fun keys(): Set<String> =
        if (websocket.isNotEmpty()) {
            setOf(websocket)
        } else {
            feeds.map { feed -> feed.fingerprint }.filter { key -> key.isNotEmpty() }.toSet()
        }
}

/**
 * Whether a frame about [feed] concerns one of [feeds]. The aggregate key also
 * carries the frames of feeds held only as another feed's source, which no list
 * of the user's feeds shows; a frame naming no feed is let through.
 */
internal fun listed(feeds: Collection<Feed>, feed: String?): Boolean =
    feed.isNullOrEmpty() || feeds.any { item -> item.id == feed || item.fingerprint == feed }

/**
 * The frame [event] carries. On the aggregate key each feed's frame arrives
 * wrapped, `{"type": "feeds/frame", "feed": <feed id>, "frame": <its frame>}`,
 * so that core passes each write on once; a feed's own key, and the socket's
 * own reopen, send the frame bare. The wrapper's feed is the one the frame is
 * about. Null for a wrapper with nothing inside.
 */
internal fun unwrap(event: WebSocketEvent): WebSocketEvent? {
    if (event.type != WRAPPED) return event
    val frame = event.frame ?: return null
    return frame.copy(feed = event.feed?.takeIf { feed -> feed.isNotEmpty() } ?: frame.feed)
}

/** The type of a frame wrapped on the aggregate key; see [unwrap]. */
internal const val WRAPPED = "feeds/frame"

data class PostListResult(
    val posts: List<Post>,
    val hasMore: Boolean,
    val nextCursor: Long = 0,
    val permissions: Permissions = Permissions()
)

data class PostDetailResult(
    val post: Post,
    val permissions: Permissions
)

/**
 * The detail result behind a `-/:post` answer. A deleted or unknown post is
 * answered with 200 and an empty `posts` list rather than a 404, so the
 * not-found state has to be raised here — otherwise the screen shows the
 * generic error-with-retry instead of "post not found".
 */
internal fun postDetail(response: PostDetailResponse): PostDetailResult =
    PostDetailResult(
        post = response.posts.firstOrNull() ?: throw MochiError.NotFoundError(),
        permissions = response.permissions,
    )

/**
 * A post edit's attachment order: one part per item, which every feeds server
 * reads. An empty list - every attachment removed - is sent as "[]", since no
 * parts at all would read as "leave the attachments alone"; a server from 6.129
 * reads it as an empty order and an older one as an unknown id, and both then
 * remove every attachment. A JSON array of ids would read as one unknown id to
 * an older server and delete them all. Null sends no order, which leaves the
 * post's attachments as they are.
 */
internal fun addEditOrder(builder: MultipartBody.Builder, order: List<String>?) {
    when {
        order == null -> {}
        order.isEmpty() -> builder.addFormDataPart("order", "[]")
        else -> order.forEach { builder.addFormDataPart("order", it) }
    }
}

data class ProbeResult(
    val feed: Feed?,
)

/** The feed a probe found: its answer is the feed's own entry, empty when it
 *  found nothing. */
internal fun probeFeed(entry: Feed): Feed? = entry.takeIf { it.id.isNotEmpty() }

data class AddSourceResult(
    val source: Source,
    val suggestedCredibility: Int? = null,
    /** How many posts the source brought in as it was added. */
    val ingested: Int = 0,
)

/** Parsed body of a `permission_required` 403 from `sources/add`. */
private data class PermissionRequiredBody(
    val app: String? = null,
    val error: String? = null,
    val permission: String? = null
)

/**
 * `sources/add` answered `permission_required`: [app] must be granted
 * [permission] first.
 */
class PermissionRequiredException(
    val app: String,
    val permission: String
) : Exception("permission_required: $permission")

@Singleton
class FeedsRepository @Inject constructor(
    private val api: FeedsApi,
    private val menuApi: MenuApi,
    fileStore: FileStore,
    @param:ApplicationContext private val context: Context,
    private val session: SessionManager,
) : FileRepository(fileStore) {

    // In-memory cache: feedId -> (posts, hasMore, timestamp). Each entry is
    // the signed-in account's: another account, or none, never reads it.
    private val postCache = mutableMapOf<String, CachedPosts>()
    private val feedInfoCache = mutableMapOf<String, CachedFeedInfo>()
    private val cacheMaxAge = 60_000L // 1 minute

    private data class CachedPosts(
        val account: String,
        val result: PostListResult,
        val sort: String?,
        val tag: String?,
        val unreadOnly: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private data class CachedFeedInfo(
        val account: String,
        val result: FeedInfoResult,
        val timestamp: Long = System.currentTimeMillis()
    )

    /** The last class `-/info` answer, the account it was read for, when it
     *  was asked for and when it arrived. */
    private class Roster(val account: String, val read: Long, val arrived: Long, val answer: FeedsInfoResult)

    /** One class `-/info` read at a time: a caller arriving while one is under
     *  way waits for it and takes its answer. */
    private val lock = Mutex()

    @Volatile private var roster: Roster? = null

    /** Moved on by [forget], so a read already under way keeps nothing. */
    private val generation = AtomicInteger()

    // Emits whenever the viewer's subscriptions change (subscribe/unsubscribe),
    // so the feed-list drawer can reload even without a navigation that would
    // otherwise recreate its ViewModel. Replay-less: only live collectors react.
    private val _subscriptionChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val subscriptionChanges: SharedFlow<Unit> = _subscriptionChanges.asSharedFlow()

    suspend fun getCachedPosts(feedId: String, sort: String?, tag: String?, unreadOnly: Boolean): PostListResult? {
        val account = session.getBoundIdentity()
        val cached = postCache[feedId] ?: return null
        if (cached.account != account) return null
        if (System.currentTimeMillis() - cached.timestamp > cacheMaxAge) return null
        if (cached.sort != sort || cached.tag != tag || cached.unreadOnly != unreadOnly) return null
        return cached.result
    }

    suspend fun getCachedFeedInfo(feedId: String): FeedInfoResult? {
        val account = session.getBoundIdentity()
        val cached = feedInfoCache[feedId] ?: return null
        if (cached.account != account) return null
        if (System.currentTimeMillis() - cached.timestamp > cacheMaxAge) return null
        return cached.result
    }


    // --- Class-level operations ---

    suspend fun listFeeds(): List<Feed> {
        return getFeedsInfo().feeds
    }

    /**
     * Fetch the full class-level feeds overview (`-/info`): feed list with
     * each feed's own sort, the user's global default sort, and the server's
     * AI availability. The feeds pinned to the home screen are brought into
     * line with the list.
     *
     * The drawer, the feed screen's sort and the all-feeds websocket list all
     * ask as a feed opens, so the answer is held for [cacheMaxAge] and shared,
     * and only for the account it was read for: signing out, or in as
     * somebody else, reads it afresh. [fresh] reads past the held answer, for
     * the drawer's refreshes. [current] takes only an answer that arrives
     * after the call, for a drawer opening: one read under way as it opens is
     * shared, and an older one held is not, so its unread counts are those of
     * the moment.
     */
    suspend fun getFeedsInfo(fresh: Boolean = false, current: Boolean = false): FeedsInfoResult {
        val asked = SystemClock.elapsedRealtime()
        return lock.withLock { information(fresh, if (current) asked else null) }
    }

    /** [getFeedsInfo] under its lock; [after] is the earliest arrival taken. */
    private suspend fun information(fresh: Boolean, after: Long?): FeedsInfoResult {
        val account = session.getBoundIdentity()
        val held = roster
        val usable = when {
            fresh || held == null || held.account != account -> false
            after != null -> held.arrived >= after
            else -> SystemClock.elapsedRealtime() - held.read < cacheMaxAge
        }
        if (usable && held != null) return held.answer
        val started = generation.get()
        val read = SystemClock.elapsedRealtime()
        val answer = try {
            val response = api.getInfo().unwrap()
            Shortcuts.reconcile(context, "feeds", Shortcuts.paths(response.feeds, { it.name }) { listOf(it.id, it.fingerprint) })
            FeedsInfoResult(
                feeds = response.feeds,
                hasAi = response.hasAi,
                sort = response.settings.sort,
                websocket = response.websocket.orEmpty(),
            )
        } catch (e: Exception) {
            throw e.toMochiError()
        }
        // Kept only for the account still signed in: one that changed while
        // it was read may have had the request sent on its own session.
        roster = if (account != null && generation.get() == started && session.getBoundIdentity() == account) {
            Roster(account, read, SystemClock.elapsedRealtime(), answer)
        } else {
            null
        }
        return answer
    }

    /**
     * Drops the held class `-/info` answer, so the next [getFeedsInfo] reads
     * it afresh: after a write that changes the feeds, their unread counts or
     * a sort.
     */
    fun forget() {
        generation.incrementAndGet()
        roster = null
    }

    suspend fun createFeed(name: String, privacy: String, memories: Boolean): Feed {
        return try {
            val response = api.createFeed(
                CreateFeedRequest(name = name, privacy = privacy, memories = memories.toString())
            ).unwrap()
            forget()
            Feed(id = response.id, fingerprint = response.fingerprint, name = name, privacy = privacy)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun searchDirectory(query: String): List<Feed> {
        return try {
            api.searchDirectory(query).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getRecommendations(): List<Feed> {
        return try {
            api.getRecommendations().unwrap().feeds
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun probeUrl(url: String): ProbeResult {
        return try {
            ProbeResult(feed = probeFeed(api.probeUrl(url).unwrap()))
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun subscribeFeed(feed: String, server: String? = null, peer: String? = null) {
        try {
            // Send the hints. Without them the server takes its directory
            // branch and answers 404 errors.feed_not_in_directory for a feed it
            // has never seen, so a feed on another server could not be
            // subscribed to at all.
            api.subscribe(
                SubscribeRequest(
                    feed = feed,
                    server = server?.takeIf { it.isNotBlank() },
                    peer = peer?.takeIf { it.isNotBlank() },
                )
            ).unwrap()
            forget()
            _subscriptionChanges.emit(Unit)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun unsubscribeFeed(feed: String, server: String? = null) {
        try {
            api.unsubscribe(
                UnsubscribeRequest(feed = feed, server = server?.takeIf { it.isNotBlank() })
            ).unwrap()
            forget()
            _subscriptionChanges.emit(Unit)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Clears the entity's RSS token, so its published URLs stop working. */
    suspend fun revokeRssToken(entity: String) {
        api.revokeRssToken(entity).unwrap()
    }

    suspend fun getRssToken(entity: String, mode: String): String {
        return try {
            api.getRssToken(entity, mode).unwrap().token
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** The feed's shareable `mochi://<peer>/<feed>` link, built by the server. */
    suspend fun shareFeed(feedId: String): String {
        return try {
            api.shareFeed(feedId).unwrap().link
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun searchUsers(query: String): List<User> {
        return try {
            api.searchUsers(query).unwrap().results
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Resolve a permission key to its human label. */
    suspend fun permissionName(permission: String): String {
        return try {
            menuApi.permissionName(permission).unwrap().name
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Grant [app] the given [permission], returning the resulting status. */
    suspend fun grantPermission(app: String, permission: String): String {
        return try {
            menuApi.grantPermission(app, permission).unwrap().status
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getGroups(): List<Group> {
        return try {
            api.getGroups().unwrap().groups
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Entity-level operations ---

    suspend fun getFeedInfo(feedId: String): FeedInfoResult {
        return try {
            val account = session.getBoundIdentity()
            val response = api.getFeedInfo(feedId).unwrap()
            val result = FeedInfoResult(feed = response.feed, permissions = response.permissions)
            if (account != null && session.getBoundIdentity() == account) {
                feedInfoCache[feedId] = CachedFeedInfo(account, result)
            }
            result
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getPosts(
        feedId: String,
        before: String? = null,
        offset: Long? = null,
        limit: Int = 20,
        sort: String? = null,
        tag: String? = null,
        unreadOnly: Boolean = false,
        forceRefresh: Boolean = false
    ): PostListResult {
        val isFirstPage = before == null && offset == null
        // Only cache first-page requests. An explicit refresh skips the cached
        // copy and re-fetches so it can't show stale data (e.g. comments that
        // changed since the cache was written).
        if (isFirstPage && !forceRefresh) {
            getCachedPosts(feedId, sort, tag, unreadOnly)?.let { return it }
        }
        return try {
            val account = session.getBoundIdentity()
            val response = api.getPosts(
                feedId = feedId,
                before = before,
                offset = offset,
                limit = limit,
                sort = sort,
                tag = tag,
                unread = if (unreadOnly) "1" else null
            ).unwrap()
            val result = PostListResult(
                posts = response.posts,
                hasMore = response.hasMore,
                nextCursor = response.nextCursor,
                permissions = response.permissions ?: Permissions()
            )
            if (isFirstPage && account != null && session.getBoundIdentity() == account) {
                postCache[feedId] = CachedPosts(account, result, sort, tag, unreadOnly)
            }
            result
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // Posts merged across every subscribed feed for the "All feeds" aggregate.
    // Paginated server-side by the same before/offset cursors as a single feed;
    // not cached (the merged view changes as any feed updates).
    suspend fun getAllPosts(
        before: String? = null,
        offset: Long? = null,
        limit: Int = 20,
        sort: String? = null,
        unreadOnly: Boolean = false
    ): PostListResult {
        return try {
            val response = api.getAllPosts(
                before = before,
                offset = offset,
                limit = limit,
                sort = sort,
                unread = if (unreadOnly) "1" else null
            ).unwrap()
            PostListResult(
                posts = response.posts,
                hasMore = response.hasMore,
                nextCursor = response.nextCursor,
                permissions = response.permissions ?: Permissions()
            )
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteFeed(feedId: String) {
        try {
            api.deleteFeed(feedId).unwrap()
            forget()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun renameFeed(feedId: String, name: String) {
        try {
            api.renameFeed(feedId, name).unwrap()
            forget()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun markAllRead(feedId: String) {
        try {
            api.markAllRead(feedId).unwrap()
            forget()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Best image for an RSS post: the server fetches and caches the article's
     * og:image on the post row. "" when nothing better exists or the post is
     * not RSS.
     */
    suspend fun getPostImage(feedId: String, postId: String): String {
        return try {
            api.getPostImage(feedId, postId).unwrapRaw().image
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun markPostsRead(feedId: String, postIds: List<String>) {
        if (postIds.isEmpty()) return
        try {
            api.markPostsRead(feedId, postIds).unwrap()
            forget()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun createPost(
        feedId: String,
        body: String,
        files: List<Pair<String, ByteArray>>,
        fileTypes: List<String>,
        checkin: PlaceData? = null,
        travellingOrigin: PlaceData? = null,
        travellingDestination: PlaceData? = null
    ) {
        try {
            val multipartBody = buildPostBody(feedId, body, checkin, travellingOrigin, travellingDestination)
            files.forEachIndexed { index, (name, bytes) ->
                val mediaType = fileTypes.getOrElse(index) { FileStore.DEFAULT_MIME }
                multipartBody.addPart(fileStore.bytesPart("files", name, mediaType, bytes))
            }
            api.createPost(feedId, multipartBody.build()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun createPostFromUris(
        feedId: String,
        body: String,
        uris: List<Uri>,
        context: Context,
        // Per-file captions aligned with `uris`; sent only when one is set.
        captions: List<String> = emptyList(),
        checkin: PlaceData? = null,
        travellingOrigin: PlaceData? = null,
        travellingDestination: PlaceData? = null,
        tracker: UploadTracker? = null,
        // The accounts on other networks the post goes to, overriding the
        // feed's list; null leaves it to the feed.
        accounts: List<String>? = null,
        features: PostFeatures? = null,
        poll: PollInput? = null,
    ) {
        val files = fileStore.cacheFiles(uris)
        try {
            val multipartBody = buildPostBody(feedId, body, checkin, travellingOrigin, travellingDestination)
            for (file in files) {
                multipartBody.addPart(fileStore.filePart("files", file, tracker = tracker))
            }
            if (captions.any { it.isNotEmpty() }) {
                multipartBody.addFormDataPart("captions", Gson().toJson(captions))
            }
            if (accounts != null) {
                multipartBody.addFormDataPart("accounts", Gson().toJson(accounts))
            }
            multipartBody.features(features)
            if (poll != null) {
                multipartBody.addFormDataPart("poll", Gson().toJson(poll))
            }
            api.createPost(feedId, multipartBody.build()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        } finally {
            fileStore.deleteAll(files)
        }
    }

    private fun placeMap(place: PlaceData): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "name" to place.name,
            "lat" to place.lat,
            "lon" to place.lon
        )
        if (place.category.isNotEmpty()) {
            map["category"] = place.category
        }
        return map
    }

    private fun buildPostBody(
        feedId: String,
        body: String,
        checkin: PlaceData?,
        travellingOrigin: PlaceData?,
        travellingDestination: PlaceData?
    ): MultipartBody.Builder {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("feed", feedId)
            .addFormDataPart("body", body)

        val data = mutableMapOf<String, Any>()
        if (checkin != null) {
            data["checkin"] = placeMap(checkin)
        }
        if (travellingOrigin != null || travellingDestination != null) {
            val travelling = mutableMapOf<String, Any>()
            travellingOrigin?.let { travelling["origin"] = placeMap(it) }
            travellingDestination?.let { travelling["destination"] = placeMap(it) }
            data["travelling"] = travelling
        }
        if (data.isNotEmpty()) {
            builder.addFormDataPart("data", Gson().toJson(data))
        }

        return builder
    }

    suspend fun editPost(
        feedId: String,
        postId: String,
        body: String,
        // Null leaves the post's attachments untouched.
        order: List<String>?,
        newFiles: List<Uri>,
        context: Context,
        // Caption edits keyed by attachment id or "new:N" placeholder.
        captions: Map<String, String> = emptyMap(),
        checkin: PlaceData? = null,
        travellingOrigin: PlaceData? = null,
        travellingDestination: PlaceData? = null,
        tracker: UploadTracker? = null,
        features: PostFeatures? = null,
    ) {
        val files = fileStore.cacheFiles(newFiles)
        try {
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("feed", feedId)
                .addFormDataPart("post", postId)
                .addFormDataPart("body", body)

            val data = mutableMapOf<String, Any>()
            if (checkin != null) {
                data["checkin"] = placeMap(checkin)
            }
            if (travellingOrigin != null || travellingDestination != null) {
                val travelling = mutableMapOf<String, Any>()
                travellingOrigin?.let { travelling["origin"] = placeMap(it) }
                travellingDestination?.let { travelling["destination"] = placeMap(it) }
                data["travelling"] = travelling
            }
            if (data.isNotEmpty()) {
                builder.addFormDataPart("data", Gson().toJson(data))
            }

            addEditOrder(builder, order)

            for (file in files) {
                builder.addPart(fileStore.filePart("files", file, tracker = tracker))
            }
            if (captions.isNotEmpty()) {
                builder.addFormDataPart("captions", Gson().toJson(captions))
            }
            builder.features(features)
            api.editPost(feedId, postId, builder.build()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        } finally {
            fileStore.deleteAll(files)
        }
    }

    suspend fun deletePost(feedId: String, postId: String) {
        try {
            api.deletePost(feedId, postId).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun reactToPost(feedId: String, postId: String, reaction: String) {
        try {
            api.reactToPost(feedId, postId, reaction).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Read one post. A [shared] read is for a caller that waited
     * [REFRESH_DEBOUNCE] after the change it reloads for: it joins a read of
     * the same post begun within that wait, which started after the change and
     * so already holds it. The post screen and the feed under it reload for
     * the same frame, and make one request between them.
     */
    suspend fun getPost(
        feedId: String,
        postId: String,
        shared: Boolean = false,
    ): PostDetailResult {
        if (!shared) {
            return readPost(feedId, postId)
        }
        val now = SystemClock.elapsedRealtime()
        val read = synchronized(postReads) {
            postReads[postId]?.takeIf { held ->
                held.answer.isActive && now - held.started <= REFRESH_DEBOUNCE.inWholeMilliseconds
            } ?: PostRead(now, readScope.async { readPost(feedId, postId) }).also { started ->
                postReads[postId] = started
                started.answer.invokeOnCompletion { _ ->
                    synchronized(postReads) {
                        if (postReads[postId] === started) {
                            postReads.remove(postId)
                        }
                    }
                }
            }
        }
        return read.answer.await()
    }

    private suspend fun readPost(feedId: String, postId: String): PostDetailResult {
        return try {
            postDetail(api.getPost(feedId, postId).unwrap())
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** A shared [getPost] under way, and when it began. */
    private class PostRead(val started: Long, val answer: Deferred<PostDetailResult>)

    /** The shared [getPost] reads under way, by post. */
    private val postReads = mutableMapOf<String, PostRead>()

    private val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getNewPostFeeds(feedId: String): List<Feed> {
        return try {
            api.getNewPostFeeds(feedId).unwrap().feeds
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Comments ---

    /**
     * [attachment] anchors a top-level comment to one of the post's own
     * attachment ids; the server refuses any other. Replies take no anchor.
     */
    suspend fun createComment(
        feedId: String,
        postId: String,
        body: String,
        parent: String? = null,
        files: List<Uri>,
        context: Context,
        attachment: String? = null,
        tracker: UploadTracker? = null,
    ): Comment {
        val cachedFiles = fileStore.cacheFiles(files)
        return try {
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("feed", feedId)
                .addFormDataPart("post", postId)
                .addFormDataPart("body", body)
            if (parent != null) {
                builder.addFormDataPart("parent", parent)
            } else if (!attachment.isNullOrEmpty()) {
                builder.addFormDataPart("attachment", attachment)
            }
            for (file in cachedFiles) {
                builder.addPart(fileStore.filePart("files", file, tracker = tracker))
            }
            api.createComment(feedId, postId, builder.build()).unwrap().comment
        } catch (e: Exception) {
            throw e.toMochiError()
        } finally {
            fileStore.deleteAll(cachedFiles)
        }
    }

    suspend fun editComment(feedId: String, postId: String, commentId: String, body: String) {
        try {
            api.editComment(feedId, postId, commentId, body).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteComment(feedId: String, postId: String, commentId: String) {
        try {
            api.deleteComment(feedId, postId, commentId).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun reactToComment(feedId: String, postId: String, commentId: String, reaction: String) {
        try {
            api.reactToComment(feedId, postId, commentId, reaction).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Access control ---

    suspend fun getAccessRules(feedId: String): List<AccessRule> {
        return try {
            api.getAccessRules(feedId).unwrap().rules
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setAccess(feedId: String, subject: String, level: String) {
        try {
            api.setAccess(
                feedId,
                AccessSetRequest(feed = feedId, subject = subject, level = level)
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun revokeAccess(feedId: String, subject: String) {
        try {
            api.revokeAccess(
                feedId,
                AccessRevokeRequest(feed = feedId, subject = subject)
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Sources ---

    suspend fun getSources(feedId: String): List<Source> {
        return try {
            api.getSources(feedId).unwrap().sources
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun addSource(feedId: String, url: String, type: String, account: String? = null): AddSourceResult {
        val response = api.addSource(feedId, AddSourceRequest(feed = feedId, type = type, url = url, account = account))
        if (response.isSuccessful) {
            val data = response.body()?.data
                ?: throw MochiError.Unknown()
            return AddSourceResult(source = data.source, suggestedCredibility = data.suggestedCredibility, ingested = data.ingested)
        }
        // A `permission_required` 403 carries the requesting app + permission
        // key in its body — surface it distinctly so the caller can offer to
        // grant it; every other failure maps to the standard error.
        val body = response.errorBody()?.string()
        if (response.code() == 403 && !body.isNullOrBlank()) {
            val permission = runCatching {
                Gson().fromJson(body, PermissionRequiredBody::class.java)
            }.getOrNull()
            if (permission?.error == "permission_required" &&
                !permission.app.isNullOrEmpty() && !permission.permission.isNullOrEmpty()) {
                throw PermissionRequiredException(permission.app, permission.permission)
            }
        }
        // A structured `{"error": "Feed returned status 403"}` body carries the
        // server's message — wrap it in an ApiException and let toMochiError()
        // map it. Anything else (empty or an HTML gateway page) is a network
        // failure.
        val trimmed = body?.trimStart()
        val apiError = if (!trimmed.isNullOrEmpty() && trimmed.startsWith("{")) {
            runCatching { Gson().fromJson(trimmed, ApiError::class.java) }.getOrNull()
        } else {
            null
        }
        throw apiError?.let { error -> ApiException(response.code(), error).toMochiError() }
            ?: MochiError.NetworkError()
    }

    suspend fun editSource(
        feedId: String,
        id: String,
        name: String? = null,
        credibility: Int? = null,
        transform: String? = null
    ) {
        try {
            api.editSource(feedId, id, name, credibility, transform).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun removeSource(feedId: String, id: String, deletePosts: Boolean = false) {
        try {
            api.removeSource(feedId, id, deletePosts).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun pollSource(feedId: String, source: String) {
        try {
            api.pollSource(feedId, source).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Follow a followed source's person again where the follow is gone. */
    suspend fun followSource(feedId: String, source: String): String {
        try {
            return api.followSource(feedId, source).unwrap().state
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Tags ---

    suspend fun getTags(feedId: String): List<Tag> {
        return try {
            api.getTags(feedId).unwrap().tags
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getPostTags(feedId: String, postId: String): List<Tag> {
        return try {
            api.getPostTags(feedId, postId).unwrap().tags
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun addTag(feedId: String, postId: String, label: String, qid: String? = null) {
        try {
            api.addTag(feedId, postId, label, qid).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }


    suspend fun adjustInterest(feedId: String, qid: String?, label: String?, direction: String) {
        try {
            api.adjustInterest(feedId, qid, label, direction).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- AI ---

    suspend fun listAiAccounts(): List<org.mochios.android.model.Account> {
        return try {
            api.listAccounts(capability = "ai").unwrapRaw()
        } catch (e: Exception) {
            Log.w("FeedsRepository", "listAiAccounts failed", e)
            emptyList()
        }
    }

    suspend fun setAiSettings(feedId: String, mode: String, account: String = "") {
        try {
            api.setAiSettings(feedId, mode, account).unwrap()
            return
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getAiPrompts(feedId: String): Pair<Map<String, String>, Map<String, String>> {
        return try {
            val response = api.getAiPrompts(feedId).unwrap()
            response.defaults to response.prompts
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setAiPrompt(feedId: String, type: String, prompt: String) {
        try {
            api.setAiPrompt(feedId, type, prompt).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Notifications ---

    suspend fun clearNotifications(feedId: String) {
        try {
            api.clearNotifications(feedId).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getNotifications(feedId: String): FeedNotificationSettings {
        try {
            return api.getNotifications(feedId).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setNotification(feedId: String, kind: String, enabled: Boolean): FeedNotificationSettings {
        try {
            return api.setNotification(feedId, kind, enabled.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Follow or unfollow a post: every reply and reaction in a followed one notifies. */
    suspend fun followPost(feedId: String, postId: String, following: Boolean) {
        try {
            if (following) api.followPost(feedId, postId).unwrap() else api.unfollowPost(feedId, postId).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Members ---

    suspend fun getMembers(feedId: String): List<Member> {
        return try {
            api.getMembers(feedId).unwrap().members
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun removeMember(feedId: String, member: String) {
        try {
            api.removeMember(feedId, member).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun searchMembers(feedId: String, query: String): List<Member> {
        return try {
            api.searchMembers(feedId, query).unwrap().members
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Banner ---


    suspend fun setBanner(feedId: String, banner: String) {
        try {
            api.setBanner(feedId, banner).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Sort persistence ---

    suspend fun setFeedSort(feedId: String, sort: String): String {
        return try {
            api.setFeedSort(feedId, sort).unwrap().sort.also { forget() }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setGlobalSort(sort: String): String {
        return try {
            api.setGlobalSort(sort).unwrap().sort.also { forget() }
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /** Every feed the user holds marked read, for "All feeds". */
    suspend fun markEveryFeedRead() = mapped { api.markEveryFeedRead().unwrap().also { forget() } }

    // --- Other networks ---

    /** Pin a post of a feed the user owns, or unpin it; answers when it was pinned (0 for not). */
    suspend fun pinPost(feedId: String, postId: String, pinned: Boolean): Long = mapped {
        if (pinned) api.pinPost(feedId, postId, postId).unwrap().pinned
        else api.unpinPost(feedId, postId, postId).unwrap().pinned
    }

    /** Vote in a post's poll; answers the poll as recounted and the options voted for. */
    suspend fun votePoll(feedId: String, postId: String, options: List<Int>): Pair<PostPoll?, List<Int>> = mapped {
        val answer = api.votePoll(feedId, postId, feedId, postId, options).unwrap()
        answer.poll to answer.voted
    }

    /** Boost a post from another network from a hosted account, or take the boost back. */
    suspend fun boostPost(feedId: String, postId: String, account: String, boosted: Boolean): List<PostCopy> = mapped {
        if (boosted) api.boostPost(feedId, postId, postId, account).unwrap().copies
        else api.unboostPost(feedId, postId, postId, account).unwrap().copies
    }

    /** Block, mute or report the person on another network behind a comment. */
    suspend fun moderateCommenter(feedId: String, postId: String, commentId: String, action: String, reason: String = "") {
        mapped {
            when (action) {
                "block" -> api.blockCommenter(feedId, postId, commentId).unwrap()
                "mute" -> api.muteCommenter(feedId, postId, commentId).unwrap()
                else -> api.reportCommenter(feedId, postId, commentId, reason).unwrap()
            }
        }
    }

    suspend fun getFilters(feedId: String): List<Filter> = mapped { api.getFilters(feedId).unwrap().filters }

    suspend fun addFilter(feedId: String, phrase: String): List<Filter> =
        mapped { api.addFilter(feedId, phrase).unwrap().filters }

    suspend fun removeFilter(feedId: String, filter: String): List<Filter> =
        mapped { api.removeFilter(feedId, filter).unwrap().filters }

    suspend fun importFollows(feedId: String, addresses: String): Imported =
        mapped { api.importFollows(feedId, addresses).unwrap() }

    suspend fun people(): List<Person> = mapped { api.getPeople().unwrap().people }

    suspend fun lookup(query: String): Lookup = mapped { api.lookup(query).unwrapRaw() }

    /** The user's accounts on other networks, hosted or linked. */
    suspend fun networkAccounts(): List<PublishAccount> = mapped { api.listNetworkAccounts().unwrapRaw() }

    suspend fun getPublish(feedId: String): PublishState = mapped { api.getPublish(feedId).unwrapRaw() }

    suspend fun setPublish(feedId: String, accounts: List<String>): PublishSaved =
        mapped { api.setPublish(feedId, Gson().toJson(accounts)).unwrapRaw() }

    suspend fun forgetCopies(feedId: String, account: String): Int =
        mapped { api.forgetCopies(feedId, account).unwrapRaw().deleted }

    suspend fun setGenerator(feedId: String, account: String, on: Boolean): Map<String, String> =
        mapped { api.setGenerator(feedId, account, if (on) "true" else "false").unwrapRaw().generators }

    suspend fun importAccount(feedId: String, account: String, kind: String): Imported =
        mapped { api.importAccount(feedId, account, kind).unwrapRaw() }

    suspend fun backups(feedId: String, account: String): List<String> =
        mapped { api.getBackups(feedId, account).unwrapRaw().files }

    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw e.toMochiError()
    }
}

/**
 * A post's features as the form carries them: each only when the composer set
 * it, so an edit that says nothing leaves the post's own.
 */
internal fun MultipartBody.Builder.features(features: PostFeatures?): MultipartBody.Builder = apply {
    if (features == null) return@apply
    features.visibility?.takeIf { it.isNotEmpty() }?.let { addFormDataPart("visibility", it) }
    features.warning?.let { addFormDataPart("warning", it) }
    features.sensitive?.let { addFormDataPart("sensitive", if (it) "true" else "false") }
    features.language?.takeIf { it.isNotEmpty() }?.let { addFormDataPart("language", it) }
}
