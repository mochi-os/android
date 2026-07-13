// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.api

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.mochios.android.api.ApiResponse
import org.mochios.android.model.AccessRule
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.crm.model.Activity
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.Group
import org.mochios.crm.model.Link
import org.mochios.crm.model.Person
import org.mochios.crm.model.Crm
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.Template
import org.mochios.crm.model.Watcher
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
data class CrmListResponse(@SerializedName("crms") val crms: List<Crm> = emptyList())
data class CrmResponse(val crm: Crm = Crm())
data class CrmInfoResponse(
    val crm: Crm = Crm(),
    val classes: List<CrmClass> = emptyList(),
    val fields: Map<String, List<CrmField>> = emptyMap(),
    val options: Map<String, Map<String, List<FieldOption>>> = emptyMap(),
    val views: List<CrmView> = emptyList(),
    val hierarchy: Map<String, List<String>> = emptyMap()
)
data class TemplateListResponse(val templates: List<Template> = emptyList())
data class ObjectListResponse(val objects: List<CrmObject> = emptyList())
data class ObjectResponse(val `object`: CrmObject = CrmObject())
data class CommentListResponse(val comments: List<Comment> = emptyList())
data class CommentResponse(val comment: Comment = Comment(id = ""))
data class AttachmentListResponse(val attachments: List<Attachment> = emptyList())
data class AttachmentResponse(val attachment: Attachment = Attachment(id = ""))
data class ActivityListResponse(val activities: List<Activity> = emptyList())
data class LinkListResponse(val incoming: List<Link> = emptyList(), val outgoing: List<Link> = emptyList())
data class WatcherListResponse(val watchers: List<Watcher> = emptyList(), val watching: Boolean = false)
data class PeopleResponse(val people: List<Person> = emptyList())
data class AccessResponse(val rules: List<AccessRule> = emptyList())
data class ClassListResponse(val classes: List<CrmClass> = emptyList())
data class ClassResponse(val `class`: CrmClass = CrmClass())
data class FieldListResponse(val fields: List<CrmField> = emptyList())
data class FieldResponse(val field: CrmField = CrmField())
data class OptionListResponse(val options: List<FieldOption> = emptyList())
data class OptionResponse(val option: FieldOption = FieldOption())
data class ViewListResponse(val views: List<CrmView> = emptyList())
data class ViewResponse(val view: CrmView = CrmView())
data class SuccessResponse(val success: Boolean = false)
data class UserSearchResponse(@SerializedName("results") val results: List<Person> = emptyList())
data class GroupListResponse(val groups: List<Group> = emptyList())
data class HierarchyResponse(val parents: List<String> = emptyList())
data class PreferenceResponse(val preference: String = "")

interface CrmsApi {

    // ---- Class-level endpoints ----

    @GET("-/list")
    suspend fun listCrms(): Response<ApiResponse<CrmListResponse>>

    @FormUrlEncoded
    @POST("-/create")
    suspend fun createCrm(
        @Field("name") name: String,
        @Field("description") description: String?,
        @Field("prefix") prefix: String?, // contract-ok: crm create ignores prefix server-side (no prefix on create)
        @Field("privacy") privacy: String,
        @Field("template") template: String? // contract-ok: templates applied via design-import, not create
    ): Response<ApiResponse<CrmResponse>>

    @GET("-/templates")
    suspend fun getTemplates(): Response<ApiResponse<TemplateListResponse>>

    // directory/search returns a bare array in `data`, not a {crms:[...]} object.
    @GET("-/directory/search")
    suspend fun searchDirectory(@Query("search") query: String): Response<ApiResponse<List<Crm>>>

    @GET("-/recommendations")
    suspend fun getRecommendations(): Response<ApiResponse<CrmListResponse>>

    @FormUrlEncoded
    @POST("-/probe")
    suspend fun probe(@Field("url") url: String): Response<ApiResponse<CrmResponse>>

