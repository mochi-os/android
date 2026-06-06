package org.mochios.wikis.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.model.AccessRule
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.model.Change
import org.mochios.wikis.model.CommentCreateResponse
import org.mochios.wikis.model.CommentEditResponse
import org.mochios.wikis.model.CommentsResponse
import org.mochios.wikis.model.DirectorySearchResponse
import org.mochios.wikis.model.Group
import org.mochios.wikis.model.NewPageResponse
import org.mochios.wikis.model.PageDeleteResponse
import org.mochios.wikis.model.PageEditResponse
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.PageHistoryResponse
import org.mochios.wikis.model.PageRevertResponse
import org.mochios.wikis.model.PageRevisionResponse
import org.mochios.wikis.model.RecommendationsResponse
import org.mochios.wikis.model.Redirect
import org.mochios.wikis.model.Replica
import org.mochios.wikis.model.SearchResponse
import org.mochios.wikis.model.SettingsResponse
import org.mochios.wikis.model.Tag
import org.mochios.wikis.model.TagPagesResponse
import org.mochios.wikis.model.User
import org.mochios.wikis.model.WikiInfoResponse
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository result type for [WikisRepository.createWiki]. Surfaces the
 * fields the UI cares about (id + fingerprint for navigation, home for
 * an initial page redirect) without leaking the full response shape.
 */
data class CreateWikiResult(
    val id: String,
    val fingerprint: String,
    val home: String,
)

/**
 * Repository result type for [WikisRepository.joinWiki]. Same idea as
 * [CreateWikiResult] but for the subscribe-to-remote flow.
 */
data class JoinWikiResult(
    val id: String,
    val fingerprint: String,
    val home: String,
    val source: String? = null,
    val message: String? = null,
)

/**
 * Repository result type for [WikisRepository.renamePage]. Returns the
 * list of resulting slugs (the renamed page plus any child pages that
 * were swept along) and the count of pages whose markdown links were
 * rewritten to point at the new slugs.
 */
data class RenamePageResult(
    val renamed: List<String>,
    val updatedLinks: Int,
)

/**
 * Thin wrapper around [WikisApi]. Mirrors the structure of
 * `FeedsRepository`: every method calls `.unwrap()` on the Retrofit
 * response and re-throws any exception as a typed [org.mochios.android.api.MochiError]
 * via [toMochiError] so ViewModels can render localised messages.
 *
 * The wikis app only exposes entity-context routes (`{wiki}/-/...`) for
 * per-wiki operations — there is no class-context variant of the
 * per-wiki endpoints. The wiki id parameter is either an entity ID or a
 * fingerprint.
 */
