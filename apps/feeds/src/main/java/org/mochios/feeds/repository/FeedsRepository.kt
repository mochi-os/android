// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import android.content.ContentResolver
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.ApiError
import org.mochios.android.api.ApiException
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.android.model.AccessRule
import org.mochios.android.model.Comment
import org.mochios.android.model.PlaceData
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.InterestSuggestion
import org.mochios.feeds.api.MenuApi
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
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.Source
import org.mochios.feeds.model.Tag
import org.mochios.feeds.model.User
import javax.inject.Inject
import javax.inject.Singleton

data class FeedInfoResult(
    val feed: Feed,
    val permissions: Permissions
)

/**
 * The class-level feeds overview returned by `-/info`: the subscribed feed
 * list and whether the server has any AI provider configured (which gates the
 * AI sort option).
 */
data class FeedsInfoResult(
    val feeds: List<Feed>,
    val hasAi: Boolean
)

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

data class ProbeResult(
    val feed: Feed?,
    val type: String
)

data class AddSourceResult(
    val source: Source,
    val suggestedCredibility: Int? = null
)

/** Parsed body of a `permission_required` 403 from `sources/add`. */
private data class PermissionRequiredBody(
    val app: String? = null,
    val error: String? = null,
    val permission: String? = null
)

/**
 * Thrown when `sources/add` returns `permission_required`: the app must first
 * be granted [permission] before the source can be added. Carries the
 * requesting [app] entity id so the caller can resolve a name and grant it.
 */
class PermissionRequiredException(
    val app: String,
    val permission: String
) : Exception("permission_required: $permission")

/**
 * A one-shot interest-suggestion prompt raised by subscribing to a feed, to be
 * shown once on that feed's screen. Held in the [@Singleton][Singleton]
 * repository because the subscribe action (FindFeeds) and the feed screen live
 * in different ViewModels.
 */
data class PendingInterestSuggestion(
    val feedId: String,
    val suggestions: List<InterestSuggestion>
)

