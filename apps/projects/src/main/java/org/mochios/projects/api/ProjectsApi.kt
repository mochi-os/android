// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.api

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.mochios.android.api.ApiResponse
import org.mochios.android.model.AccessRule
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.projects.model.Activity
import org.mochios.projects.model.Branch
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.Group
import org.mochios.projects.model.Link
import org.mochios.projects.model.MergeCheck
import org.mochios.projects.model.MergeRequest
import org.mochios.projects.model.Person
import org.mochios.projects.model.Project
import org.mochios.projects.model.ProjectClass
import org.mochios.projects.model.ProjectField
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.ProjectView
import org.mochios.projects.model.Repository
import org.mochios.projects.model.Template
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

// Response wrappers
data class ProjectListResponse(val projects: List<Project> = emptyList())

// `-/create` answers with the new project's identity flat in `data`:
// {"data": {"fingerprint": "…", "id": "…"}}, the way objects/create does.
// The nested `project` is kept for the wrapped shape other endpoints use, and
// the repository resolves whichever the backend sent.
data class ProjectCreateResponse(

    val id: String = "",

    val fingerprint: String = "",

    val project: Project? = null
)
data class ProjectInfoResponse(
    val project: Project = Project(),
    val classes: List<ProjectClass> = emptyList(),
    val fields: Map<String, List<ProjectField>> = emptyMap(),
    val options: Map<String, Map<String, List<FieldOption>>> = emptyMap(),
    val views: List<ProjectView> = emptyList(),
    val hierarchy: Map<String, List<String>> = emptyMap()
)
data class TemplateListResponse(val templates: List<Template> = emptyList())
data class ObjectListResponse(
    val objects: List<ProjectObject> = emptyList(),
    val watched: List<String> = emptyList()
)
// `-/objects/:object` sends the values beside the object, not inside it.
data class ObjectResponse(
    val `object`: ProjectObject = ProjectObject(),
    val values: Map<String, Any?> = emptyMap(),
)
// objects/create returns a flat body (id/number/readable), not a wrapped object.
data class ObjectCreateResponse(
    val id: String = "",
    val number: Int = 0,
    val readable: String = ""
)
data class CommentListResponse(val comments: List<Comment> = emptyList())
data class AttachmentListResponse(val attachments: List<Attachment> = emptyList())
data class ActivityListResponse(val activities: List<Activity> = emptyList())
data class LinkListResponse(val incoming: List<Link> = emptyList(), val outgoing: List<Link> = emptyList())
// The watchers list beside it is not read: the sheet shows only whether the user watches.
data class WatcherListResponse(val watching: Boolean = false)
data class MergeRequestListResponse(val requests: List<MergeRequest> = emptyList())
data class PeopleResponse(val people: List<Person> = emptyList())
data class AccessResponse(val rules: List<AccessRule> = emptyList())

// JSON bodies, not forms, for every call that may send an empty value: the
// server reads an empty form field as absent but an empty JSON string as "".
// Gson leaves a null out, so a property the user did not touch stays unsent.

// Body of `objects/create`: the object and its first values in one request,
// so a value the server refuses creates nothing.
data class CreateObjectRequest(
    @SerializedName("class") val classId: String,
    val parent: String?,
    val title: String,
    val values: Map<String, String>?,
)

// Body of `objects/{object}/update`; "" for [parent] moves it to the top level.
data class UpdateObjectRequest(val parent: String?)

// Body of `objects/{object}/move`: `after` names the object it was dropped
// directly after, or `before` the one directly before when none was above it;
// with neither, a move to another column goes to its end. `parent` moves it
// into that object's children in the same move. An empty `value` keeps the
// column, and `clear` "true" empties it.
data class MoveObjectRequest(
    val field: String?,
    val value: String?,
    val after: String?,
    val before: String?,
    val row: String?,
    val parent: String?,
    val clear: String? = null,
)

// Body of `classes/{class}/hierarchy/set`: the parents comma-separated, as the
// web sends them, "" for the top level only and "_none_" for none.
data class HierarchyRequest(val parents: String)

