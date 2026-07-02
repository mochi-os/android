// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.mochios.android.api.ApiResponse
import org.mochios.android.model.AccessRule
import org.mochios.forums.model.AiPrompts
import org.mochios.forums.model.AiSettings
import org.mochios.forums.model.DirectoryEntry
import org.mochios.forums.model.Forum
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.ForumMember
import org.mochios.forums.model.Member
import org.mochios.forums.model.ModerationLogEntry
import org.mochios.forums.model.ModerationQueueCounts
import org.mochios.forums.model.ModerationReport
import org.mochios.forums.model.ModerationSettings
import org.mochios.forums.model.ModerationSettingsResponse
import org.mochios.forums.model.Post
import org.mochios.forums.model.RecommendedForum
import org.mochios.forums.model.Restriction
import org.mochios.forums.model.SavedListResponse
import org.mochios.forums.model.SavedToggleResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

data class ForumListResponse(
    val forums: List<Forum> = emptyList(),
    val posts: List<Post> = emptyList(),
    val settings: ForumSettings = ForumSettings()
)

data class ForumSettings(val sort: String = "")

data class ViewForumResponse(
    val forum: Forum = Forum(),
    val posts: List<Post> = emptyList(),
    val member: Member = Member(),
    val can_manage: Boolean = false,
    val can_moderate: Boolean = false,
    val hasMore: Boolean = false,
    val nextCursor: Long? = null
)

data class CreateForumResponse(
    val id: String = "",
    val fingerprint: String = ""
)

data class SearchForumsResponse(val results: List<DirectoryEntry> = emptyList())

data class RecommendationsResponse(val forums: List<RecommendedForum> = emptyList())


data class SubscribeResponse(val already_subscribed: Boolean = false)

data class ViewPostResponse(
    val forum: Forum = Forum(),
    val post: Post = Post(),
    val comments: List<ForumComment> = emptyList(),
    val member: Member = Member(),
    val can_vote: Boolean = false,
    val can_comment: Boolean = false,
    val can_moderate: Boolean = false
)

data class CreatePostResponse(
    val forum: String = "",
    val post: String = ""
)

data class CreateCommentResponse(
    val comment: String = "",
    val forum: String = "",
    val post: String = ""
)

data class SuccessResponse(val success: Boolean = false)

data class ModerationQueueResponse(
    val forum: Forum = Forum(),
    val posts: List<Post> = emptyList(),
    val comments: List<ForumComment> = emptyList(),
    val reports: List<ModerationReport> = emptyList(),
    val counts: ModerationQueueCounts = ModerationQueueCounts(),
)

data class ModerationReportsResponse(
    val forum: Forum = Forum(),
    val reports: List<ModerationReport> = emptyList(),
)

data class RestrictionsResponse(
    val restrictions: List<Restriction> = emptyList(),
)

data class ModerationLogResponse(
    val entries: List<ModerationLogEntry> = emptyList(),
)

data class AccessResponse(
    val forum: Forum = Forum(),
    val rules: List<AccessRule> = emptyList(),
)

data class MembersResponse(
    val forum: Forum = Forum(),
    val members: List<ForumMember> = emptyList(),
)

data class MemberSearchResponse(
    val members: List<ForumMember> = emptyList(),
)

data class BannerResponse(
    val banner: String = "",
)

data class RssTokenResponse(
    val token: String = "",
    val url: String = "",
)

data class ForumTagCount(val label: String = "", val count: Int = 0)

data class ForumTagsResponse(
    val tags: List<ForumTagCount> = emptyList(),
)

data class UserSearchResponse(
    val results: List<org.mochios.forums.model.User> = emptyList(),
)

data class GroupListResponse(
    val groups: List<org.mochios.forums.model.Group> = emptyList(),
)

// action_probe returns a directory-like entry for a forum found by URL.
data class ProbeForumResponse(
    val id: String = "",
    val name: String = "",
    val fingerprint: String = "",
    @com.google.gson.annotations.SerializedName("class") val klass: String = "",
    val server: String = "",
)