@Singleton
class WikisRepository @Inject constructor(
    private val api: WikisApi,
) {

    private val text = "text/plain".toMediaTypeOrNull()

    // ---- Wiki class-level ----

    suspend fun getClassInfo(): WikiInfoResponse {
        return try {
            api.getClassInfo().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun createWiki(name: String, privacy: String): CreateWikiResult {
        return try {
            val r = api.createWiki(name, privacy).unwrap()
            CreateWikiResult(
                id = r.id,
                fingerprint = r.fingerprint,
                home = r.home,
            )
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun joinWiki(target: String, server: String?): JoinWikiResult {
        return try {
            val r = api.joinWiki(target, server).unwrap()
            JoinWikiResult(
                id = r.id,
                fingerprint = r.fingerprint,
                home = r.home,
                source = r.source,
                message = r.message,
            )
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun directorySearch(query: String): DirectorySearchResponse {
        return try {
            api.searchDirectory(query).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun recommendations(): RecommendationsResponse {
        return try {
            api.getRecommendations().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun globalRssToken(mode: String): String {
        return try {
            api.createRssToken("*", mode).unwrap().token
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Per-wiki ----

    suspend fun getInfo(wiki: String): WikiInfoResponse {
        return try {
            api.getInfo(wiki).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun unsubscribeWiki(wiki: String) {
        try {
            api.unsubscribe(wiki).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun syncWiki(wiki: String) {
        try {
            api.sync(wiki).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteWiki(wiki: String) {
        try {
            api.deleteWiki(wiki).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun renameWiki(wiki: String, name: String) {
        try {
            api.renameWiki(wiki, name).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getSettings(wiki: String): SettingsResponse {
        return try {
            api.getSettings(wiki).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setSetting(wiki: String, name: String, value: String) {
        try {
            api.setSetting(wiki, name, value).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getReplicas(wiki: String): List<Replica> {
        return try {
            api.listReplicas(wiki).unwrap().replicas
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun removeReplica(wiki: String, replica: String) {
        try {
            api.removeReplica(wiki, replica).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getAccess(wiki: String): List<AccessRule> {
        return try {
            api.listAccess(wiki).unwrap().rules
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setAccess(wiki: String, subject: String, level: String) {
        try {
            api.setAccess(wiki, subject, level).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun revokeAccess(wiki: String, subject: String) {
        try {
            api.revokeAccess(wiki, subject).unwrap()
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

    suspend fun listGroups(wiki: String): List<Group> {
        return try {
            api.listGroups(wiki).unwrap().groups
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getRedirects(wiki: String): List<Redirect> {
        return try {
            api.listRedirects(wiki).unwrap().redirects
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setRedirect(wiki: String, source: String, target: String) {
        try {
            api.setRedirect(wiki, source, target).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteRedirect(wiki: String, source: String) {
        try {
            api.deleteRedirect(wiki, source).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun wikiRssToken(wiki: String, mode: String): String {
        return try {
            api.createRssToken(wiki, mode).unwrap().token
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Pages ----

    suspend fun getPage(wiki: String, slug: String): PageFetchResponse {
        return try {
            api.getPage(wiki, slug).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun editPage(
        wiki: String,
        slug: String,
        title: String,
        content: String,
        comment: String,
    ): PageEditResponse {
        return try {
            api.editPage(wiki, slug, title, content, comment).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun createPage(
        wiki: String,
        slug: String,
        title: String,
        content: String,
    ): NewPageResponse {
        return try {
            api.createPage(wiki, slug, title, content).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deletePage(wiki: String, slug: String): PageDeleteResponse {
        return try {
            api.deletePage(wiki, slug).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun renamePage(
        wiki: String,
        slug: String,
        newSlug: String,
        createRedirect: Boolean,
    ): RenamePageResult {
        return try {
            val r = api.renamePage(
                wiki = wiki,
                page = slug,
                slug = newSlug,
                redirects = if (createRedirect) "true" else "false",
            ).unwrap()
            RenamePageResult(
                renamed = r.renamed.map { it.new },
                updatedLinks = r.updatedLinks,
            )
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun revertPage(
        wiki: String,
        slug: String,
        version: Int,
        comment: String,
    ): PageRevertResponse {
        return try {
            api.revertPage(wiki, slug, version, comment).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getHistory(wiki: String, slug: String): PageHistoryResponse {
        return try {
            api.getPageHistory(wiki, slug).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getRevision(
        wiki: String,
        slug: String,
        version: Int,
    ): PageRevisionResponse {
        return try {
            api.getPageRevision(wiki, slug, version).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Tags ----

    suspend fun getTags(wiki: String): List<Tag> {
        return try {
            api.listTags(wiki).unwrap().tags
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getPagesForTag(wiki: String, tag: String): TagPagesResponse {
        return try {
            api.listTagPages(wiki, tag).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun addTag(wiki: String, slug: String, tag: String) {
        try {
            api.addTag(wiki, slug, tag).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun removeTag(wiki: String, slug: String, tag: String) {
        try {
            api.removeTag(wiki, slug, tag).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Search + changes ----

    suspend fun search(wiki: String, query: String): SearchResponse {
        return try {
            api.search(wiki, query).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getChanges(wiki: String): List<Change> {
        return try {
            api.getChanges(wiki).unwrap().changes
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Comments ----

    suspend fun getComments(wiki: String, slug: String): CommentsResponse {
        return try {
            api.getPageComments(wiki, slug).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun createComment(
        wiki: String,
        slug: String,
        body: String,
        parent: String?,
        files: List<File>?,
    ): CommentCreateResponse {
        return try {
            val bodyPart = body.toRequestBody(text)
            val parentPart = parent?.toRequestBody(text)
            val fileParts = files.orEmpty().map { multipart("files", it) }
            api.createComment(
                wiki = wiki,
                page = slug,
                body = bodyPart,
                parent = parentPart,
                files = fileParts,
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun editComment(
        wiki: String,
        slug: String,
        id: String,
        body: String,
    ): CommentEditResponse {
        return try {
            api.editComment(wiki, slug, id, body).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteComment(wiki: String, slug: String, id: String) {
        try {
            api.deleteComment(wiki, slug, id).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Attachments ----

    suspend fun getAttachments(wiki: String, slug: String): List<Attachment> {
        return try {
            api.listAttachments(wiki, slug).unwrap().attachments
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun uploadAttachments(
        wiki: String,
        slug: String,
        files: List<File>,
    ): List<Attachment> {
        return try {
            val pagePart = slug.toRequestBody(text)
            val fileParts = files.map { multipart("files", it) }
            api.uploadAttachments(wiki, pagePart, fileParts).unwrap().attachments
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteAttachment(wiki: String, id: String) {
        try {
            api.deleteAttachment(wiki, id).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    private fun multipart(field: String, file: File): MultipartBody.Part {
        val body: RequestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(field, file.name, body)
    }
}