// Body of `views/{view}/update`; "" clears a setting.
data class UpdateViewRequest(
    val name: String?,
    val viewtype: String?,
    val columns: String?,
    val rows: String?,
    val filter: String?,
    val sort: String?,
    val direction: String?,
    val classes: String?,
    val border: String?,
)

// Body of `fields/{field}/update`; "" clears flags, position, pattern or rows.
data class UpdateFieldRequest(
    val name: String?,
    val flags: String?,
    val card: Boolean?,
    val position: String?,
    val rows: String?,
    val pattern: String?,
    val minlength: Int?,
    val maxlength: Int?,
)

// Body of `-/update`; "" clears the description.
data class UpdateProjectRequest(val name: String?, val description: String?, val prefix: String?)

// Body of `options/{option}/update`; "" clears the colour or icon.
data class UpdateOptionRequest(val name: String?, val colour: String?, val icon: String?)

// Body for setting a single field value. The values endpoint expects a JSON
// object ({"value": "…"}); form encoding fails to update some fields (e.g. the
// due date).
data class SetValueRequest(val value: String)

// JSON body of `-/subscribe`. `server` is the home-server hint from the
// directory hit and `peer` the pin from a `mochi://` share link; each is
// omitted from the payload when null.
data class SubscribeRequest(val project: String, val server: String? = null, val peer: String? = null)

/**
 * The project `-/subscribe` joined. Route by [fingerprint]: the directory hit
 * may have carried a full id.
 */
data class SubscribeResponse(

    val fingerprint: String = "",

    val id: String = ""
)

// JSON body of `-/unsubscribe`. `server` is accepted for symmetry with
// subscribe but ignored server-side — unsubscribe resolves the project locally.
// The server unsubscribes through the server it stored for the project.
data class UnsubscribeRequest(val project: String)

// JSON body of `classes/{class}/update`; a null field is left out, an empty
// `title` clears the class's title field.
data class UpdateClassRequest(val name: String? = null, val title: String? = null, val requests: String? = null)

// Shareable project link returned by the `-/share` endpoint.
data class ShareResponse(val link: String = "")

data class WarmExportResponse(val attachments: Int = 0, val remaining: Int = 0)
data class FieldListResponse(val fields: List<ProjectField> = emptyList())
data class RepositoryListResponse(val repositories: List<Repository> = emptyList())
data class BranchListResponse(val branches: List<Branch> = emptyList())
data class SuccessResponse(val success: Boolean = false)

// `objects/{object}/move`: every key the move wrote, the moved object's and
// any a neighbour that shared its anchor's took.
data class MoveResponse(val success: Boolean = false, val ranks: List<MovedRank>? = null)

// One key a move wrote; [updated] is the moved object's new time, on its row only.
data class MovedRank(val id: String = "", val rank: String = "", val updated: Long? = null)

// `-/repositories/{repository}/merge`: [recorded] when the server also marked
// the merge request it was given merged; absent from a server that does not
// take one, or when recording it failed after the merge.
data class MergeResponse(val success: Boolean = false, val recorded: Boolean? = null)
data class UserSearchResponse(val results: List<Person> = emptyList())
data class GroupListResponse(val groups: List<Group> = emptyList())
data class PreferenceResponse(@SerializedName("style") val preference: String = "unified")

// `-/recommendations`: a project the directory recommends, with its [blurb]
// where a project otherwise carries a description.
data class Recommendation(
    val id: String = "",
    val name: String = "",
    val blurb: String = "",
    val fingerprint: String = "",
    val server: String? = null,
)
data class RecommendationListResponse(val projects: List<Recommendation> = emptyList())

interface ProjectsApi {

    // ---- Class-level endpoints ----

    @GET("-/list")
    suspend fun listProjects(): Response<ApiResponse<ProjectListResponse>>

    @FormUrlEncoded
    @POST("-/create")
    suspend fun createProject(
        @Field("name") name: String,
        @Field("description") description: String?,
        @Field("prefix") prefix: String?,
        // Whether the directory lists the project, and nothing else.
        @Field("privacy") privacy: String,
        // "true" lets anyone view it; "false" keeps it for its members.
        @Field("readable") readable: String,
        @Field("template") template: String?
    ): Response<ApiResponse<ProjectCreateResponse>>

