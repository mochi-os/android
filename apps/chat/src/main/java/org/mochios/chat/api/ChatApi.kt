// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.api

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.mochios.android.api.ApiResponse
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatAccount
import org.mochios.chat.model.ChatMember
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatPreferences
import org.mochios.chat.model.ChatPresence
import org.mochios.chat.model.ChatReceipt
import org.mochios.chat.model.ChatSearchResult
import org.mochios.chat.model.ChatViewResponse
import org.mochios.chat.model.EncryptionDevice
import org.mochios.chat.model.EncryptionOverview
import org.mochios.chat.model.Friend
import org.mochios.chat.model.Verification
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

data class CreateChatResponse(
    val id: String = "",
    val name: String = "",
    val members: List<ChatMember> = emptyList()
)

data class NewChatResponse(
    val name: String = "",
    val friends: List<Friend> = emptyList(),
    /** The user's accounts on other networks a conversation can be had through. */
    val accounts: List<ChatAccount> = emptyList(),
)

data class MessageListResponse(
    val messages: List<ChatMessage> = emptyList(),
    val more: Boolean = false,
    /** The server's opaque keyset cursor for the next (older) page; null on the last. */
    val cursor: String? = null,
    /** Each member's delivered and displayed watermarks. */
    val receipts: List<ChatReceipt> = emptyList(),
)

data class SendMessageResponse(val id: String = "")

data class MemberListResponse(val members: List<ChatMember> = emptyList())

data class MemberAddResponse(
    val success: Boolean = false,
    val member: ChatMember = ChatMember()
)

data class SuccessResponse(val success: Boolean = false)

/** What messages/edit answers: the id it edited and the stamp it recorded. */
data class EditMessageResponse(
    val id: String = "",
    val edited: Long = 0,
)

data class ForwardResponse(
    val forwarded: List<String> = emptyList(),
    val destination: String = ""
)

data class ReactResponse(
    val reactions: Map<String, Int> = emptyMap(),
    /** The caller's own reaction, null once cleared. */
    val reaction: String? = null
)

data class DeleteMessagesResponse(val deleted: List<String> = emptyList())

data class MarkReadResponse(val read: Long = 0)

data class SearchResponse(
    val query: String = "",
    val results: List<ChatSearchResult> = emptyList()
)

/** What accepting an invitation answers: the conversation, now active. */
data class AcceptResponse(val id: String = "")

data class DeclineResponse(val declined: Boolean = false)

data class DevicesResponse(val devices: List<EncryptionDevice> = emptyList())

data class VerificationsResponse(val verifications: List<Verification> = emptyList())

/** Setting up recovery: the recovery key, shown once, or that the account's password is wanted. */
data class SetupResponse(val recovery: String = "", val password: Boolean = false)

data class RecoverResponse(val restored: Int = 0)

interface ChatApi {

    @GET("-/list")
    suspend fun listChats(): Response<ApiResponse<List<Chat>>>

    @GET("-/new")
    suspend fun getNewChatData(): Response<ApiResponse<NewChatResponse>>

    @GET("-/preferences/get")
    suspend fun getPreferences(): Response<ApiResponse<ChatPreferences>>

    // Only what changed is sent; a field left null keeps the user's setting.
    @FormUrlEncoded
    @POST("-/preferences/set")
    suspend fun setPreferences(
        @Field("policy") policy: String? = null,
        @Field("presence") presence: String? = null,
        @Field("receipts") receipts: String? = null,
        @Field("typing") typing: String? = null,
    ): Response<ApiResponse<Map<String, Any>>>

    // With an account, members are addresses on its network.
    @FormUrlEncoded
    @POST("-/create")
    suspend fun createChat(
        @Field("name") name: String,
        @Field("members") members: String?,
        @Field("account") account: String? = null,
    ): Response<ApiResponse<CreateChatResponse>>

    @GET("{chatId}/-/view")
    suspend fun viewChat(@Path("chatId") chatId: String): Response<ApiResponse<ChatViewResponse>>

