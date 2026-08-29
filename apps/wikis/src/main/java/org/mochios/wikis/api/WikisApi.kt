// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.mochios.android.api.ApiResponse
import org.mochios.wikis.model.ProbeResponse
import org.mochios.wikis.model.AccessListResponse
import org.mochios.wikis.model.AttachmentDeleteResponse
import org.mochios.wikis.model.AttachmentUpdateResponse
import org.mochios.wikis.model.AttachmentUploadResponse
import org.mochios.wikis.model.AttachmentsResponse
import org.mochios.wikis.model.ChangesResponse
import org.mochios.wikis.model.CommentCreateResponse
import org.mochios.wikis.model.CommentDeleteResponse
import org.mochios.wikis.model.CommentEditResponse
import org.mochios.wikis.model.CommentsResponse
import org.mochios.wikis.model.CreateWikiResponse
import org.mochios.wikis.model.DirectorySearchResponse
import org.mochios.wikis.model.GroupsResponse
import org.mochios.wikis.model.JoinWikiResponse
import org.mochios.wikis.model.NewPageResponse
import org.mochios.wikis.model.OkResponse
import org.mochios.wikis.model.PageDeleteResponse
import org.mochios.wikis.model.PageEditResponse
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.PageHistoryResponse
import org.mochios.wikis.model.PageRenameResponse
import org.mochios.wikis.model.PageRevertResponse
import org.mochios.wikis.model.PageRevisionResponse
import org.mochios.wikis.model.RecommendationsResponse
import org.mochios.wikis.model.RedirectDeleteResponse
import org.mochios.wikis.model.RedirectSetResponse
import org.mochios.wikis.model.RedirectsResponse
import org.mochios.wikis.model.ReplicasResponse
import org.mochios.wikis.model.RssTokenResponse
import org.mochios.wikis.model.SearchResponse
import org.mochios.wikis.model.SettingsResponse
import org.mochios.wikis.model.SettingsSetResponse
import org.mochios.wikis.model.ShareResponse
import org.mochios.wikis.model.SubscribeRequest
import org.mochios.wikis.model.TagAddResponse
import org.mochios.wikis.model.TagPagesResponse
import org.mochios.wikis.model.TagRemoveResponse
import org.mochios.wikis.model.TagsResponse
import org.mochios.wikis.model.UsersSearchResponse
import org.mochios.wikis.model.WikiInfoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the wikis app, bound to a per-app Retrofit with
 * baseUrl `<server>/wikis/`, so paths are relative: `-/...` is class-level,
 * `{wiki}/-/...` is entity-scoped. Path segments use `encoded = true` so
 * pre-encoded slugs are not double-encoded.
 */
interface WikisApi {

    // ---- Class-level info / discovery ----

    /** Top-level wiki list (no wiki context). */
    
    @GET("-/info")
    suspend fun getClassInfo(): Response<ApiResponse<WikiInfoResponse>>

    /** Per-wiki info, including permissions and sidebar list. */
    
