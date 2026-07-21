// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.api

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import org.mochios.android.api.ApiResponse
import org.mochios.android.model.AccessRule
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Group
import org.mochios.feeds.model.Member
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.SavedListResponse
import org.mochios.feeds.model.SavedToggleResponse
import org.mochios.feeds.model.Source
import org.mochios.feeds.model.Tag
import org.mochios.feeds.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Response wrapper types

data class FeedListResponse(
    val feeds: List<Feed> = emptyList(),
    val entity: Boolean = false,
    val hasAi: Boolean = false,
    val settings: FeedSettings = FeedSettings(),
    @SerializedName("user_id") val userId: String = ""
)

data class FeedCreateResponse(
    val fingerprint: String = "",
    val id: String = ""
)

data class FeedInfoResponse(
    val feed: Feed,
    val permissions: Permissions = Permissions()
)

data class PostListResponse(
    val posts: List<Post> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long = 0,
    // Nullable: the "All feeds" aggregate endpoint (-/posts) has no single feed
    // to scope permissions to and returns null. A non-null type would let Gson
    // set the field to null and then NPE when constructing PostListResult.
    val permissions: Permissions? = null,
    val owner: Boolean = false
)

data class PostDetailResponse(
    val posts: List<Post> = emptyList(),
    val permissions: Permissions = Permissions()
)

data class PostImageResponse(
    val image: String = ""
)

data class PostCreateResponse(
    val id: String = "",
    val feed: Feed? = null,
    val attachments: List<Attachment> = emptyList()
)

data class SuccessResponse(
    val success: Boolean = false
)

data class CreateFeedRequest(
    val name: String,
    val privacy: String,
    val memories: String
)

data class SubscribeRequest(
    val feed: String,
    val server: String? = null
)

data class UnsubscribeRequest(
    val feed: String,
    val server: String? = null
)

data class AccessSetRequest(
    val feed: String,
    val subject: String,
    val level: String
)

data class AccessRevokeRequest(
    val feed: String,
    val subject: String
)

data class AddSourceRequest(
    val feed: String,
    val type: String,
    val url: String
)

data class RecommendationsResponse(
    val feeds: List<Feed> = emptyList()
)

data class ProbeResponse(
    val feed: Feed? = null,
    val type: String = ""
)

data class RssTokenResponse(
    val token: String = ""
)

/**
 * Response of `{feedId}/-/share`: the shareable [link] (`mochi://<peer>/<feed>`)
 * plus the [peer] and [feed] it is built from.
 */
data class ShareResponse(
    val feed: String = "",
    val link: String = "",
    val peer: String = ""
)

data class BannerResponse(
    val banner: String = ""
)

data class SourceListResponse(
    val sources: List<Source> = emptyList()
)

data class SourceAddResponse(
    val source: Source,
    @SerializedName("suggested_credibility")
    val suggestedCredibility: Int? = null
)

data class TagListResponse(
    val tags: List<Tag> = emptyList()
)

data class AccessListResponse(
    val rules: List<AccessRule> = emptyList()
)

data class AiPromptsResponse(
    val defaults: Map<String, String> = emptyMap(),
    val prompts: Map<String, String> = emptyMap()
)

data class CommentCreateResponse(
    val comment: Comment
)

data class NewPostResponse(
    val feeds: List<Feed> = emptyList()
)

data class MemberListResponse(
    val members: List<Member> = emptyList()
)

data class MemberSearchResponse(
    val members: List<Member> = emptyList()
)

data class UserSearchResponse(
    @SerializedName("results") val results: List<User> = emptyList()
)

data class GroupListResponse(
    val groups: List<Group> = emptyList()
)

interface FeedsApi {

    // --- Class-level endpoints (no entity) ---

    @GET("-/info")
    suspend fun getInfo(): Response<ApiResponse<FeedListResponse>>

    @POST("-/create")
    suspend fun createFeed(
        @Body body: CreateFeedRequest
    ): Response<ApiResponse<FeedCreateResponse>>

    // directory/search returns a bare array of feeds in `data`, not a {feeds:[...]} object.
    @GET("-/directory/search")
    suspend fun searchDirectory(
        @Query("search") query: String
    ): Response<ApiResponse<List<Feed>>>

    @GET("-/recommendations")
    suspend fun getRecommendations(): Response<ApiResponse<RecommendationsResponse>>

    @GET("-/probe")
    suspend fun probeUrl(
        @Query("url") url: String
    ): Response<ApiResponse<ProbeResponse>>

    @POST("-/subscribe")
    suspend fun subscribe(
        @Body body: SubscribeRequest
    ): Response<ApiResponse<SuccessResponse>>

