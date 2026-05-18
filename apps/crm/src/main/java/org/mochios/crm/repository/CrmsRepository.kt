package org.mochios.crm.repository

import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.unwrap
import org.mochios.android.model.AccessRule
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.crm.api.CrmsApi
import org.mochios.crm.model.Activity
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.Group
import org.mochios.crm.model.Link
import org.mochios.crm.model.Person
import org.mochios.crm.model.Crm
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.CrmView
import org.mochios.crm.model.Template
import org.mochios.crm.model.Watcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrmsRepository @Inject constructor(
    private val api: CrmsApi
) {
    // In-memory cache
    private val crmInfoCache = mutableMapOf<String, Pair<CrmDetails, Long>>()
    private val objectsCache = mutableMapOf<String, Pair<List<CrmObject>, Long>>()
    private val cacheMaxAge = 60_000L // 1 minute

    fun getCachedCrmInfo(crmId: String): CrmDetails? {
        val (details, ts) = crmInfoCache[crmId] ?: return null
        if (System.currentTimeMillis() - ts > cacheMaxAge) return null
        return details
    }

    fun getCachedObjects(crmId: String): List<CrmObject>? {
        val (objects, ts) = objectsCache[crmId] ?: return null
        if (System.currentTimeMillis() - ts > cacheMaxAge) return null
        return objects
    }

    fun invalidateCache(crmId: String) {
        crmInfoCache.remove(crmId)
        objectsCache.remove(crmId)
    }

    // ---- Crms ----

    suspend fun listCrms(): List<Crm> =
        api.listCrms().unwrap().crm

    suspend fun createCrm(
        name: String,
        description: String? = null,
        prefix: String? = null,
        privacy: String = "private",
        template: String? = null
    ): Crm = api.createCrm(name, description, prefix, privacy, template).unwrap().crm

    suspend fun getTemplates(): List<Template> =
        api.getTemplates().unwrap().templates

    suspend fun searchDirectory(query: String): List<Crm> =
        api.searchDirectory(query).unwrap().crm

    suspend fun getRecommendations(): List<Crm> =
        api.getRecommendations().unwrap().crm

    suspend fun probe(url: String): Crm =
        api.probe(url).unwrap().crm

    suspend fun subscribe(crm: String, server: String? = null) {
        api.subscribe(crm, server).unwrap()
    }

    suspend fun unsubscribe(crm: String, server: String? = null) {
        api.unsubscribe(crm, server).unwrap()
    }

    suspend fun checkNotifications(): Boolean =
        api.checkNotifications().unwrap().exists

    suspend fun searchUsers(query: String): List<Person> =
        api.searchUsers(query).unwrap().users

    suspend fun getGroups(): List<Group> =
        api.getGroups().unwrap().groups

    // ---- Crm Info ----

    suspend fun getCrmInfo(crmId: String): CrmDetails {
        val r = api.getCrmInfo(crmId).unwrap()
        val details = CrmDetails(
            crm = r.crm,
            classes = r.classes,
            fields = r.fields,
            options = r.options,
            views = r.views,
            hierarchy = r.hierarchy
        )
        crmInfoCache[crmId] = details to System.currentTimeMillis()
        return details
    }

    suspend fun updateCrm(crmId: String, name: String? = null, description: String? = null, prefix: String? = null) {
        api.updateCrm(crmId, name, description, prefix).unwrap()
    }

    suspend fun deleteCrm(crmId: String) {
        api.deleteCrm(crmId).unwrap()
    }

    suspend fun getPeople(crmId: String): List<Person> =
        api.getPeople(crmId).unwrap().people

    suspend fun getAccess(crmId: String): List<AccessRule> =
        api.getAccess(crmId).unwrap().rules

    suspend fun setAccess(crmId: String, subject: String, level: String) {
        api.setAccess(crmId, subject, level).unwrap()
    }

    suspend fun revokeAccess(crmId: String, subject: String) {
        api.revokeAccess(crmId, subject).unwrap()
    }

    // ---- Objects ----

    suspend fun getObjects(crmId: String): List<CrmObject> {
        val objects = api.getObjects(crmId).unwrap().objects
        objectsCache[crmId] = objects to System.currentTimeMillis()
        return objects
    }

    suspend fun createObject(crmId: String, classId: String, parent: String? = null, title: String): CrmObject =
        api.createObject(crmId, classId, parent, title).unwrap().`object`

    suspend fun getObject(crmId: String, objectId: String): CrmObject =
        api.getObject(crmId, objectId).unwrap().`object`

    suspend fun updateObject(crmId: String, objectId: String, title: String? = null, parent: String? = null) {
        api.updateObject(crmId, objectId, title, parent).unwrap()
    }

    suspend fun deleteObject(crmId: String, objectId: String) {
        api.deleteObject(crmId, objectId).unwrap()
    }

    suspend fun moveObject(
        crmId: String,
        objectId: String,
        field: String? = null,
        value: String? = null,
        rank: Int? = null,
        row: String? = null,
        scopeParent: String? = null,
        promote: Boolean = false
    ) {
        api.moveObject(
            crmId, objectId, field, value, rank, row,
            scopeParent,
            if (promote) "true" else null
        ).unwrap()
    }

    suspend fun setValues(crmId: String, objectId: String, values: Map<String, String>) {
        api.setValues(crmId, objectId, values).unwrap()
    }

    suspend fun setValue(crmId: String, objectId: String, fieldId: String, value: String) {
        api.setValue(crmId, objectId, fieldId, value).unwrap()
    }

    // ---- Links ----

    data class LinksResult(val incoming: List<Link>, val outgoing: List<Link>)

    suspend fun getLinks(crmId: String, objectId: String): LinksResult {
        val response = api.getLinks(crmId, objectId).unwrap()
        return LinksResult(incoming = response.incoming, outgoing = response.outgoing)
    }

    suspend fun createLink(crmId: String, objectId: String, target: String, linktype: String) {
        api.createLink(crmId, objectId, target, linktype).unwrap()
    }

    suspend fun deleteLink(crmId: String, objectId: String, target: String, linktype: String) {
        api.deleteLink(crmId, objectId, target, linktype).unwrap()
    }

    // ---- Comments ----

    suspend fun getComments(crmId: String, objectId: String): List<Comment> =
        api.getComments(crmId, objectId).unwrap().comments

    suspend fun createComment(crmId: String, objectId: String, content: String, parent: String? = null, files: List<File> = emptyList()): Comment {
        val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
        val parentBody = parent?.toRequestBody("text/plain".toMediaTypeOrNull())
        val fileParts = files.map { file ->
            val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("file", file.name, requestFile)
        }
        return api.createComment(crmId, objectId, contentBody, parentBody, fileParts).unwrap().comment
    }

    suspend fun updateComment(crmId: String, objectId: String, commentId: String, content: String) {
        api.updateComment(crmId, objectId, commentId, content).unwrap()
    }

    suspend fun deleteComment(crmId: String, objectId: String, commentId: String) {
        api.deleteComment(crmId, objectId, commentId).unwrap()
    }

    // ---- Attachments ----

    suspend fun getAttachments(crmId: String, objectId: String): List<Attachment> =
        api.getAttachments(crmId, objectId).unwrap().attachments

    suspend fun createAttachment(crmId: String, objectId: String, file: File): Attachment {
        val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
        return api.createAttachment(crmId, objectId, part).unwrap().attachment
    }

    suspend fun deleteAttachment(crmId: String, objectId: String, attachmentId: String) {
        api.deleteAttachment(crmId, objectId, attachmentId).unwrap()
    }

    // ---- Activity ----

    suspend fun getActivity(crmId: String, objectId: String): List<Activity> =
        api.getActivity(crmId, objectId).unwrap().activities

    // ---- Watchers ----

    data class WatcherResult(val watchers: List<Watcher>, val watching: Boolean)

    suspend fun getWatchers(crmId: String, objectId: String): WatcherResult {
        val response = api.getWatchers(crmId, objectId).unwrap()
        return WatcherResult(watchers = response.watchers, watching = response.watching)
    }

    suspend fun addWatcher(crmId: String, objectId: String) {
        api.addWatcher(crmId, objectId).unwrap()
    }

    suspend fun removeWatcher(crmId: String, objectId: String) {
        api.removeWatcher(crmId, objectId).unwrap()
    }

    // ---- Design ----

    suspend fun exportDesign(crmId: String): JsonObject =
        api.exportDesign(crmId).unwrap()

    suspend fun importDesign(
        crmId: String,
        data: String? = null,
        template: String? = null,
        templateVersion: Int? = null
    ) {
        api.importDesign(crmId, data, template, templateVersion).unwrap()
    }

    // ---- Views ----

    suspend fun getViews(crmId: String): List<CrmView> =
        api.getViews(crmId).unwrap().views

    suspend fun createView(
        crmId: String,
        name: String,
        viewtype: String,
        columns: String? = null,
        rows: String? = null,
        filter: String? = null,
        sort: String? = null,
        direction: String? = null,
        classes: String? = null,
        border: String? = null
    ): CrmView = api.createView(crmId, name, viewtype, columns, rows, filter, sort, direction, classes, border).unwrap().view

    suspend fun reorderViews(crmId: String, order: String) {
        api.reorderViews(crmId, order).unwrap()
    }

    suspend fun updateView(
        crmId: String,
        viewId: String,
        name: String? = null,
        viewtype: String? = null,
        columns: String? = null,
        rows: String? = null,
        filter: String? = null,
        sort: String? = null,
        direction: String? = null,
        classes: String? = null,
        border: String? = null
    ) {
        api.updateView(crmId, viewId, name, viewtype, columns, rows, filter, sort, direction, classes, border).unwrap()
    }

    suspend fun deleteView(crmId: String, viewId: String) {
        api.deleteView(crmId, viewId).unwrap()
    }

    // ---- Classes ----

    suspend fun getClasses(crmId: String): List<CrmClass> =
        api.getClasses(crmId).unwrap().classes

    suspend fun createClass(crmId: String, name: String): CrmClass =
        api.createClass(crmId, name).unwrap().`class`

    suspend fun updateClass(crmId: String, classId: String, name: String? = null, title: String? = null) {
        api.updateClass(crmId, classId, name, title).unwrap()
    }

    suspend fun deleteClass(crmId: String, classId: String) {
        api.deleteClass(crmId, classId).unwrap()
    }

    // ---- Hierarchy ----

    suspend fun getHierarchy(crmId: String, classId: String): List<String> =
        api.getHierarchy(crmId, classId).unwrap().parents

    suspend fun setHierarchy(crmId: String, classId: String, parents: String) {
        api.setHierarchy(crmId, classId, parents).unwrap()
    }

    // ---- Fields ----

    suspend fun getFields(crmId: String, classId: String): List<CrmField> =
        api.getFields(crmId, classId).unwrap().fields

    suspend fun createField(
        crmId: String,
        classId: String,
        name: String,
        fieldtype: String,
        flags: String? = null,
        multi: Boolean? = null
    ): CrmField = api.createField(crmId, classId, name, fieldtype, flags, multi).unwrap().field

    suspend fun reorderFields(crmId: String, classId: String, order: String) {
        api.reorderFields(crmId, classId, order).unwrap()
    }

    suspend fun updateField(
        crmId: String,
        classId: String,
        fieldId: String,
        name: String? = null,
        fieldtype: String? = null,
        flags: String? = null,
        multi: Boolean? = null,
        card: Boolean? = null,
        position: String? = null,
        rows: Int? = null
    ) {
        api.updateField(crmId, classId, fieldId, name, fieldtype, flags, multi, card, position, rows).unwrap()
    }

    suspend fun deleteField(crmId: String, classId: String, fieldId: String) {
        api.deleteField(crmId, classId, fieldId).unwrap()
    }

    // ---- Options ----

    suspend fun getOptions(crmId: String, classId: String, fieldId: String): List<FieldOption> =
        api.getOptions(crmId, classId, fieldId).unwrap().options

    suspend fun createOption(
        crmId: String,
        classId: String,
        fieldId: String,
        name: String,
        colour: String? = null,
        icon: String? = null
    ): FieldOption = api.createOption(crmId, classId, fieldId, name, colour, icon).unwrap().option

    suspend fun reorderOptions(crmId: String, classId: String, fieldId: String, order: String) {
        api.reorderOptions(crmId, classId, fieldId, order).unwrap()
    }

    suspend fun updateOption(
        crmId: String,
        classId: String,
        fieldId: String,
        optionId: String,
        name: String? = null,
        colour: String? = null,
        icon: String? = null
    ) {
        api.updateOption(crmId, classId, fieldId, optionId, name, colour, icon).unwrap()
    }

    suspend fun deleteOption(crmId: String, classId: String, fieldId: String, optionId: String) {
        api.deleteOption(crmId, classId, fieldId, optionId).unwrap()
    }
}
