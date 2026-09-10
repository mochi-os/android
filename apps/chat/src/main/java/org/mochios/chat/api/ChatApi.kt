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
import org.mochios.chat.model.ChatMember
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatSearchResult
import org.mochios.chat.model.ChatViewResponse
import org.mochios.chat.model.Friend
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
    val friends: List<Friend> = emptyList()
)

data class MessageListResponse(
    val messages: List<ChatMessage> = emptyList(),
    val more: Boolean = false,
    /** The server's opaque keyset cursor for the next (older) page; null on the last. */
    val cursor: String? = null
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

// Whom may start a chat with this user (the policy preference).
data class ChatPreferencesResponse(
    val policy: String = "friends"
)

// A directory person for the new-chat picker (non-friends may be addressed
// when their chat_policy allows it; the sender-side probe at create decides).
data class PersonResult(
    val id: String = "",
    val name: String = "",
    val fingerprint: String = ""
)

data class PersonSearchResponse(val results: List<PersonResult> = emptyList())

interface ChatApi {

    @GET("-/list")
    suspend fun listChats(): Response<ApiResponse<List<Chat>>>

    @GET("-/new")
    suspend fun getNewChatData(): Response<ApiResponse<NewChatResponse>>

    @GET("-/preferences/get")
    suspend fun getPreferences(): Response<ApiResponse<ChatPreferencesResponse>>

    @FormUrlEncoded
    @POST("-/preferences/set")
    suspend fun setPreferences(
        @Field("policy") policy: String
    ): Response<ApiResponse<SuccessResponse>>

    @FormUrlEncoded
    @POST("-/person/search")
    suspend fun personSearch(
        @Field("search") search: String
    ): Response<ApiResponse<PersonSearchResponse>>

    @FormUrlEncoded
    @POST("-/create")
    suspend fun createChat(
        @Field("name") name: String,
        @Field("members") members: String?
    ): Response<ApiResponse<CreateChatResponse>>

    @GET("{chatId}/-/view")
    suspend fun viewChat(@Path("chatId") chatId: String): Response<ApiResponse<ChatViewResponse>>

    @GET("{chatId}/-/messages")
    suspend fun getMessages(
        @Path("chatId") chatId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<MessageListResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/send")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Field("body") body: String,
        @Field("reply") reply: String? = null
    ): Response<ApiResponse<SendMessageResponse>>

    @Multipart
    @POST("{chatId}/-/send")
    suspend fun sendMessageWithFiles(
        @Path("chatId") chatId: String,
        @Part("body") body: RequestBody,
        @Part("reply") reply: RequestBody? = null,
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
}
