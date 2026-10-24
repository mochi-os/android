// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.repository

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.mochios.android.api.unwrap
import org.mochios.android.files.FileRepository
import org.mochios.android.files.FileStore
import org.mochios.android.files.UploadTracker
import org.mochios.android.launcher.Shortcuts
import org.mochios.chat.api.ChatApi
import org.mochios.chat.api.CreateChatResponse
import org.mochios.chat.api.DeleteMessagesResponse
import org.mochios.chat.api.ForwardResponse
import org.mochios.chat.api.MemberAddResponse
import org.mochios.chat.api.MessageListResponse
import org.mochios.chat.api.NewChatResponse
import org.mochios.chat.api.PersonResult
import org.mochios.chat.api.ReactResponse
import org.mochios.chat.api.SearchResponse
import org.mochios.chat.api.SetupResponse
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatMember
import org.mochios.chat.model.ChatPreferences
import org.mochios.chat.model.ChatPresence
import org.mochios.chat.model.ChatViewResponse
import org.mochios.chat.model.EncryptionDevice
import org.mochios.chat.model.EncryptionOverview
import org.mochios.chat.model.Verification
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: ChatApi,
    fileStore: FileStore,
    @param:ApplicationContext private val context: Context,
) : FileRepository(fileStore) {
    /** The user's chats, which those pinned to the home screen are brought into line with. */
    suspend fun listChats(): List<Chat> =
        api.listChats().unwrap().also { chats ->
            Shortcuts.reconcile(context, "chat", Shortcuts.paths(chats, { it.name }) { listOf(it.id, it.key) })
        }

    suspend fun getNewChatData(): NewChatResponse =
        api.getNewChatData().unwrap()

    suspend fun getPreferences(): ChatPreferences =
        api.getPreferences().unwrap()

    /** Change what differs from [before]; what does not is left as the user has it. */
    suspend fun setPreferences(before: ChatPreferences, after: ChatPreferences) {
        api.setPreferences(
            policy = after.policy.takeIf { it != before.policy },
            presence = after.presence.takeIf { it != before.presence }?.toString(),
            receipts = after.receipts.takeIf { it != before.receipts }?.toString(),
            typing = after.typing.takeIf { it != before.typing }?.toString(),
        ).unwrap()
    }

    /** People in the directory matching [query], friends or not. */
    suspend fun personSearch(query: String): List<PersonResult> =
        api.personSearch(query).unwrap().results

    /** A chat with Mochi [members], or with people by their addresses on [account]'s network. */
    suspend fun createChat(name: String, members: List<String> = emptyList(), account: String = ""): CreateChatResponse =
        api.createChat(name, members.takeIf { it.isNotEmpty() }?.joinToString(","), account.ifEmpty { null }).unwrap()

    suspend fun viewChat(chatId: String): ChatViewResponse =
        api.viewChat(chatId).unwrap()

    /** The conversation's messages, or with [thread] the replies under that message. */
    suspend fun getMessages(
        chatId: String,
        cursor: String? = null,
        limit: Int? = null,
        thread: String? = null,
    ): MessageListResponse =
        api.getMessages(chatId, cursor, limit, thread?.ifEmpty { null }).unwrap()

    /** Send a message, into the thread under [thread] where one is named. */
    suspend fun sendMessage(
        chatId: String,
        body: String,
        files: List<File> = emptyList(),
        reply: String? = null,
        thread: String? = null,
    ): String {
        val under = thread?.ifEmpty { null }
        if (files.isEmpty()) {
            return api.sendMessage(chatId, body, reply, under).unwrap().id
        }
        val bodyPart = body.toRequestBody("text/plain".toMediaTypeOrNull())
        val replyPart = reply?.toRequestBody("text/plain".toMediaTypeOrNull())
        val threadPart = under?.toRequestBody("text/plain".toMediaTypeOrNull())
        val parts = fileStore.fileParts("files", files)
        return api.sendMessageWithFiles(chatId, bodyPart, replyPart, threadPart, parts).unwrap().id
    }

    suspend fun sendMessageFromUris(
        chatId: String,
        body: String,
        uris: List<android.net.Uri>,
        context: android.content.Context,
        reply: String? = null,
        tracker: UploadTracker? = null,
    ): String {
        if (uris.isEmpty()) {
            return api.sendMessage(chatId, body, reply).unwrap().id
        }
        val bodyPart = body.toRequestBody("text/plain".toMediaTypeOrNull())
        val replyPart = reply?.toRequestBody("text/plain".toMediaTypeOrNull())
        val files = fileStore.cacheFiles(uris)
        try {
            val parts = fileStore.fileParts("files", files, tracker)
            return api.sendMessageWithFiles(chatId, bodyPart, replyPart, null, parts).unwrap().id
        } finally {
            fileStore.deleteAll(files)
        }
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

    // messages goes over the wire as a JSON array string of ids (server json.decodes it).
    /**
     * Edit one of the caller's own messages. The server is the authority on
     * whether the caller may: it authorises on the author and refuses a
     * deleted message.
     */
    suspend fun editMessage(chatId: String, messageId: String, body: String): Long =
        api.editMessage(
            chatId = chatId,
            chat = chatId,
            message = messageId,
            body = body,
        ).unwrap().edited

    suspend fun forwardMessages(chatId: String, messageIds: List<String>, destination: String): ForwardResponse =
        api.forwardMessages(chatId, Gson().toJson(messageIds), destination).unwrap()

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

    suspend fun typing(chatId: String) {
        api.typing(chatId).unwrap()
    }

    suspend fun presence(chatId: String): ChatPresence =
        api.presence(chatId).unwrap()

    /** Accept an invitation to a room: the account joins it. */
    suspend fun accept(chatId: String) {
        api.accept(chatId).unwrap()
    }

    /** Decline an invitation to a room; the conversation goes. */
    suspend fun decline(chatId: String) {
        api.decline(chatId).unwrap()
    }

    suspend fun encryption(chatId: String): EncryptionOverview =
        api.encryption(chatId).unwrap()

    suspend fun devices(chatId: String, person: String): List<EncryptionDevice> =
        api.devices(chatId, person).unwrap().devices

    suspend fun trust(chatId: String, person: String, device: String, trust: String) {
        api.trust(chatId, person, device, trust).unwrap()
    }

    suspend fun verify(chatId: String, person: String): Verification =
        api.verify(chatId, person).unwrap()

    suspend fun setup(chatId: String, password: String): SetupResponse =
        api.setup(chatId, password.ifEmpty { null }).unwrap()

    suspend fun recover(chatId: String, key: String): Int =
        api.recover(chatId, key).unwrap().restored

    suspend fun verifications(): List<Verification> =
        api.verifications().unwrap().verifications

    suspend fun act(account: String, flow: String, action: String) {
        api.act(account, flow, action).unwrap()
    }
}