    @GET("{chatId}/-/messages")
    suspend fun getMessages(
        @Path("chatId") chatId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("thread") thread: String? = null,
    ): Response<ApiResponse<MessageListResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/send")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Field("body") body: String,
        @Field("reply") reply: String? = null,
        @Field("thread") thread: String? = null,
    ): Response<ApiResponse<SendMessageResponse>>

    @Multipart
    @POST("{chatId}/-/send")
    suspend fun sendMessageWithFiles(
        @Path("chatId") chatId: String,
        @Part("body") body: RequestBody,
        @Part("reply") reply: RequestBody? = null,
        @Part("thread") thread: RequestBody? = null,
        @Part files: List<MultipartBody.Part>
    ): Response<ApiResponse<SendMessageResponse>>

    @GET("{chatId}/-/members")
    suspend fun getMembers(@Path("chatId") chatId: String): Response<ApiResponse<MemberListResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/rename")
    suspend fun renameChat(
        @Path("chatId") chatId: String,
        @Field("name") name: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/leave")
    suspend fun leaveChat(
        @Path("chatId") chatId: String,
        @Field("delete") delete: String?
    ): Response<ApiResponse<SuccessResponse>>

    @POST("{chatId}/-/delete")
    suspend fun deleteChat(@Path("chatId") chatId: String): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/member/add")
    suspend fun addMember(
        @Path("chatId") chatId: String,
        @Field("member") member: String
    ): Response<ApiResponse<MemberAddResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/member/remove")
    suspend fun removeMember(
        @Path("chatId") chatId: String,
        @Field("member") member: String
    ): Response<ApiResponse<SuccessResponse>>

    // Edit your own message. The server authorises on the author, refuses a
    // deleted message, and broadcasts message/edit to the other members.
    @FormUrlEncoded
    @POST("{chatId}/-/messages/edit")
    suspend fun editMessage(
        @Path("chatId") chatId: String,
        @Field("chat") chat: String,
        @Field("message") message: String,
        @Field("body") body: String,
    ): Response<ApiResponse<EditMessageResponse>>

    // messages is a JSON-encoded array string of ids (e.g. ["id1","id2"]),
    // matching the web client's URLSearchParams({ messages: JSON.stringify(...) }).
    @FormUrlEncoded
    @POST("{chatId}/-/messages/forward")
    suspend fun forwardMessages(
        @Path("chatId") chatId: String,
        @Field("messages") messages: String,
        @Field("destination") destination: String
    ): Response<ApiResponse<ForwardResponse>>

    // Forward to a friend: the server atomically reuses or creates the 1-on-1
    // chat with `member` (only after validating the messages), avoiding the
    // orphan-empty-chat race of a client-side create-then-forward. The source
    // chat is the `:chat` path param.
    @FormUrlEncoded
    @POST("{chatId}/-/messages/forward/friend")
    suspend fun forwardToFriend(
        @Path("chatId") chatId: String,
        @Field("member") member: String,
        @Field("messages") messages: String
    ): Response<ApiResponse<ForwardResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/react")
    suspend fun react(
        @Path("chatId") chatId: String,
        @Field("message") message: String,
        @Field("reaction") reaction: String
    ): Response<ApiResponse<ReactResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/messages/delete")
    suspend fun deleteMessages(
        @Path("chatId") chatId: String,
        @Field("messages") messages: String
    ): Response<ApiResponse<DeleteMessagesResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/read")
    suspend fun markRead(
        @Path("chatId") chatId: String,
        @Field("read") read: Long?
    ): Response<ApiResponse<MarkReadResponse>>

    @GET("{chatId}/-/search")
    suspend fun search(
        @Path("chatId") chatId: String,
        @Query("q") query: String
    ): Response<ApiResponse<SearchResponse>>

    // Tell the other members the user is typing; the server sends nothing
    // where the user has turned typing off.
    @POST("{chatId}/-/typing")
    suspend fun typing(@Path("chatId") chatId: String): Response<ApiResponse<Map<String, Any>>>

    // The other member of a one-to-one conversation, or the member named.
    @GET("{chatId}/-/presence")
    suspend fun presence(
        @Path("chatId") chatId: String,
        @Query("member") member: String? = null,
    ): Response<ApiResponse<ChatPresence>>

    @POST("{chatId}/-/accept")
    suspend fun accept(@Path("chatId") chatId: String): Response<ApiResponse<AcceptResponse>>

    @POST("{chatId}/-/decline")
    suspend fun decline(@Path("chatId") chatId: String): Response<ApiResponse<DeclineResponse>>

    // --- The encryption of a Matrix or XMPP conversation, whose device the server holds ---

    @GET("{chatId}/-/encryption")
    suspend fun encryption(@Path("chatId") chatId: String): Response<ApiResponse<EncryptionOverview>>

    @GET("{chatId}/-/encryption/devices")
    suspend fun devices(
        @Path("chatId") chatId: String,
        @Query("person") person: String,
    ): Response<ApiResponse<DevicesResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/encryption/trust")
    suspend fun trust(
        @Path("chatId") chatId: String,
        @Field("person") person: String,
        @Field("device") device: String,
        @Field("trust") trust: String,
    ): Response<ApiResponse<Map<String, Any>>>

    @FormUrlEncoded
    @POST("{chatId}/-/encryption/verify")
    suspend fun verify(
        @Path("chatId") chatId: String,
        @Field("person") person: String,
    ): Response<ApiResponse<Verification>>

    @FormUrlEncoded
    @POST("{chatId}/-/encryption/setup")
    suspend fun setup(
        @Path("chatId") chatId: String,
        @Field("password") password: String? = null,
    ): Response<ApiResponse<SetupResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/encryption/recover")
    suspend fun recover(
        @Path("chatId") chatId: String,
        @Field("key") key: String,
    ): Response<ApiResponse<RecoverResponse>>

    @GET("-/encryption/verifications")
    suspend fun verifications(): Response<ApiResponse<VerificationsResponse>>

    @FormUrlEncoded
    @POST("-/encryption/act")
    suspend fun act(
        @Field("account") account: String,
        @Field("flow") flow: String,
        @Field("action") action: String,
    ): Response<ApiResponse<Map<String, Any>>>
}