    @GET("-/templates")
    suspend fun getTemplates(): Response<ApiResponse<TemplateListResponse>>

    @GET("-/directory/search")
    suspend fun searchDirectory(@Query("search") query: String): Response<ApiResponse<List<Project>>>

    @GET("-/recommendations")
    suspend fun getRecommendations(): Response<ApiResponse<RecommendationListResponse>>

    @FormUrlEncoded
    @POST("-/probe")
    // The probed project comes flat in `data`, with `peer` (a share link) or
    // `server` (a web address) beside its fields.
    suspend fun probe(@Field("url") url: String): Response<ApiResponse<Project>>

    @POST("-/subscribe")
    suspend fun subscribe(
        @Body body: SubscribeRequest
    ): Response<ApiResponse<SubscribeResponse>>

    @POST("-/unsubscribe")
    suspend fun unsubscribe(
        @Body body: UnsubscribeRequest
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("-/users/search")
    suspend fun searchUsers(@Field("search") query: String): Response<ApiResponse<UserSearchResponse>>

    @GET("-/groups")
    suspend fun getGroups(): Response<ApiResponse<GroupListResponse>>

    @GET("-/repositories")
    suspend fun getRepositories(): Response<ApiResponse<RepositoryListResponse>>

    @GET("-/repositories/{repository}/branches")
    suspend fun getBranches(@Path("repository") repository: String): Response<ApiResponse<BranchListResponse>>

    @FormUrlEncoded
    @POST("-/repositories/{repository}/merge/check")
    suspend fun checkMerge(
        @Path("repository") repository: String,
        @Field("source") source: String,
        @Field("target") target: String
    ): Response<ApiResponse<MergeCheck>>

    @FormUrlEncoded
    @POST("-/repositories/{repository}/diff")
    suspend fun getDiff(
        @Path("repository") repository: String,
        @Field("base") base: String,
        @Field("head") head: String
    ): Response<ApiResponse<String>>

    // Class-level, so the project is named in the form: the server resolves
    // it before it decides whether the merge runs locally or at the owner.
    // [request] is the merge request the merge completes, which the server
    // then records merged in the same call.
    @FormUrlEncoded
    @POST("-/repositories/{repository}/merge")
    suspend fun merge(
        @Path("repository") repository: String,
        @Field("project") project: String, // contract-ok: read via resolve_project(a) helper
        @Field("source") source: String,
        @Field("target") target: String,
        @Field("message") message: String,
        @Field("method") method: String? = null,
        @Field("request") request: String? = null
    ): Response<ApiResponse<MergeResponse>>

    @GET("-/diff/preference")
    suspend fun getDiffPreference(): Response<ApiResponse<PreferenceResponse>>

    @FormUrlEncoded
    @POST("-/diff/preference/set")
    suspend fun setDiffPreference(@Field("style") preference: String): Response<ApiResponse<SuccessResponse>>

    // ---- Entity-level endpoints ----

    @GET("{projectId}/-/information")
    suspend fun getProjectInfo(@Path("projectId") projectId: String): Response<ApiResponse<ProjectInfoResponse>>

    @POST("{projectId}/-/share")
    suspend fun getShareLink(@Path("projectId") projectId: String): Response<ApiResponse<ShareResponse>>

    @POST("{projectId}/-/update")
    suspend fun updateProject(
        @Path("projectId") projectId: String,
        @Body body: UpdateProjectRequest
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/delete")
    suspend fun deleteProject(@Path("projectId") projectId: String): Response<ApiResponse<SuccessResponse>>

    @GET("{projectId}/-/people")
    suspend fun getPeople(@Path("projectId") projectId: String): Response<ApiResponse<PeopleResponse>>

    @GET("{projectId}/-/access")
    suspend fun getAccess(@Path("projectId") projectId: String): Response<ApiResponse<AccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/access/set")
    suspend fun setAccess(
        @Path("projectId") projectId: String,
        @Field("subject") subject: String,
        @Field("level") level: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/access/revoke")
    suspend fun revokeAccess(
        @Path("projectId") projectId: String,
        @Field("subject") subject: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Objects ----

    @GET("{projectId}/-/objects")
    suspend fun getObjects(@Path("projectId") projectId: String): Response<ApiResponse<ObjectListResponse>>

    @POST("{projectId}/-/objects/create")
    suspend fun createObject(
        @Path("projectId") projectId: String,
        @Body body: CreateObjectRequest
    ): Response<ApiResponse<ObjectCreateResponse>>

    @GET("{projectId}/-/objects/{objectId}")
    suspend fun getObject(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<ObjectResponse>>

    // Title is a field value (the class's title field), edited via value/set;
    // object/update only handles parent/class. Do NOT add a title field here.
    @POST("{projectId}/-/objects/{objectId}/update")
    suspend fun updateObject(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Body body: UpdateObjectRequest
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/objects/{objectId}/delete")
    suspend fun deleteObject(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/objects/{objectId}/move")
    suspend fun moveObject(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Body body: MoveObjectRequest
    ): Response<ApiResponse<MoveResponse>>

    @POST("{projectId}/-/objects/{objectId}/values/{fieldId}")
    suspend fun setValue(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Path("fieldId") fieldId: String,
        @Body body: SetValueRequest
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Links ----

    @GET("{projectId}/-/objects/{objectId}/links")
    suspend fun getLinks(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<LinkListResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/links/create")
    suspend fun createLink(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Field("target") target: String,
        @Field("linktype") linktype: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/links/delete")
    suspend fun deleteLink(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Field("target") target: String,
        @Field("linktype") linktype: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Comments ----

    @GET("{projectId}/-/objects/{objectId}/comments")
    suspend fun getComments(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<CommentListResponse>>

    @Multipart
    @POST("{projectId}/-/objects/{objectId}/comments/create")
    suspend fun createComment(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Part("content") content: RequestBody,
        @Part("parent") parent: RequestBody?,
        @Part files: List<MultipartBody.Part>
    ): Response<ApiResponse<Comment>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/comments/{commentId}/update")
    suspend fun updateComment(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Path("commentId") commentId: String,
        @Field("content") content: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/objects/{objectId}/comments/{commentId}/delete")
    suspend fun deleteComment(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Path("commentId") commentId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Attachments ----

    @GET("{projectId}/-/objects/{objectId}/attachments")
    suspend fun getAttachments(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<AttachmentListResponse>>

    @Multipart
    @POST("{projectId}/-/objects/{objectId}/attachments/create")
    suspend fun createAttachment(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Part files: List<MultipartBody.Part>
    ): Response<ApiResponse<AttachmentListResponse>>

    /** Copies attachments of other objects in the project onto this one; [attachments] comma-separated. */
    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/attachments/copy")
    suspend fun copyAttachments(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Field("attachments") attachments: String
    ): Response<ApiResponse<AttachmentListResponse>>

    @POST("{projectId}/-/objects/{objectId}/attachments/{attachmentId}/delete")
    suspend fun deleteAttachment(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Path("attachmentId") attachmentId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Merge Requests ----

    @GET("{projectId}/-/objects/{objectId}/requests")
    suspend fun getRequests(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<MergeRequestListResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/requests/create")
    suspend fun createRequest(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Field("type") type: String?,
        @Field("repository") repository: String,
        @Field("source") source: String,
        @Field("target") target: String,
        @Field("title") title: String,
        @Field("description") description: String?,
        @Field("draft") draft: String?
    ): Response<ApiResponse<MergeRequest>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/requests/{requestId}/update")
    suspend fun updateRequest(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Path("requestId") requestId: String,
        @Field("title") title: String?,
        @Field("description") description: String?,
        @Field("status") status: String?,
        @Field("draft") draft: String?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/objects/{objectId}/requests/{requestId}/delete")
    suspend fun deleteRequest(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Path("requestId") requestId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Activity ----

    // Newest first, a page of [limit] entries from [offset].
    @GET("{projectId}/-/objects/{objectId}/activity")
    suspend fun getActivity(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): Response<ApiResponse<ActivityListResponse>>

    // ---- Watchers ----

    @GET("{projectId}/-/objects/{objectId}/watchers")
    suspend fun getWatchers(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<WatcherListResponse>>

    @POST("{projectId}/-/objects/{objectId}/watchers/add")
    suspend fun addWatcher(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/objects/{objectId}/watchers/remove")
    suspend fun removeWatcher(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Design: Export / Import ----

    @GET("{projectId}/-/design/export")
    suspend fun exportDesign(@Path("projectId") projectId: String): Response<ApiResponse<JsonObject>>

    @FormUrlEncoded
    @POST("{projectId}/-/design/import")
    suspend fun importDesign(
        @Path("projectId") projectId: String,
        @Field("data") data: String?,
        @Field("template") template: String?,
        @Field("version") templateVersion: Int?
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Data: Export / Import ----

    @POST("{projectId}/-/data/export/warm")
    suspend fun warmExport(
        @Path("projectId") projectId: String
    ): Response<ApiResponse<WarmExportResponse>>

    // Answers with the zip itself, not a JSON envelope, so this is the one
    // endpoint here that isn't an ApiResponse. @Streaming keeps the body off
    // the heap — a warmed export carries every attachment.
    @Streaming
    @GET("{projectId}/-/data/export")
    suspend fun exportData(
        @Path("projectId") projectId: String
    ): Response<ResponseBody>

    // `design` asks the server to apply the design the file carries before
    // its objects, so a whole backup restores in one upload.
    @Multipart
    @POST("{projectId}/-/data/import")
    suspend fun importData(
        @Path("projectId") projectId: String,
        @Part file: MultipartBody.Part,
        @Part("design") design: RequestBody? = null
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Views ----

    @FormUrlEncoded
    @POST("{projectId}/-/views/create")
    suspend fun createView(
        @Path("projectId") projectId: String,
        @Field("name") name: String,
        @Field("viewtype") viewtype: String,
        @Field("columns") columns: String?,
        @Field("rows") rows: String?,
        @Field("filter") filter: String?,
        @Field("sort") sort: String?,
        @Field("direction") direction: String?,
        @Field("classes") classes: String?,
        @Field("border") border: String?
    ): Response<ApiResponse<ProjectView>>

    @FormUrlEncoded
    @POST("{projectId}/-/views/reorder")
    suspend fun reorderViews(
        @Path("projectId") projectId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/views/{viewId}/update")
    suspend fun updateView(
        @Path("projectId") projectId: String,
        @Path("viewId") viewId: String,
        @Body body: UpdateViewRequest
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/views/{viewId}/delete")
    suspend fun deleteView(
        @Path("projectId") projectId: String,
        @Path("viewId") viewId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Classes ----

    @FormUrlEncoded
    @POST("{projectId}/-/classes/create")
    suspend fun createClass(
        @Path("projectId") projectId: String,
        @Field("name") name: String
    ): Response<ApiResponse<ProjectClass>>

    // JSON, not a form: a form field sent empty reads as absent on the server,
    // and clearing the title field back to the default needs "" to arrive.
    @POST("{projectId}/-/classes/{classId}/update")
    suspend fun updateClass(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Body body: UpdateClassRequest
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/classes/{classId}/delete")
    suspend fun deleteClass(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Hierarchy ----

    @POST("{projectId}/-/classes/{classId}/hierarchy/set")
    suspend fun setHierarchy(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Body body: HierarchyRequest
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Fields ----

    @GET("{projectId}/-/classes/{classId}/fields")
    suspend fun getFields(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String
    ): Response<ApiResponse<FieldListResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/create")
    suspend fun createField(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Field("name") name: String,
        @Field("fieldtype") fieldtype: String,
        @Field("flags") flags: String?
    ): Response<ApiResponse<ProjectField>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/reorder")
    suspend fun reorderFields(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/update")
    suspend fun updateField(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Body body: UpdateFieldRequest
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/delete")
    suspend fun deleteField(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Options ----

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/options/create")
    suspend fun createOption(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Field("name") name: String,
        @Field("colour") colour: String?,
        @Field("icon") icon: String? = null
    ): Response<ApiResponse<FieldOption>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/options/reorder")
    suspend fun reorderOptions(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/options/{optionId}/update")
    suspend fun updateOption(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Path("optionId") optionId: String,
        @Body body: UpdateOptionRequest
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/options/{optionId}/delete")
    suspend fun deleteOption(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Path("optionId") optionId: String
    ): Response<ApiResponse<SuccessResponse>>
}
