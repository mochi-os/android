package org.mochios.forums.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.mochios.android.api.ApiResponse
import org.mochios.forums.model.DirectoryEntry
import org.mochios.forums.model.Forum
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.Member
import org.mochios.forums.model.Post
import org.mochios.forums.model.RecommendedForum
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

data class FindForumsResponse(val forums: List<DirectoryEntry> = emptyList())

data class SearchForumsResponse(val results: List<DirectoryEntry> = emptyList())

data class RecommendationsResponse(val forums: List<RecommendedForum> = emptyList())

data class ProbeResponse(
    val id: String = "",
    val name: String = "",
    val fingerprint: String = "",
    @com.google.gson.annotations.SerializedName("class") val klass: String = "",
    val server: String = ""
)

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

interface ForumsApi {

    // ---- Forum list / discovery ----

    @GET("-/list")
    suspend fun listForums(@Query("sort") sort: String? = null): Response<ApiResponse<ForumListResponse>>

    @GET("-/info")
    suspend fun getForumsInfo(): Response<ApiResponse<ForumListResponse>>

    @GET("-/new")
    suspend fun getNewForum(): Response<ApiResponse<Map<String, Any>>>

    @FormUrlEncoded
    @POST("-/create")
    suspend fun createForum(
        @Field("name") name: String,
        @Field("privacy") privacy: String? = null
    ): Response<ApiResponse<CreateForumResponse>>

    @GET("find")
    suspend fun findForums(): Response<ApiResponse<FindForumsResponse>>

    @GET("-/directory/search")
    suspend fun searchForums(@Query("search") search: String): Response<ApiResponse<SearchForumsResponse>>

    @GET("-/recommendations")
    suspend fun getRecommendations(): Response<ApiResponse<RecommendationsResponse>>

    @FormUrlEncoded
    @POST("-/probe")
    suspend fun probeForum(@Field("url") url: String): Response<ApiResponse<ProbeResponse>>

    // ---- Forum entity ----

    @GET("{forumId}/-/info")
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

    @FormUrlEncoded
    @POST("{forumId}/-/{postId}/{commentId}/edit")
    suspend fun editComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Field("body") body: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{forumId}/-/{postId}/{commentId}/delete")
    suspend fun deleteComment(
        @Path("forumId") forumId: String,
        @Path("postId") postId: String,
        @Path("commentId") commentId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Sort ----

    @FormUrlEncoded
    @POST("-/sort/set")
    suspend fun setDefaultSort(@Field("sort") sort: String): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{forumId}/-/sort/set")
    suspend fun setForumSort(
        @Path("forumId") forumId: String,
        @Field("sort") sort: String
    ): Response<ApiResponse<SuccessResponse>>
}