data class SortResponse(val sort: String = "")

interface ForumsApi {

    // ---- Forum list / discovery ----

    @GET("-/list")
    suspend fun listForums(@Query("sort") sort: String? = null): Response<ApiResponse<ForumListResponse>>

    @GET("-/information")
    suspend fun getForumsInfo(): Response<ApiResponse<ForumListResponse>>

    @GET("-/new")
    suspend fun getNewForum(): Response<ApiResponse<Map<String, Any>>>

    // ---- Saved (read-later) — class-level, per-user list spanning all forums ----

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

    @FormUrlEncoded
    @POST("-/create")
    suspend fun createForum(
        @Field("name") name: String,
        @Field("privacy") privacy: String? = null
    ): Response<ApiResponse<CreateForumResponse>>

    @GET("-/directory/search")
    suspend fun searchForums(@Query("search") search: String): Response<ApiResponse<SearchForumsResponse>>

    @GET("-/recommendations")
    suspend fun getRecommendations(): Response<ApiResponse<RecommendationsResponse>>

    // Find a forum by pasted URL (proxies to the owning server via P2P).
    @GET("-/probe")
    suspend fun probe(@Query("url") url: String): Response<ApiResponse<ProbeForumResponse>>

    // Proxy to people.users/search via the forums backend (class-level).
    @FormUrlEncoded
    @POST("-/users/search")
    suspend fun searchUsers(@Field("search") query: String): Response<ApiResponse<UserSearchResponse>>

    // Proxy to people.groups/list via the forums backend (class-level).
    @GET("-/groups")
    suspend fun getGroups(): Response<ApiResponse<GroupListResponse>>


    // ---- Forum entity ----

    @GET("{forumId}/-/information")
    suspend fun getForumInfo(@Path("forumId") forumId: String): Response<ApiResponse<Map<String, Any>>>

    @GET("{forumId}/-/posts")
    suspend fun viewForum(
        @Path("forumId") forumId: String,
        @Query("limit") limit: Int? = null,
        @Query("before") before: Long? = null,
        @Query("server") server: String? = null,
        @Query("sort") sort: String? = null,
        @Query("tag") tag: String? = null
    ): Response<ApiResponse<ViewForumResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/subscribe")
    suspend fun subscribe(
        @Path("forumId") forumId: String,
        @Field("forum") forum: String,
        @Field("server") server: String? = null
    ): Response<ApiResponse<SubscribeResponse>>

    @POST("{forumId}/-/unsubscribe")
    suspend fun unsubscribe(@Path("forumId") forumId: String): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/delete")
    suspend fun deleteForum(@Path("forumId") forumId: String): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/rename")
    suspend fun renameForum(
        @Path("forumId") forumId: String,
        @Field("forum") forum: String,
        @Field("name") name: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Posts ----

    @GET("-/post/new")
    suspend fun getNewPost(@Query("forum") forum: String): Response<ApiResponse<Map<String, Any>>>

    @Multipart
    @POST("-/post/create")
    suspend fun createPost(
        @Part("forum") forum: RequestBody,
        @Part("title") title: RequestBody,
        @Part("body") body: RequestBody,
        @Part attachments: List<MultipartBody.Part>
    ): Response<ApiResponse<CreatePostResponse>>

    @GET("{forumId}/-/{postId}")
    suspend fun viewPost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Query("server") server: String? = null
    ): Response<ApiResponse<ViewPostResponse>>

