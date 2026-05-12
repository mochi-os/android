package org.mochios.forums.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.unwrap
import org.mochios.forums.api.CreateCommentResponse
import org.mochios.forums.api.CreatePostResponse
import org.mochios.forums.api.ForumListResponse
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.api.ProbeResponse
import org.mochios.forums.api.RecommendationsResponse
import org.mochios.forums.api.ViewForumResponse
import org.mochios.forums.api.ViewPostResponse
import org.mochios.forums.model.DirectoryEntry
import org.mochios.forums.model.Forum
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

    suspend fun findForums(): List<DirectoryEntry> =
        api.findForums().unwrap().forums

    suspend fun searchForums(query: String): List<DirectoryEntry> =
        api.searchForums(query).unwrap().results

    suspend fun getRecommendations(): RecommendationsResponse =
        api.getRecommendations().unwrap()

    suspend fun probeForum(url: String): ProbeResponse =
        api.probeForum(url).unwrap()

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

    suspend fun editComment(forumId: String, postId: String, commentId: String, body: String) {
        api.editComment(forumId, postId, commentId, body).unwrap()
    }

    suspend fun deleteComment(forumId: String, postId: String, commentId: String) {
        api.deleteComment(forumId, postId, commentId).unwrap()
    }

    suspend fun setDefaultSort(sort: String) {
        api.setDefaultSort(sort).unwrap()
    }

    suspend fun setForumSort(forumId: String, sort: String) {
        api.setForumSort(forumId, sort).unwrap()
    }

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