    @GET("{wiki}/-/info")
    suspend fun getInfo(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<WikiInfoResponse>>

    
    @FormUrlEncoded
    @POST("-/create")
    suspend fun createWiki(
        @Field("name") name: String,
        @Field("privacy") privacy: String,
    ): Response<ApiResponse<CreateWikiResponse>>


    /** Resolve a pasted mochi:// share link to the wiki it names. */
    @FormUrlEncoded
    @POST("-/probe")
    suspend fun probeUrl(
        @Field("url") url: String,
    ): Response<ApiResponse<ProbeResponse>>

    @POST("-/subscribe")
    suspend fun joinWiki(
        @Body body: SubscribeRequest,
    ): Response<ApiResponse<JoinWikiResponse>>

    
    @POST("{wiki}/-/unsubscribe")
    suspend fun unsubscribe(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<OkResponse>>

    /** The wiki's shareable `mochi://` link, assembled by the server. */
    @POST("{wiki}/-/share")
    suspend fun share(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<ShareResponse>>

    
    @POST("{wiki}/-/sync")
    suspend fun sync(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<OkResponse>>

    
    @GET("-/directory/search")
    suspend fun searchDirectory(
        @Query("search") search: String,
    ): Response<ApiResponse<DirectorySearchResponse>>

    
    @GET("-/recommendations")
    suspend fun getRecommendations(): Response<ApiResponse<RecommendationsResponse>>

    // ---- Pages ----

    
    @GET("{wiki}/-/pages/{page}")
    suspend fun getPage(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
    ): Response<ApiResponse<PageFetchResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/pages/{page}/edit")
    suspend fun editPage(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Field("title") title: String,
        @Field("content") content: String,
        @Field("comment") comment: String?,
    ): Response<ApiResponse<PageEditResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/page/create")
    suspend fun createPage(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("slug") slug: String,
        @Field("title") title: String,
        @Field("content") content: String,
    ): Response<ApiResponse<NewPageResponse>>

    
    @GET("{wiki}/-/pages/{page}/history")
    suspend fun getPageHistory(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Query("limit") limit: Int, // contract-ok: read via pagination(a) helper
        @Query("offset") offset: Int, // contract-ok: read via pagination(a) helper
    ): Response<ApiResponse<PageHistoryResponse>>

    
    @GET("{wiki}/-/pages/{page}/history/{version}")
    suspend fun getPageRevision(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Path("version") version: Int,
    ): Response<ApiResponse<PageRevisionResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/pages/{page}/revert")
    suspend fun revertPage(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Field("version") version: Int,
        @Field("comment") comment: String?,
    ): Response<ApiResponse<PageRevertResponse>>

    
    @POST("{wiki}/-/pages/{page}/delete")
    suspend fun deletePage(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
    ): Response<ApiResponse<PageDeleteResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/pages/{page}/rename")
    suspend fun renamePage(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Field("slug") slug: String,
        @Field("redirects") redirects: String?,
    ): Response<ApiResponse<PageRenameResponse>>

    // ---- Tags ----

    
    @FormUrlEncoded
    @POST("{wiki}/-/pages/{page}/tag/add")
    suspend fun addTag(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Field("tag") tag: String,
    ): Response<ApiResponse<TagAddResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/pages/{page}/tag/remove")
    suspend fun removeTag(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Field("tag") tag: String,
    ): Response<ApiResponse<TagRemoveResponse>>

    
    @GET("{wiki}/-/tags")
    suspend fun listTags(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<TagsResponse>>

    
    @GET("{wiki}/-/tag/{tag}")
    suspend fun listTagPages(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "tag", encoded = true) tag: String,
    ): Response<ApiResponse<TagPagesResponse>>

    // ---- Recent changes ----

    
    @GET("{wiki}/-/changes")
    suspend fun getChanges(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Query("limit") limit: Int, // contract-ok: read via pagination(a) helper
        @Query("offset") offset: Int, // contract-ok: read via pagination(a) helper
    ): Response<ApiResponse<ChangesResponse>>

    // ---- Redirects ----

    
    @GET("{wiki}/-/redirects")
    suspend fun listRedirects(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<RedirectsResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/redirect/set")
    suspend fun setRedirect(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("source") source: String,
        @Field("target") target: String,
    ): Response<ApiResponse<RedirectSetResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/redirect/delete")
    suspend fun deleteRedirect(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("source") source: String,
    ): Response<ApiResponse<RedirectDeleteResponse>>

    // ---- Settings ----

    
    @GET("{wiki}/-/settings")
    suspend fun getSettings(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<SettingsResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/settings/set")
    suspend fun setSetting(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("name") name: String,
        @Field("value") value: String,
    ): Response<ApiResponse<SettingsSetResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/rename")
    suspend fun renameWiki(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("name") name: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Replicas ----

    
    @GET("{wiki}/-/replicas")
    suspend fun listReplicas(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<ReplicasResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/replica/remove")
    suspend fun removeReplica(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("replica") replica: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Access control ----

    
    @GET("{wiki}/-/access")
    suspend fun listAccess(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<AccessListResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/access/set")
    suspend fun setAccess(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("subject") subject: String,
        @Field("level") level: String,
    ): Response<ApiResponse<OkResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/access/revoke")
    suspend fun revokeAccess(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("subject") subject: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Cross-app proxies (users/groups) ----

    /** Proxy to people.users/search via the wikis backend (class-level). */
    @GET("-/users/search")
    suspend fun searchUsers(
        @Query("search") query: String,
    ): Response<ApiResponse<UsersSearchResponse>>

    // Proxy to people.groups/list via the wikis backend (class-level).
    // action_groups takes no wiki: it calls people directly, and app.json
    // registers only `-/groups`. The entity-scoped form never resolved.
    @GET("-/groups")
    suspend fun listGroups(): Response<ApiResponse<GroupsResponse>>

    // ---- Wiki delete ----

    
    @POST("{wiki}/-/delete")
    suspend fun deleteWiki(
        @Path(value = "wiki", encoded = true) wiki: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Page comments ----

    
    @GET("{wiki}/-/pages/{page}/comments")
    suspend fun getPageComments(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
    ): Response<ApiResponse<CommentsResponse>>

    
    @Multipart
    @POST("{wiki}/-/pages/{page}/comment/create")
    suspend fun createComment(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Part("body") body: RequestBody,
        @Part("parent") parent: RequestBody?,
        @Part files: List<MultipartBody.Part>,
    ): Response<ApiResponse<CommentCreateResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/pages/{page}/comment/edit")
    suspend fun editComment(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Field("id") id: String,
        @Field("body") body: String,
    ): Response<ApiResponse<CommentEditResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/pages/{page}/comment/delete")
    suspend fun deleteComment(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Path(value = "page", encoded = true) page: String,
        @Field("id") id: String,
    ): Response<ApiResponse<CommentDeleteResponse>>

    // ---- Attachments ----

    
    @GET("{wiki}/-/attachment/list")
    suspend fun listAttachments(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Query("page") page: String, // contract-ok: no server-side pagination for attachments; page ignored
    ): Response<ApiResponse<AttachmentsResponse>>

    
    @Multipart
    @POST("{wiki}/-/attachment/upload")
    suspend fun uploadAttachments(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Part files: List<MultipartBody.Part>,
    ): Response<ApiResponse<AttachmentUploadResponse>>

    
    @FormUrlEncoded
    @POST("{wiki}/-/attachment/update")
    suspend fun updateAttachment(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("id") id: String,
        @Field("caption") caption: String,
    ): Response<ApiResponse<AttachmentUpdateResponse>>

    @FormUrlEncoded
    @POST("{wiki}/-/attachment/delete")
    suspend fun deleteAttachment(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Field("id") id: String,
    ): Response<ApiResponse<AttachmentDeleteResponse>>

    // ---- RSS tokens ----

    /**
     * RSS token. Class-level: the `entity` field scopes it - `*` for the
     * all-wikis feed, else a wiki id or fingerprint.
     */
    @FormUrlEncoded
    @POST("-/rss/token")
    suspend fun createRssToken(
        @Field("entity") entity: String,
        @Field("mode") mode: String,
    ): Response<ApiResponse<RssTokenResponse>>

    /**
     * Invalidate the RSS tokens minted for [entity], so feed URLs already
     * handed out stop resolving. Scoped the way [createRssToken] is - `*` for
     * the all-wikis feed, else a wiki id or fingerprint.
     */
    @FormUrlEncoded
    @POST("-/rss/token/revoke")
    suspend fun revokeRssToken(
        @Field("entity") entity: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Search ----

    
    @GET("{wiki}/-/search")
    suspend fun search(
        @Path(value = "wiki", encoded = true) wiki: String,
        @Query("q") query: String,
    ): Response<ApiResponse<SearchResponse>>
}