@Singleton
class FeedsRepository @Inject constructor(
    private val api: FeedsApi,
    private val menuApi: MenuApi
) {

    // In-memory cache: feedId -> (posts, hasMore, timestamp)
    private val postCache = mutableMapOf<String, CachedPosts>()
    private val feedInfoCache = mutableMapOf<String, CachedFeedInfo>()
    private val cacheMaxAge = 60_000L // 1 minute

    private data class CachedPosts(
        val result: PostListResult,
        val sort: String?,
        val tag: String?,
        val unreadOnly: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private data class CachedFeedInfo(
        val result: FeedInfoResult,
        val timestamp: Long = System.currentTimeMillis()
    )

    // One-shot interest suggestions raised by a successful subscribe, consumed
    // once by the matching feed screen. Bridges the FindFeeds subscribe flow and
    // the feed screen, which are separate ViewModels.
    private val _pendingInterestSuggestion = MutableStateFlow<PendingInterestSuggestion?>(null)
    val pendingInterestSuggestion: StateFlow<PendingInterestSuggestion?> =
        _pendingInterestSuggestion.asStateFlow()

    /** Stash suggestions for [feedId] so its feed screen can show them once. */
    fun setPendingInterestSuggestion(feedId: String, suggestions: List<InterestSuggestion>) {
        _pendingInterestSuggestion.value = PendingInterestSuggestion(feedId, suggestions)
    }

    /** Clear the pending suggestion once consumed (or when no longer relevant). */
    fun clearPendingInterestSuggestion() {
        _pendingInterestSuggestion.value = null
    }

    // Emits whenever the viewer's subscriptions change (subscribe/unsubscribe),
    // so the feed-list drawer can reload even without a navigation that would
    // otherwise recreate its ViewModel. Replay-less: only live collectors react.
    private val _subscriptionChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val subscriptionChanges: SharedFlow<Unit> = _subscriptionChanges.asSharedFlow()

    fun getCachedPosts(feedId: String, sort: String?, tag: String?, unreadOnly: Boolean): PostListResult? {
        val cached = postCache[feedId] ?: return null
        if (System.currentTimeMillis() - cached.timestamp > cacheMaxAge) return null
        if (cached.sort != sort || cached.tag != tag || cached.unreadOnly != unreadOnly) return null
        return cached.result
    }

    fun getCachedFeedInfo(feedId: String): FeedInfoResult? {
        val cached = feedInfoCache[feedId] ?: return null
        if (System.currentTimeMillis() - cached.timestamp > cacheMaxAge) return null
        return cached.result
    }

    fun invalidateCache(feedId: String) {
        postCache.remove(feedId)
        feedInfoCache.remove(feedId)
    }

    // --- Class-level operations ---

    suspend fun listFeeds(): List<Feed> {
        return getFeedsInfo().feeds
    }

    /**
     * Fetch the full class-level feeds overview (`-/info`): feed list, the
     * user's global default sort, and the server's AI availability.
     */
    suspend fun getFeedsInfo(): FeedsInfoResult {
        return try {
            val response = api.getInfo().unwrap()
            FeedsInfoResult(
                feeds = response.feeds,
                hasAi = response.hasAi
            )
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun createFeed(name: String, privacy: String, memories: Boolean): Feed {
        return try {
            val response = api.createFeed(
                CreateFeedRequest(name = name, privacy = privacy, memories = memories.toString())
            ).unwrap()
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
            val response = api.probeUrl(url).unwrap()
            ProbeResult(feed = response.feed, type = response.type)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun subscribeFeed(feed: String, server: String? = null) {
        try {
            // Server hint omitted from the request body for now.
            api.subscribe(SubscribeRequest(feed = feed)).unwrap()
            _subscriptionChanges.emit(Unit)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun unsubscribeFeed(feed: String, server: String? = null) {
        try {
            // Server hint omitted from the request body for now.
            api.unsubscribe(UnsubscribeRequest(feed = feed)).unwrap()
            _subscriptionChanges.emit(Unit)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getRssToken(entity: String, mode: String): String {
        return try {
            api.getRssToken(entity, mode).unwrap().token
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
            val response = api.getFeedInfo(feedId).unwrap()
            val result = FeedInfoResult(feed = response.feed, permissions = response.permissions)
            feedInfoCache[feedId] = CachedFeedInfo(result)
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
                permissions = response.permissions
            )
            if (isFirstPage) {
                postCache[feedId] = CachedPosts(result, sort, tag, unreadOnly)
            }
            result
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteFeed(feedId: String) {
        try {
            api.deleteFeed(feedId).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun renameFeed(feedId: String, name: String) {
        try {
            api.renameFeed(feedId, name).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun markAllRead(feedId: String) {
        try {
            api.markAllRead(feedId).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Lazy og:image fetch for RSS posts whose feed didn't carry an
     * inline image. Server-side action_post_image fetches the article URL
     * once per day per post and caches the result on the post row;
     * subsequent calls return the cached value cheaply. Returns "" when
     * no image could be extracted (or the post is non-RSS).
     */
    suspend fun getPostImage(feedId: String, postId: String): String {
        return try {
            api.getPostImage(feedId, postId).unwrap().image
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun markPostsRead(feedId: String, postIds: List<String>) {
        if (postIds.isEmpty()) return
        try {
            api.markPostsRead(feedId, postIds).unwrap()
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
                val mediaType = fileTypes.getOrElse(index) { "application/octet-stream" }
                multipartBody.addFormDataPart("files", name, bytes.toRequestBody(mediaType.toMediaTypeOrNull()))
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
        contentResolver: ContentResolver,
        checkin: PlaceData? = null,
        travellingOrigin: PlaceData? = null,
        travellingDestination: PlaceData? = null
    ) {
        try {
            val multipartBody = buildPostBody(feedId, body, checkin, travellingOrigin, travellingDestination)
            for (uri in uris) {
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = getFileName(contentResolver, uri)
                val bytes = contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw IllegalStateException("Cannot read file")
                multipartBody.addFormDataPart("files", fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            }
            api.createPost(feedId, multipartBody.build()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
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
        order: List<String>,
        newFiles: List<Uri>,
        contentResolver: ContentResolver,
        checkin: PlaceData? = null,
        travellingOrigin: PlaceData? = null,
        travellingDestination: PlaceData? = null
    ) {
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

            for (item in order) {
                builder.addFormDataPart("order", item)
            }

            for (uri in newFiles) {
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = getFileName(contentResolver, uri)
                val bytes = contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw IllegalStateException("Cannot read file")
                builder.addFormDataPart("files", fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            }
            api.editPost(feedId, postId, builder.build()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
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

    suspend fun getPost(feedId: String, postId: String): PostDetailResult {
        return try {
            val response = api.getPost(feedId, postId).unwrap()
            PostDetailResult(post = response.posts.first(), permissions = response.permissions)
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getNewPostFeeds(feedId: String): List<Feed> {
        return try {
            api.getNewPostFeeds(feedId).unwrap().feeds
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Comments ---

    suspend fun createComment(
        feedId: String,
        postId: String,
        body: String,
        parent: String? = null,
        files: List<Uri>,
        contentResolver: ContentResolver
    ): Comment {
        return try {
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("feed", feedId)
                .addFormDataPart("post", postId)
                .addFormDataPart("body", body)
            if (parent != null) {
                builder.addFormDataPart("parent", parent)
            }
            for (uri in files) {
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = getFileName(contentResolver, uri)
                val bytes = contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw IllegalStateException("Cannot read file")
                builder.addFormDataPart("files", fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            }
            api.createComment(feedId, postId, builder.build()).unwrap().comment
        } catch (e: Exception) {
            throw e.toMochiError()
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

    suspend fun addSource(feedId: String, url: String, type: String): AddSourceResult {
        val response = api.addSource(feedId, AddSourceRequest(feed = feedId, type = type, url = url))
        if (response.isSuccessful) {
            val data = response.body()?.data
                ?: throw MochiError.Unknown()
            return AddSourceResult(source = data.source, suggestedCredibility = data.suggestedCredibility)
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

    suspend fun removeTag(feedId: String, postId: String, id: String) {
        try {
            api.removeTag(feedId, postId, id).unwrap()
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

    suspend fun getSuggestedInterests(feedId: String): List<InterestSuggestion> {
        return try {
            api.getSuggestedInterests(feedId).unwrap().suggestions
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- AI ---

    suspend fun listAiAccounts(): List<org.mochios.android.model.Account> {
        return try {
            api.listAccounts(capability = "ai").unwrap()
        } catch (_: Exception) {
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

    // --- Members ---

    suspend fun getMembers(feedId: String): List<Member> {
        return try {
            api.getMembers(feedId).unwrap().members
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun addMember(feedId: String, member: String) {
        try {
            api.addMember(feedId, member).unwrap()
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

    suspend fun getBanner(feedId: String): String {
        return try {
            api.getBanner(feedId).unwrap().banner
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

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
            api.setFeedSort(feedId, sort).unwrap().sort
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setGlobalSort(sort: String): String {
        return try {
            api.setGlobalSort(sort).unwrap().sort
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getGlobalSort(): String {
        return try {
            api.getGlobalSortInfo().unwrap().settings.sort
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // --- Helpers ---

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var name = "file"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }
}
