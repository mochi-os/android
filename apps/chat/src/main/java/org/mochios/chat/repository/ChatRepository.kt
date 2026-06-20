// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.repository

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.unwrap
import org.mochios.chat.api.ChatApi
import org.mochios.chat.api.CreateChatResponse
import org.mochios.chat.api.DeleteMessagesResponse
import org.mochios.chat.api.ForwardResponse
import org.mochios.chat.api.MemberAddResponse
import org.mochios.chat.api.MessageListResponse
import org.mochios.chat.api.NewChatResponse
import org.mochios.chat.api.ReactResponse
import org.mochios.chat.api.SearchResponse
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatMember
import org.mochios.chat.model.ChatViewResponse
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: ChatApi
) {
    suspend fun listChats(): List<Chat> =
        api.listChats().unwrap()

    suspend fun getNewChatData(): NewChatResponse =
        api.getNewChatData().unwrap()

    suspend fun createChat(name: String, members: List<String> = emptyList()): CreateChatResponse =
        api.createChat(name, members.takeIf { it.isNotEmpty() }?.joinToString(",")).unwrap()

    suspend fun viewChat(chatId: String): ChatViewResponse =
        api.viewChat(chatId).unwrap()

    suspend fun getMessages(chatId: String, before: Long? = null, beforeId: String? = null, limit: Int? = null): MessageListResponse =
        api.getMessages(chatId, before, beforeId, limit).unwrap()

    suspend fun sendMessage(
        chatId: String,
        body: String,
        files: List<File> = emptyList(),
        replyTo: String? = null,
    ): String {
        if (files.isEmpty()) {
            return api.sendMessage(chatId, body, replyTo).unwrap().id
        }
        val bodyPart = body.toRequestBody("text/plain".toMediaTypeOrNull())
        val replyPart = replyTo?.toRequestBody("text/plain".toMediaTypeOrNull())
        val parts = files.map { file ->
            val mediaType = guessMediaType(file).toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            MultipartBody.Part.createFormData("files", file.name, requestFile)
        }
        return api.sendMessageWithFiles(chatId, bodyPart, replyPart, parts).unwrap().id
    }

    suspend fun sendMessageFromUris(
        chatId: String,
        body: String,
        uris: List<android.net.Uri>,
        contentResolver: android.content.ContentResolver,
        replyTo: String? = null,
    ): String {
        if (uris.isEmpty()) {
            return api.sendMessage(chatId, body, replyTo).unwrap().id
        }
        val bodyPart = body.toRequestBody("text/plain".toMediaTypeOrNull())
        val replyPart = replyTo?.toRequestBody("text/plain".toMediaTypeOrNull())
        val parts = uris.map { uri ->
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val bytes = contentResolver.openInputStream(uri)?.readBytes()
                ?: throw IllegalStateException("Cannot read file: $uri")
            MultipartBody.Part.createFormData(
                "files",
                fileName,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
            )
        }
        return api.sendMessageWithFiles(chatId, bodyPart, replyPart, parts).unwrap().id
    }

    suspend fun getMembers(chatId: String): List<ChatMember> =
        api.getMembers(chatId).unwrap().members

    suspend fun renameChat(chatId: String, name: String) {
        api.renameChat(chatId, name).unwrap()
    }

    suspend fun leaveChat(chatId: String, deleteLocally: Boolean = false) {
        api.leaveChat(chatId, if (deleteLocally) "true" else null).unwrap()
    }

    suspend fun deleteChat(chatId: String) {
        api.deleteChat(chatId).unwrap()
    }

    suspend fun addMember(chatId: String, member: String): MemberAddResponse =
        api.addMember(chatId, member).unwrap()

    suspend fun removeMember(chatId: String, member: String) {
        api.removeMember(chatId, member).unwrap()
    }

    // message_ids goes over the wire as a JSON array string (server json.decodes it).
    suspend fun forwardMessages(chatId: String, messageIds: List<String>, toChat: String): ForwardResponse =
        api.forwardMessages(chatId, Gson().toJson(messageIds), toChat).unwrap()

    // Forward to a friend's 1-on-1 chat (server creates or reuses it atomically).
    suspend fun forwardToFriend(chatId: String, messageIds: List<String>, member: String): ForwardResponse =
        api.forwardToFriend(chatId, member, Gson().toJson(messageIds)).unwrap()

    suspend fun react(chatId: String, messageId: String, reaction: String): ReactResponse =
        api.react(chatId, messageId, reaction).unwrap()

    suspend fun deleteMessages(chatId: String, messageIds: List<String>): DeleteMessagesResponse =
        api.deleteMessages(chatId, Gson().toJson(messageIds)).unwrap()

    /** Move the read watermark; [read] defaults server-side to the latest message. */
    suspend fun markRead(chatId: String, read: Long? = null): Long =
        api.markRead(chatId, read).unwrap().read

    suspend fun search(chatId: String, query: String): SearchResponse =
        api.search(chatId, query).unwrap()

    private fun guessMediaType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
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
