// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.unwrap
import org.mochios.forums.api.AccessResponse
import org.mochios.forums.api.BannerResponse
import org.mochios.forums.api.CreateCommentResponse
import org.mochios.forums.api.CreatePostResponse
import org.mochios.forums.api.ForumListResponse
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.api.MemberSearchResponse
import org.mochios.forums.api.MembersResponse
import org.mochios.forums.api.ModerationLogResponse
import org.mochios.forums.api.ModerationQueueResponse
import org.mochios.forums.api.ModerationReportsResponse
import org.mochios.forums.api.RecommendationsResponse
import org.mochios.forums.api.RestrictionsResponse
import org.mochios.forums.api.RssTokenResponse
import org.mochios.forums.api.ViewForumResponse
import org.mochios.forums.api.ViewPostResponse
import org.mochios.forums.model.AiPrompts
import org.mochios.forums.model.AiSettings
import org.mochios.forums.model.DirectoryEntry
import org.mochios.forums.model.Forum
import org.mochios.forums.model.ModerationSettings
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForumsRepository @Inject constructor(
    private val api: ForumsApi
) {
    private val text = "text/plain".toMediaTypeOrNull()

    suspend fun listForums(sort: String? = null): ForumListResponse =
        api.listForums(sort).unwrap()

    suspend fun viewForum(forumId: String, before: Long? = null, sort: String? = null, tag: String? = null): ViewForumResponse =
        api.viewForum(forumId, before = before, sort = sort, tag = tag).unwrap()

    suspend fun createForum(name: String, privacy: String? = null): String {
        val r = api.createForum(name, privacy).unwrap()
        return r.fingerprint.ifEmpty { r.id }
    }

    suspend fun searchForums(query: String): List<DirectoryEntry> =
        api.searchForums(query).unwrap().results

    suspend fun getRecommendations(): RecommendationsResponse =
        api.getRecommendations().unwrap()

    suspend fun probe(url: String): org.mochios.forums.api.ProbeForumResponse =
        api.probe(url).unwrap()

    suspend fun searchUsers(query: String): List<org.mochios.forums.model.User> =
        api.searchUsers(query).unwrap().results

    suspend fun listGroups(): List<org.mochios.forums.model.Group> =
        api.getGroups().unwrap().groups

    suspend fun subscribe(forumId: String, server: String? = null) {
        api.subscribe(forumId, forumId, server).unwrap()
    }

    suspend fun unsubscribe(forumId: String) {
        api.unsubscribe(forumId).unwrap()
    }

    suspend fun deleteForum(forumId: String) {
        api.deleteForum(forumId).unwrap()
    }

    suspend fun renameForum(forumId: String, name: String) {
        api.renameForum(forumId, forumId, name).unwrap()
    }

    suspend fun viewPost(forumId: String, postId: String): ViewPostResponse =
        api.viewPost(forumId, postId).unwrap()

    suspend fun votePost(forumId: String, postId: String, vote: String) {
        api.votePost(forumId, postId, vote.ifEmpty { "none" }).unwrap()
    }

    suspend fun deletePost(forumId: String, postId: String) {
        api.deletePost(forumId, postId).unwrap()
    }

    suspend fun pinPost(forumId: String, postId: String) {
        api.pinPost(forumId, postId).unwrap()
    }

    suspend fun unpinPost(forumId: String, postId: String) {
        api.unpinPost(forumId, postId).unwrap()
    }

    suspend fun lockPost(forumId: String, postId: String) {
        api.lockPost(forumId, postId).unwrap()
    }

    suspend fun unlockPost(forumId: String, postId: String) {
        api.unlockPost(forumId, postId).unwrap()
    }

    suspend fun approvePost(forumId: String, postId: String) {
        api.approvePost(forumId, postId).unwrap()
    }

    suspend fun removePost(forumId: String, postId: String) {
        api.removePost(forumId, postId).unwrap()
    }

    suspend fun restorePost(forumId: String, postId: String) {
        api.restorePost(forumId, postId).unwrap()
    }

    suspend fun reportPost(forumId: String, postId: String, reason: String, details: String) {
        api.reportPost(forumId, postId, reason, details).unwrap()
    }

    suspend fun removeComment(forumId: String, postId: String, commentId: String) {
        api.removeComment(forumId, postId, commentId).unwrap()
    }

    suspend fun restoreComment(forumId: String, postId: String, commentId: String) {
        api.restoreComment(forumId, postId, commentId).unwrap()
    }

    suspend fun approveComment(forumId: String, postId: String, commentId: String) {
        api.approveComment(forumId, postId, commentId).unwrap()
    }

    suspend fun reportComment(forumId: String, postId: String, commentId: String, reason: String, details: String) {
        api.reportComment(forumId, postId, commentId, reason, details).unwrap()
    }

    suspend fun createPost(forumId: String, title: String, body: String, files: List<File> = emptyList()): CreatePostResponse {
        val parts = files.map { f ->
            MultipartBody.Part.createFormData("attachments", f.name, f.asRequestBody(guessMediaType(f).toMediaTypeOrNull()))
        }
        return api.createPost(
            forum = forumId.toRequestBody(text),
            title = title.toRequestBody(text),
            body = body.toRequestBody(text),
            attachments = parts
        ).unwrap()
    }

    suspend fun editPost(forumId: String, postId: String, title: String, body: String, order: String? = null, files: List<File> = emptyList()) {
        val parts = files.map { f ->
            MultipartBody.Part.createFormData("attachments", f.name, f.asRequestBody(guessMediaType(f).toMediaTypeOrNull()))
        }
        api.editPost(
            forumId = forumId,
            postId = postId,
            title = title.toRequestBody(text),
            body = body.toRequestBody(text),
            order = order?.toRequestBody(text),
            attachments = parts
        ).unwrap()
    }

    suspend fun createComment(forumId: String, postId: String, body: String, parent: String? = null, files: List<File> = emptyList()): CreateCommentResponse {
        val parts = files.map { f ->
            MultipartBody.Part.createFormData("files", f.name, f.asRequestBody(guessMediaType(f).toMediaTypeOrNull()))
        }
        return api.createComment(
            forumId = forumId,
            postId = postId,
            forum = forumId.toRequestBody(text),
            post = postId.toRequestBody(text),
            body = body.toRequestBody(text),
            parent = parent?.toRequestBody(text),
            files = parts
        ).unwrap()
    }

    suspend fun voteComment(forumId: String, postId: String, commentId: String, vote: String) {
        api.voteComment(forumId, postId, commentId, vote.ifEmpty { "none" }).unwrap()
    }

    suspend fun editComment(
        forumId: String,
        postId: String,
        commentId: String,
        body: String,
        order: List<String>? = null,
        files: List<File> = emptyList(),
    ) {
        val parts = files.map { f ->
            MultipartBody.Part.createFormData(
                "files", f.name, f.asRequestBody(guessMediaType(f).toMediaTypeOrNull())
            )
        }
        val orderJson = order?.let { com.google.gson.Gson().toJson(it).toRequestBody(text) }
        api.editComment(
            forumId = forumId,
            postId = postId,
            commentId = commentId,
            body = body.toRequestBody(text),
            order = orderJson,
            files = parts,
        ).unwrap()
    }

    suspend fun editCommentFromUris(
        forumId: String,
        postId: String,
        commentId: String,
        body: String,
        keptAttachmentIds: List<String>?,
        newFileUris: List<android.net.Uri>,
        contentResolver: android.content.ContentResolver,
    ) {
        val newParts = newFileUris.map { uri ->
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val bytes = contentResolver.openInputStream(uri)?.readBytes()
                ?: throw IllegalStateException("Cannot read $uri")
            MultipartBody.Part.createFormData(
                "files", fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
        }
        val order: List<String>? = when {
            keptAttachmentIds == null && newFileUris.isEmpty() -> null
            else -> keptAttachmentIds.orEmpty() + newFileUris.indices.map { "new:$it" }
        }
        val orderJson = order?.let { com.google.gson.Gson().toJson(it).toRequestBody(text) }
        api.editComment(
            forumId = forumId,
            postId = postId,
            commentId = commentId,
            body = body.toRequestBody(text),
            order = orderJson,
            files = newParts,
        ).unwrap()
    }

    suspend fun deleteComment(forumId: String, postId: String, commentId: String) {
        api.deleteComment(forumId, postId, commentId).unwrap()
    }

    suspend fun setForumSort(forumId: String, sort: String) {
        api.setForumSort(forumId, sort).unwrap()
    }

    suspend fun setDefaultSort(sort: String) {
        api.setDefaultSort(sort).unwrap()
    }

    suspend fun moderationQueue(forumId: String): ModerationQueueResponse =
        api.moderationQueue(forumId).unwrap()

    suspend fun moderationReports(forumId: String, status: String = "pending"): ModerationReportsResponse =
        api.moderationReports(forumId, status).unwrap()

    suspend fun moderationLog(forumId: String, limit: Int? = null): ModerationLogResponse =
        api.moderationLog(forumId, limit).unwrap()

    suspend fun restrictions(forumId: String): RestrictionsResponse =
        api.restrictions(forumId).unwrap()

    suspend fun restrict(forumId: String, user: String, type: String, reason: String, duration: Long? = null) {
        api.restrict(forumId, user, type, reason, duration).unwrap()
    }

    suspend fun unrestrict(forumId: String, user: String) {
        api.unrestrict(forumId, user).unwrap()
    }

    suspend fun resolveReport(forumId: String, reportId: String, resolution: String) {
        api.resolveReport(forumId, reportId, resolution).unwrap()
    }

    suspend fun moderationSettings(forumId: String): ModerationSettings =
        api.moderationSettings(forumId).unwrap().settings

    suspend fun saveModerationSettings(forumId: String, settings: ModerationSettings) {
        api.saveModerationSettings(
            forumId,
            settings.moderationPosts,
            settings.moderationComments,
            settings.moderationNew,
            settings.newUserDays,
            settings.postLimit,
            settings.commentLimit,
            settings.limitWindow,
        ).unwrap()
    }

    suspend fun getAccess(forumId: String): AccessResponse =
        api.getAccess(forumId).unwrap()

    suspend fun setAccess(forumId: String, target: String, level: String) {
        api.setAccess(forumId, target, level).unwrap()
    }

    suspend fun revokeAccess(forumId: String, target: String) {
        api.revokeAccess(forumId, target).unwrap()
    }

    suspend fun getMembers(forumId: String): MembersResponse =
        api.getMembers(forumId).unwrap()

    suspend fun searchMembers(forumId: String, query: String): MemberSearchResponse =
        api.searchMembers(forumId, query).unwrap()

    suspend fun removeMember(forumId: String, memberId: String) {
        api.saveMembers(forumId, remove = memberId).unwrap()
    }

    suspend fun getBanner(forumId: String): BannerResponse =
        api.getBanner(forumId).unwrap()

    suspend fun setBanner(forumId: String, banner: String) {
        api.setBanner(forumId, banner).unwrap()
    }

    suspend fun listAiAccounts(): List<org.mochios.android.model.Account> {
        return try {
            api.listAccounts(capability = "ai").unwrap()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun setAiSettings(forumId: String, mode: String, account: Int = 0) {
        api.setAiSettings(forumId, mode, account).unwrap()
    }

    suspend fun getAiPrompts(forumId: String): AiPrompts =
        api.getAiPrompts(forumId).unwrap()

    suspend fun setAiPrompt(forumId: String, type: String, prompt: String) {
        api.setAiPrompt(forumId, type, prompt).unwrap()
    }

    suspend fun addPostTag(forumId: String, postId: String, label: String): Map<String, Any> {
        return api.addPostTag(forumId, postId, label).unwrap()
    }

    suspend fun removePostTag(forumId: String, postId: String, tagId: String) {
        api.removePostTag(forumId, postId, tagId).unwrap()
    }

    suspend fun adjustTagInterest(forumId: String, qid: String, direction: String) {
        api.adjustTagInterest(forumId, qid, direction).unwrap()
    }

    suspend fun clearNotifications(forumId: String) {
        api.clearNotifications(forumId).unwrap()
    }

    suspend fun getRssToken(entity: String, mode: String = "posts"): RssTokenResponse =
        api.getRssToken(entity, mode).unwrap()

    suspend fun getForumTags(forumId: String): List<org.mochios.forums.api.ForumTagCount> =
        api.getForumTags(forumId).unwrap().tags

    private fun guessMediaType(f: File): String {
        return when (f.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