    @FormUrlEncoded
    @POST("-/subscribe")
    suspend fun subscribe(
        @Field("crm") crm: String,
        @Field("server") server: String?
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("-/unsubscribe")
    suspend fun unsubscribe(
        @Field("crm") crm: String,
        @Field("server") server: String? // contract-ok: unsubscribe resolves locally; server hint ignored
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("-/users/search")
    suspend fun searchUsers(@Field("search") query: String): Response<ApiResponse<UserSearchResponse>>

    @GET("-/groups")
    suspend fun getGroups(): Response<ApiResponse<GroupListResponse>>

    // ---- Entity-level endpoints ----

    @GET("{crmId}/-/info")
    suspend fun getCrmInfo(@Path("crmId") crmId: String): Response<ApiResponse<CrmInfoResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/update")
    suspend fun updateCrm(
        @Path("crmId") crmId: String,
        @Field("name") name: String?,
        @Field("description") description: String?,
        @Field("prefix") prefix: String? // contract-ok: crm update reads name/description only; prefix not server-supported
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{crmId}/-/delete")
    suspend fun deleteCrm(@Path("crmId") crmId: String): Response<ApiResponse<SuccessResponse>>

    @GET("{crmId}/-/people")
    suspend fun getPeople(@Path("crmId") crmId: String): Response<ApiResponse<PeopleResponse>>

    @GET("{crmId}/-/access")
    suspend fun getAccess(@Path("crmId") crmId: String): Response<ApiResponse<AccessResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/access/set")
    suspend fun setAccess(
        @Path("crmId") crmId: String,
        @Field("subject") subject: String,
        @Field("level") level: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/access/revoke")
    suspend fun revokeAccess(
        @Path("crmId") crmId: String,
        @Field("subject") subject: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Objects ----

    @GET("{crmId}/-/objects")
    suspend fun getObjects(@Path("crmId") crmId: String): Response<ApiResponse<ObjectListResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/objects/create")
    suspend fun createObject(
        @Path("crmId") crmId: String,
        @Field("class") classId: String,
        @Field("parent") parent: String?,
        @Field("title") title: String
    ): Response<ApiResponse<ObjectResponse>>

    @GET("{crmId}/-/objects/{objectId}")
    suspend fun getObject(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<ObjectResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/objects/{objectId}/update")
    suspend fun updateObject(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        // Title is a field value (the class's title field), edited via value/set;
        // object/update only handles parent/class. Do NOT add a title field here.
        @Field("parent") parent: String?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{crmId}/-/objects/{objectId}/delete")
    suspend fun deleteObject(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/objects/{objectId}/move")
    suspend fun moveObject(
        @Path("crmId") crmId: String,
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
    @POST("{crmId}/-/objects/{objectId}/values")
    suspend fun setValues(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @FieldMap values: Map<String, String>
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/objects/{objectId}/values/{fieldId}")
    suspend fun setValue(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @Path("fieldId") fieldId: String,
        @Field("value") value: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Links ----

    @GET("{crmId}/-/objects/{objectId}/links")
    suspend fun getLinks(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<LinkListResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/objects/{objectId}/links/create")
    suspend fun createLink(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @Field("target") target: String,
        @Field("linktype") linktype: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/objects/{objectId}/links/delete")
    suspend fun deleteLink(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @Field("target") target: String,
        @Field("linktype") linktype: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Comments ----

    @GET("{crmId}/-/objects/{objectId}/comments")
    suspend fun getComments(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<CommentListResponse>>

    @Multipart
    @POST("{crmId}/-/objects/{objectId}/comments/create")
    suspend fun createComment(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @Part("content") content: RequestBody,
        @Part("parent") parent: RequestBody?,
        @Part files: List<MultipartBody.Part>
    ): Response<ApiResponse<CommentResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/objects/{objectId}/comments/{commentId}/update")
    suspend fun updateComment(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @Path("commentId") commentId: String,
        @Field("content") content: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{crmId}/-/objects/{objectId}/comments/{commentId}/delete")
    suspend fun deleteComment(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @Path("commentId") commentId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Attachments ----

    @GET("{crmId}/-/objects/{objectId}/attachments")
    suspend fun getAttachments(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<AttachmentListResponse>>

    @Multipart
    @POST("{crmId}/-/objects/{objectId}/attachments/create")
    suspend fun createAttachment(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<AttachmentResponse>>

    @POST("{crmId}/-/objects/{objectId}/attachments/{attachmentId}/delete")
    suspend fun deleteAttachment(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String,
        @Path("attachmentId") attachmentId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Activity ----

    @GET("{crmId}/-/objects/{objectId}/activity")
    suspend fun getActivity(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<ActivityListResponse>>

    // ---- Watchers ----

    @GET("{crmId}/-/objects/{objectId}/watchers")
    suspend fun getWatchers(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<WatcherListResponse>>

    @POST("{crmId}/-/objects/{objectId}/watchers/add")
    suspend fun addWatcher(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{crmId}/-/objects/{objectId}/watchers/remove")
    suspend fun removeWatcher(
        @Path("crmId") crmId: String,
        @Path("objectId") objectId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Design: Export / Import ----

    @GET("{crmId}/-/design/export")
    suspend fun exportDesign(@Path("crmId") crmId: String): Response<ApiResponse<JsonObject>>

    @FormUrlEncoded
    @POST("{crmId}/-/design/import")
    suspend fun importDesign(
        @Path("crmId") crmId: String,
        @Field("data") data: String?,
        @Field("template") template: String?,
        @Field("template_version") templateVersion: Int?
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Views ----

    @GET("{crmId}/-/views")
    suspend fun getViews(@Path("crmId") crmId: String): Response<ApiResponse<ViewListResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/views/create")
    suspend fun createView(
        @Path("crmId") crmId: String,
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
    @POST("{crmId}/-/views/reorder")
    suspend fun reorderViews(
        @Path("crmId") crmId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/views/{viewId}/update")
    suspend fun updateView(
        @Path("crmId") crmId: String,
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

    @POST("{crmId}/-/views/{viewId}/delete")
    suspend fun deleteView(
        @Path("crmId") crmId: String,
        @Path("viewId") viewId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Classes ----

    @GET("{crmId}/-/classes")
    suspend fun getClasses(@Path("crmId") crmId: String): Response<ApiResponse<ClassListResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/create")
    suspend fun createClass(
        @Path("crmId") crmId: String,
        @Field("name") name: String
    ): Response<ApiResponse<ClassResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/{classId}/update")
    suspend fun updateClass(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Field("name") name: String?,
        @Field("title") title: String? = null
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{crmId}/-/classes/{classId}/delete")
    suspend fun deleteClass(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Hierarchy ----

    @GET("{crmId}/-/classes/{classId}/hierarchy")
    suspend fun getHierarchy(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String
    ): Response<ApiResponse<HierarchyResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/{classId}/hierarchy/set")
    suspend fun setHierarchy(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Field("parents") parents: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Fields ----

    @GET("{crmId}/-/classes/{classId}/fields")
    suspend fun getFields(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String
    ): Response<ApiResponse<FieldListResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/{classId}/fields/create")
    suspend fun createField(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Field("name") name: String,
        @Field("fieldtype") fieldtype: String,
        @Field("flags") flags: String?,
        @Field("multi") multi: Boolean?
    ): Response<ApiResponse<FieldResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/{classId}/fields/reorder")
    suspend fun reorderFields(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/{classId}/fields/{fieldId}/update")
    suspend fun updateField(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Field("name") name: String?,
        @Field("fieldtype") fieldtype: String?,
        @Field("flags") flags: String?,
        @Field("multi") multi: Boolean?,
        @Field("card") card: Boolean?,
        @Field("position") position: String?,
        @Field("rows") rows: Int?,
        @Field("pattern") pattern: String?,
        @Field("minlength") minlength: Int?,
        @Field("maxlength") maxlength: Int?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{crmId}/-/classes/{classId}/fields/{fieldId}/delete")
    suspend fun deleteField(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String
    ): Response<ApiResponse<SuccessResponse>>

    // ---- Options ----

    @GET("{crmId}/-/classes/{classId}/fields/{fieldId}/options")
    suspend fun getOptions(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String
    ): Response<ApiResponse<OptionListResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/{classId}/fields/{fieldId}/options/create")
    suspend fun createOption(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Field("name") name: String,
        @Field("colour") colour: String?,
        @Field("icon") icon: String? = null
    ): Response<ApiResponse<OptionResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/{classId}/fields/{fieldId}/options/reorder")
    suspend fun reorderOptions(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Field("order") order: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{crmId}/-/classes/{classId}/fields/{fieldId}/options/{optionId}/update")
    suspend fun updateOption(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Path("optionId") optionId: String,
        @Field("name") name: String?,
        @Field("colour") colour: String?,
        @Field("icon") icon: String?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{crmId}/-/classes/{classId}/fields/{fieldId}/options/{optionId}/delete")
    suspend fun deleteOption(
        @Path("crmId") crmId: String,
        @Path("classId") classId: String,
        @Path("fieldId") fieldId: String,
        @Path("optionId") optionId: String
    ): Response<ApiResponse<SuccessResponse>>
}