    @POST("-/unsubscribe")
    suspend fun unsubscribe(
        @Body body: UnsubscribeRequest
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("-/rss/token")
    suspend fun getRssToken(
        @Field("entity") entity: String,
        @Field("mode") mode: String
    ): Response<ApiResponse<RssTokenResponse>>

    @POST("{feedId}/-/share")
    suspend fun shareFeed(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<ShareResponse>>

    @FormUrlEncoded
    @POST("-/users/search")
    suspend fun searchUsers(
        @Field("search") query: String
    ): Response<ApiResponse<UserSearchResponse>>

    @GET("-/groups")
    suspend fun getGroups(): Response<ApiResponse<GroupListResponse>>

    // --- Saved (read-later) — class-level, per-user list spanning all feeds ---

    @POST("-/saved/list")
    suspend fun listSaved(): Response<ApiResponse<SavedListResponse>>

    @FormUrlEncoded
    @POST("-/saved/add")
    suspend fun addSaved(
        @Field("post") post: String,
        @Field("data") data: String
    ): Response<ApiResponse<SavedToggleResponse>>

    @FormUrlEncoded
    @POST("-/saved/remove")
    suspend fun removeSaved(
        @Field("post") post: String
    ): Response<ApiResponse<SavedToggleResponse>>

    @POST("-/saved/clear")
    suspend fun clearSaved(): Response<ApiResponse<SavedToggleResponse>>

    // --- Entity-level endpoints ---

    @GET("{feedId}/-/info")
    suspend fun getFeedInfo(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<FeedInfoResponse>>

    @GET("{feedId}/-/posts")
    suspend fun getPosts(
        @Path("feedId") feedId: String,
        @Query("before") before: String? = null,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String? = null,
        @Query("tag") tag: String? = null,
        @Query("unread") unread: String? = null
    ): Response<ApiResponse<PostListResponse>>

    // "All feeds" aggregate: posts merged across every subscribed feed in one
    // indexed query, paginated by the same before/offset cursors as a single
    // feed. Class-level (no entity) — there's no single feed to scope it to.
    @GET("-/posts")
    suspend fun getAllPosts(
        @Query("before") before: String? = null,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String? = null,
        @Query("unread") unread: String? = null
    ): Response<ApiResponse<PostListResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/delete")
    suspend fun deleteFeed(
        @Path("feedId") feedId: String,
        @Field("confirm") confirm: Boolean = true // contract-ok: client-side confirmation flag; not read server-side
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/rename")
    suspend fun renameFeed(
        @Path("feedId") feedId: String,
        @Field("name") name: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{feedId}/-/read-all")
    suspend fun markAllRead(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/posts/read")
    suspend fun markPostsRead(
        @Path("feedId") feedId: String,
        @Field("post") postIds: List<String>
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{feedId}/-/post/create")
    suspend fun createPost(
        @Path("feedId") feedId: String,
        @retrofit2.http.Body body: RequestBody
    ): Response<ApiResponse<PostCreateResponse>>

    @GET("{feedId}/-/post/new")
    suspend fun getNewPostFeeds(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<NewPostResponse>>

    @POST("{feedId}/-/{postId}/edit")
    suspend fun editPost(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String,
        @retrofit2.http.Body body: RequestBody
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{feedId}/-/{postId}/delete")
    suspend fun deletePost(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/{postId}/react")
    suspend fun reactToPost(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String,
        @Field("reaction") reaction: String
    ): Response<ApiResponse<SuccessResponse>>

    @GET("{feedId}/-/{postId}")
    suspend fun getPost(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String
    ): Response<ApiResponse<PostDetailResponse>>

    // Bare JSON response (no `{data:…}` envelope) — decode with unwrapRaw.
    @GET("{feedId}/-/{postId}/image")
    suspend fun getPostImage(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String
    ): Response<PostImageResponse>

    @POST("{feedId}/-/{postId}/comment/create")
    suspend fun createComment(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String,
        @retrofit2.http.Body body: RequestBody
    ): Response<ApiResponse<CommentCreateResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/{postId}/{commentId}/edit")
    suspend fun editComment(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Field("body") body: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{feedId}/-/{postId}/{commentId}/delete")
    suspend fun deleteComment(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/{postId}/comment/react")
    suspend fun reactToComment(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String,
        @Field("comment") comment: String,
        @Field("reaction") reaction: String
    ): Response<ApiResponse<SuccessResponse>>

    // --- Access control ---

    @GET("{feedId}/-/access")
    suspend fun getAccessRules(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<AccessListResponse>>

    @POST("{feedId}/-/access/set")
    suspend fun setAccess(
        @Path("feedId") feedId: String,
        @Body body: AccessSetRequest
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{feedId}/-/access/revoke")
    suspend fun revokeAccess(
        @Path("feedId") feedId: String,
        @Body body: AccessRevokeRequest
    ): Response<ApiResponse<SuccessResponse>>

    // --- Sources ---

    @GET("{feedId}/-/sources")
    suspend fun getSources(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<SourceListResponse>>

    @POST("{feedId}/-/sources/add")
    suspend fun addSource(
        @Path("feedId") feedId: String,
        @Body body: AddSourceRequest
    ): Response<ApiResponse<SourceAddResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/sources/edit")
    suspend fun editSource(
        @Path("feedId") feedId: String,
        @Field("source") id: String,
        @Field("name") name: String?,
        @Field("credibility") credibility: Int?,
        @Field("transform") transform: String?
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/sources/remove")
    suspend fun removeSource(
        @Path("feedId") feedId: String,
        @Field("source") id: String,
        @Field("delete_posts") deletePosts: Boolean?
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/sources/poll")
    suspend fun pollSource(
        @Path("feedId") feedId: String,
        @Field("source") source: String
    ): Response<ApiResponse<SuccessResponse>>

    // --- Tags ---

    @GET("{feedId}/-/tags")
    suspend fun getTags(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<TagListResponse>>

    @GET("{feedId}/-/{postId}/tags")
    suspend fun getPostTags(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String
    ): Response<ApiResponse<TagListResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/{postId}/tags/add")
    suspend fun addTag(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String,
        @Field("label") label: String,
        @Field("qid") qid: String? // contract-ok: server re-derives qid from label; provided qid is advisory
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/{postId}/tags/remove")
    suspend fun removeTag(
        @Path("feedId") feedId: String,
        @Path("postId") postId: String,
        @Field("tag") id: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/tags/interest")
    suspend fun adjustInterest(
        @Path("feedId") feedId: String,
        @Field("qid") qid: String?,
        @Field("label") label: String?,
        @Field("direction") direction: String
    ): Response<ApiResponse<SuccessResponse>>

    // --- AI ---

    // The accounts endpoint returns a bare JSON array (no `{data:…}` envelope),
    // so decode it as a plain list, not ApiResponse.
    @GET("-/accounts/list")
    suspend fun listAccounts(
        @Query("capability") capability: String,
    ): Response<List<org.mochios.android.model.Account>>

    @FormUrlEncoded
    @POST("{feedId}/-/ai/settings")
    suspend fun setAiSettings(
        @Path("feedId") feedId: String,
        @Field("mode") mode: String,
        @Field("account") account: String = "",
    ): Response<ApiResponse<SuccessResponse>>

    @GET("{feedId}/-/ai/prompts/get")
    suspend fun getAiPrompts(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<AiPromptsResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/ai/prompts/set")
    suspend fun setAiPrompt(
        @Path("feedId") feedId: String,
        @Field("type") type: String,
        @Field("prompt") prompt: String
    ): Response<ApiResponse<SuccessResponse>>

    // --- Notifications ---

    @POST("{feedId}/-/notifications/clear")
    suspend fun clearNotifications(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<SuccessResponse>>

    // --- Members ---

    @GET("{feedId}/-/members")
    suspend fun getMembers(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<MemberListResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/members/remove")
    suspend fun removeMember(
        @Path("feedId") feedId: String,
        @Field("member") member: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/members/search")
    suspend fun searchMembers(
        @Path("feedId") feedId: String,
        @Field("q") query: String
    ): Response<ApiResponse<MemberSearchResponse>>

    // --- Banner ---

    @GET("{feedId}/-/banner/get")
    suspend fun getBanner(
        @Path("feedId") feedId: String
    ): Response<ApiResponse<BannerResponse>>

    @FormUrlEncoded
    @POST("{feedId}/-/banner/set")
    suspend fun setBanner(
        @Path("feedId") feedId: String,
        @Field("banner") banner: String
    ): Response<ApiResponse<SuccessResponse>>

    // --- Sort persistence (appended) ---

    @FormUrlEncoded
    @POST("{feedId}/-/sort/set")
    suspend fun setFeedSort(
        @Path("feedId") feedId: String,
        @Field("sort") sort: String
    ): Response<ApiResponse<SortResponse>>

    @FormUrlEncoded
    @POST("-/sort/set")
    suspend fun setGlobalSort(
        @Field("sort") sort: String
    ): Response<ApiResponse<SortResponse>>

    // No dedicated /get endpoint — global sort is returned by `-/info` in
    // settings.sort. Reuse that route with a slim response type.
    @GET("-/info")
    suspend fun getGlobalSortInfo(): Response<ApiResponse<GlobalSortInfoResponse>>
}

data class SortResponse(
    val sort: String = ""
)

data class FeedSettings(
    val sort: String = ""
)

data class GlobalSortInfoResponse(
    val settings: FeedSettings = FeedSettings()
)