    @POST("{forumId}/-/{postId}/vote/{vote}")
    suspend fun votePost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("vote") vote: String
    ): Response<ApiResponse<SuccessResponse>>

    @Multipart
    @POST("{forumId}/-/{postId}/edit")
    suspend fun editPost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Part("title") title: RequestBody,
        @Part("body") body: RequestBody,
        @Part("order") order: RequestBody?,
        @Part attachments: List<MultipartBody.Part>
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/delete")
    suspend fun deletePost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Post moderation ----

    @POST("{forumId}/-/{postId}/pin")
    suspend fun pinPost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/unpin")
    suspend fun unpinPost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/lock")
    suspend fun lockPost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/unlock")
    suspend fun unlockPost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/approve")
    suspend fun approvePost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/remove")
    suspend fun removePost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/restore")
    suspend fun restorePost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/{postId}/report")
    suspend fun reportPost(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Field("reason") reason: String,
        @Field("details") details: String,
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Comments ----

    @GET("{forumId}/-/{postId}/comment")
    suspend fun getNewComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Query("parent") parent: String? = null
    ): Response<ApiResponse<Map<String, Any>>>

    @Multipart
    @POST("{forumId}/-/{postId}/create")
    suspend fun createComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Part("forum") forum: RequestBody,
        @Part("post") post: RequestBody,
        @Part("body") body: RequestBody,
        @Part("parent") parent: RequestBody?,
        @Part files: List<MultipartBody.Part>
    ): Response<ApiResponse<CreateCommentResponse>>

    @POST("{forumId}/-/{postId}/{commentId}/vote/{vote}")
    suspend fun voteComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Path("vote") vote: String
    ): Response<ApiResponse<SuccessResponse>>

    @Multipart
    @POST("{forumId}/-/{postId}/{commentId}/edit")
    suspend fun editComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Part("body") body: RequestBody,
        @Part("order") order: RequestBody?,
        @Part files: List<MultipartBody.Part>,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/{commentId}/delete")
    suspend fun deleteComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Comment moderation ----

    @POST("{forumId}/-/{postId}/{commentId}/remove")
    suspend fun removeComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/{commentId}/restore")
    suspend fun restoreComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/{commentId}/approve")
    suspend fun approveComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/{postId}/{commentId}/report")
    suspend fun reportComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Field("reason") reason: String,
        @Field("details") details: String,
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Sort ----

    @FormUrlEncoded
    @POST("{forumId}/-/sort/set")
    suspend fun setForumSort(
        @Path("forumId") forumId: String,
        @Field("sort") sort: String
    ): Response<ApiResponse<SuccessResponse>>

    // Class-level default post sort (applied to the list and to forums with no
    // override). Distinct from the per-forum override above.
    @FormUrlEncoded
    @POST("-/sort/set")
    suspend fun setDefaultSort(
        @Field("sort") sort: String
    ): Response<ApiResponse<SortResponse>>

    // ---- Moderation centre ----

    @GET("{forumId}/-/moderation/queue")
    suspend fun moderationQueue(
        @Path("forumId") forumId: String,
    ): Response<ApiResponse<ModerationQueueResponse>>

    @GET("{forumId}/-/moderation/reports")
    suspend fun moderationReports(
        @Path("forumId") forumId: String,
        @Query("status") status: String = "pending",
    ): Response<ApiResponse<ModerationReportsResponse>>

    @GET("{forumId}/-/moderation/log")
    suspend fun moderationLog(
        @Path("forumId") forumId: String,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ModerationLogResponse>>

    @GET("{forumId}/-/restrictions")
    suspend fun restrictions(
        @Path("forumId") forumId: String,
    ): Response<ApiResponse<RestrictionsResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/restrict")
    suspend fun restrict(
        @Path("forumId") forumId: String,
        @Field("user") user: String,
        @Field("type") type: String,
        @Field("reason") reason: String,
        @Field("duration") duration: Long? = null,
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/unrestrict")
    suspend fun unrestrict(
        @Path("forumId") forumId: String,
        @Field("user") user: String,
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/moderation/reports/{reportId}/resolve")
    suspend fun resolveReport(
        @Path("forumId") forumId: String,
        @Path("reportId") reportId: String,
        @Field("action") resolution: String,
    ): Response<ApiResponse<SuccessResponse>>

    @GET("{forumId}/-/moderation/settings")
    suspend fun moderationSettings(
        @Path("forumId") forumId: String,
    ): Response<ApiResponse<ModerationSettingsResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/moderation/settings/save")
    suspend fun saveModerationSettings(
        @Path("forumId") forumId: String,
        @Field("moderation_posts") moderationPosts: Boolean,
        @Field("moderation_comments") moderationComments: Boolean,
        @Field("moderation_new") moderationNew: Boolean,
        @Field("new_user_days") newUserDays: Int,
        @Field("post_limit") postLimit: Int,
        @Field("comment_limit") commentLimit: Int,
        @Field("limit_window") limitWindow: Int,
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Settings ----

    @GET("{forumId}/-/access")
    suspend fun getAccess(@Path("forumId") forumId: String): Response<ApiResponse<AccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/access/set")
    suspend fun setAccess(
        @Path("forumId") forumId: String,
        @Field("target") target: String,
        @Field("level") level: String,
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/access/revoke")
    suspend fun revokeAccess(
        @Path("forumId") forumId: String,
        @Field("target") target: String,
    ): Response<ApiResponse<SuccessResponse>>

    @GET("{forumId}/-/members")
    suspend fun getMembers(@Path("forumId") forumId: String): Response<ApiResponse<MembersResponse>>

    @GET("{forumId}/-/members/search")
    suspend fun searchMembers(
        @Path("forumId") forumId: String,
        @Query("q") query: String,
    ): Response<ApiResponse<MemberSearchResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/members/save")
    suspend fun saveMembers(
        @Path("forumId") forumId: String,
        @Field("remove") remove: String? = null,
    ): Response<ApiResponse<SuccessResponse>>

    @GET("{forumId}/-/banner/get")
    suspend fun getBanner(@Path("forumId") forumId: String): Response<ApiResponse<BannerResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/banner/set")
    suspend fun setBanner(
        @Path("forumId") forumId: String,
        @Field("banner") banner: String,
    ): Response<ApiResponse<SuccessResponse>>

    @GET("-/accounts/list")
    suspend fun listAccounts(
        @Query("capability") capability: String,
    ): Response<ApiResponse<List<org.mochios.android.model.Account>>>

    @FormUrlEncoded
    @POST("{forumId}/-/ai/settings")
    suspend fun setAiSettings(
        @Path("forumId") forumId: String,
        @Field("mode") mode: String,
        @Field("account") account: String = "",
    ): Response<ApiResponse<SuccessResponse>>

    @GET("{forumId}/-/ai/prompts/get")
    suspend fun getAiPrompts(@Path("forumId") forumId: String): Response<ApiResponse<AiPrompts>>

    @FormUrlEncoded
    @POST("{forumId}/-/ai/prompts/set")
    suspend fun setAiPrompt(
        @Path("forumId") forumId: String,
        @Field("type") type: String,
        @Field("prompt") prompt: String,
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/{postId}/tags/add")
    suspend fun addPostTag(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Field("label") label: String,
    ): Response<ApiResponse<Map<String, Any>>>

    @FormUrlEncoded
    @POST("{forumId}/-/{postId}/tags/remove")
    suspend fun removePostTag(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Field("tag") tagId: String,
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/tags/interest")
    suspend fun adjustTagInterest(
        @Path("forumId") forumId: String,
        @Field("qid") qid: String,
        @Field("direction") direction: String,
    ): Response<ApiResponse<Map<String, Any>>>

    @POST("{forumId}/-/notifications/clear")
    suspend fun clearNotifications(@Path("forumId") forumId: String): Response<ApiResponse<SuccessResponse>>

    // entity scopes the token (a forum id); mode is "posts" or "all" — both
    // required by the server (action_rss_token).
    @GET("-/rss/token")
    suspend fun getRssToken(
        @Query("entity") entity: String,
        @Query("mode") mode: String,
    ): Response<ApiResponse<RssTokenResponse>>

    @GET("{forumId}/-/tags")
    suspend fun getForumTags(@Path("forumId") forumId: String): Response<ApiResponse<ForumTagsResponse>>
}
