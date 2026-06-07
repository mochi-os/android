package org.mochios.projects.api

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.mochios.android.api.ApiResponse
import org.mochios.android.model.AccessRule
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.projects.model.Activity
import org.mochios.projects.model.Branch
import org.mochios.projects.model.DiffResult
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
import org.mochios.projects.model.Watcher
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// Response wrappers
data class ProjectListResponse(val projects: List<Project> = emptyList())
data class ProjectResponse(val project: Project = Project())
data class ProjectInfoResponse(
    val project: Project = Project(),
    val classes: List<ProjectClass> = emptyList(),
    val fields: Map<String, List<ProjectField>> = emptyMap(),
    val options: Map<String, Map<String, List<FieldOption>>> = emptyMap(),
    val views: List<ProjectView> = emptyList(),
    val hierarchy: Map<String, List<String>> = emptyMap()
)
data class TemplateListResponse(val templates: List<Template> = emptyList())
data class ObjectListResponse(val objects: List<ProjectObject> = emptyList())
data class ObjectResponse(val `object`: ProjectObject = ProjectObject())
data class CommentListResponse(val comments: List<Comment> = emptyList())
data class CommentResponse(val comment: Comment = Comment(id = ""))
data class AttachmentListResponse(val attachments: List<Attachment> = emptyList())
data class AttachmentResponse(val attachment: Attachment = Attachment(id = ""))
data class ActivityListResponse(val activities: List<Activity> = emptyList())
data class LinkListResponse(val incoming: List<Link> = emptyList(), val outgoing: List<Link> = emptyList())
data class WatcherListResponse(val watchers: List<Watcher> = emptyList(), val watching: Boolean = false)
data class MergeRequestListResponse(val requests: List<MergeRequest> = emptyList())
data class MergeRequestResponse(val request: MergeRequest = MergeRequest())
data class PeopleResponse(val people: List<Person> = emptyList())
data class AccessResponse(val rules: List<AccessRule> = emptyList())
data class ClassListResponse(val classes: List<ProjectClass> = emptyList())
data class ClassResponse(val `class`: ProjectClass = ProjectClass())
data class FieldListResponse(val fields: List<ProjectField> = emptyList())
data class FieldResponse(val field: ProjectField = ProjectField())
data class OptionListResponse(val options: List<FieldOption> = emptyList())
data class OptionResponse(val option: FieldOption = FieldOption())
data class ViewListResponse(val views: List<ProjectView> = emptyList())
data class ViewResponse(val view: ProjectView = ProjectView())
data class RepositoryListResponse(val repositories: List<Repository> = emptyList())
data class BranchListResponse(val branches: List<Branch> = emptyList())
data class MergeCheckResponse(
    @SerializedName("can_merge") val canMerge: Boolean = false,
    val conflicts: List<String> = emptyList(),
    val base: String = "",
    val ahead: Int = 0,
    val behind: Int = 0
)
data class SuccessResponse(val success: Boolean = false)
data class UserSearchResponse(val users: List<Person> = emptyList())
data class GroupListResponse(val groups: List<Group> = emptyList())
data class HierarchyResponse(val parents: List<String> = emptyList())
data class PreferenceResponse(val preference: String = "")

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
        @Field("privacy") privacy: String,
        @Field("template") template: String?
    ): Response<ApiResponse<ProjectResponse>>

    @GET("-/templates")
    suspend fun getTemplates(): Response<ApiResponse<TemplateListResponse>>

    @GET("-/directory/search")
    suspend fun searchDirectory(@Query("search") query: String): Response<ApiResponse<ProjectListResponse>>

    @GET("-/recommendations")
    suspend fun getRecommendations(): Response<ApiResponse<ProjectListResponse>>

    @FormUrlEncoded
    @POST("-/probe")
    suspend fun probe(@Field("url") url: String): Response<ApiResponse<ProjectResponse>>

    @FormUrlEncoded
    @POST("-/subscribe")
    suspend fun subscribe(
        @Field("project") project: String,
        @Field("server") server: String?
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("-/unsubscribe")
    suspend fun unsubscribe(
        @Field("project") project: String,
        @Field("server") server: String?
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("-/users/search")
    suspend fun searchUsers(@Field("search") query: String): Response<ApiResponse<UserSearchResponse>>

    @GET("-/groups")
    suspend fun getGroups(): Response<ApiResponse<GroupListResponse>>

    @GET("-/repositories")
    suspend fun getRepositories(): Response<ApiResponse<RepositoryListResponse>>

    @GET("-/repositories/{repo}/branches")
    suspend fun getBranches(@Path("repo") repo: String): Response<ApiResponse<BranchListResponse>>

    @FormUrlEncoded
    @POST("-/repositories/{repo}/merge/check")
    suspend fun checkMerge(
        @Path("repo") repo: String,
        @Field("source") source: String,
        @Field("target") target: String
    ): Response<ApiResponse<MergeCheckResponse>>

    @FormUrlEncoded
    @POST("-/repositories/{repo}/diff")
    suspend fun getDiff(
        @Path("repo") repo: String,
        @Field("base") base: String,
        @Field("head") head: String
    ): Response<ApiResponse<String>>

    @FormUrlEncoded
    @POST("-/repositories/{repo}/merge")
    suspend fun merge(
        @Path("repo") repo: String,
        @Field("source") source: String,
        @Field("target") target: String,
        @Field("message") message: String,
        @Field("method") method: String? = null
    ): Response<ApiResponse<SuccessResponse>>

    @GET("-/diff/preference")
    suspend fun getDiffPreference(): Response<ApiResponse<PreferenceResponse>>

    @FormUrlEncoded
    @POST("-/diff/preference/set")
    suspend fun setDiffPreference(@Field("style") preference: String): Response<ApiResponse<SuccessResponse>>

    // ---- Entity-level endpoints ----

    @GET("{projectId}/-/info")
    suspend fun getProjectInfo(@Path("projectId") projectId: String): Response<ApiResponse<ProjectInfoResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/update")
    suspend fun updateProject(
        @Path("projectId") projectId: String,
        @Field("name") name: String?,
        @Field("description") description: String?,
        @Field("prefix") prefix: String?
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

    @FormUrlEncoded
    @POST("{projectId}/-/objects/create")
    suspend fun createObject(
        @Path("projectId") projectId: String,
        @Field("class") classId: String,
        @Field("parent") parent: String?,
        @Field("title") title: String
    ): Response<ApiResponse<ObjectResponse>>

    @GET("{projectId}/-/objects/{objectId}")
    suspend fun getObject(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<ObjectResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/update")
    suspend fun updateObject(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        // Title is a field value (the class's title field), edited via value/set;
        // object/update only handles parent/class. Do NOT add a title field here.
        @Field("parent") parent: String?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/objects/{objectId}/delete")
    suspend fun deleteObject(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/move")
    suspend fun moveObject(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Field("field") field: String?,
        @Field("value") value: String?,
        @Field("rank") rank: Int?,
        @Field("row_field") rowField: String? = null,
        @Field("row_value") rowValue: String? = null,
        @Field("scope_parent") scopeParent: String? = null,
        @Field("promote") promote: String? = null
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/values")
    suspend fun setValues(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @FieldMap values: Map<String, String>
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/objects/{objectId}/values/{fieldId}")
    suspend fun setValue(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String,
        @Path("fieldId") fieldId: String,
        @Field("value") value: String
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
    ): Response<ApiResponse<CommentResponse>>

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
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<AttachmentResponse>>

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
    ): Response<ApiResponse<MergeRequestResponse>>

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

    @GET("{projectId}/-/objects/{objectId}/activity")
    suspend fun getActivity(
        @Path("projectId") projectId: String,
        @Path("objectId") objectId: String
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
        @Field("template_version") templateVersion: Int?
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Views ----

    @GET("{projectId}/-/views")
    suspend fun getViews(@Path("projectId") projectId: String): Response<ApiResponse<ViewListResponse>>

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
    ): Response<ApiResponse<ViewResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/views/reorder")
    suspend fun reorderViews(
        @Path("projectId") projectId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/views/{viewId}/update")
    suspend fun updateView(
        @Path("projectId") projectId: String,
        @Path("viewId") viewId: String,
        @Field("name") name: String?,
        @Field("viewtype") viewtype: String?,
        @Field("columns") columns: String?,
        @Field("rows") rows: String?,
        @Field("filter") filter: String?,
        @Field("sort") sort: String?,
        @Field("direction") direction: String?,
        @Field("classes") classes: String?,
        @Field("border") border: String?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/views/{viewId}/delete")
    suspend fun deleteView(
        @Path("projectId") projectId: String,
        @Path("viewId") viewId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Classes ----

    @GET("{projectId}/-/classes")
    suspend fun getClasses(@Path("projectId") projectId: String): Response<ApiResponse<ClassListResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/create")
    suspend fun createClass(
        @Path("projectId") projectId: String,
        @Field("name") name: String
    ): Response<ApiResponse<ClassResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/update")
    suspend fun updateClass(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Field("name") name: String?,
        @Field("title") title: String? = null,
        @Field("requests") requests: String? = null
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/classes/{classId}/delete")
    suspend fun deleteClass(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Hierarchy ----

    @GET("{projectId}/-/classes/{classId}/hierarchy")
    suspend fun getHierarchy(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String
    ): Response<ApiResponse<HierarchyResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/hierarchy/set")
    suspend fun setHierarchy(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Field("parents") parents: String
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
        @Field("flags") flags: String?,
        @Field("multi") multi: Boolean?
    ): Response<ApiResponse<FieldResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/reorder")
    suspend fun reorderFields(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/update")
    suspend fun updateField(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Field("name") name: String?,
        @Field("fieldtype") fieldtype: String?,
        @Field("flags") flags: String?,
        @Field("multi") multi: Boolean?,
        @Field("card") card: Boolean?,
        @Field("position") position: String?,
        @Field("rows") rows: Int?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/delete")
    suspend fun deleteField(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Options ----

    @GET("{projectId}/-/classes/{classId}/fields/{fieldId}/options")
    suspend fun getOptions(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String
    ): Response<ApiResponse<OptionListResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/options/create")
    suspend fun createOption(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Field("name") name: String,
        @Field("colour") colour: String?,
        @Field("icon") icon: String? = null
    ): Response<ApiResponse<OptionResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/options/reorder")
    suspend fun reorderOptions(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/options/{optionId}/update")
    suspend fun updateOption(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Path("optionId") optionId: String,
        @Field("name") name: String?,
        @Field("colour") colour: String?,
        @Field("icon") icon: String?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{projectId}/-/classes/{classId}/fields/{fieldId}/options/{optionId}/delete")
    suspend fun deleteOption(
        @Path("projectId") projectId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Path("optionId") optionId: String
    ): Response<ApiResponse<SuccessResponse>>
}
