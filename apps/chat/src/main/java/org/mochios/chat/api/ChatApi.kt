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
    val fingerprint: String = "",
    val name: String = "",
    val members: List<ChatMember> = emptyList()
)

data class NewChatResponse(
    val name: String = "",
    val friends: List<Friend> = emptyList()
)

data class MessageListResponse(
    val messages: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
    // Keyset cursor for the next (older) page: the oldest message's timestamp
    // plus its id. The id disambiguates messages sharing one whole-second
    // nextCursor, which a timestamp-only cursor cannot.
    val nextCursor: Long? = null,
    val nextCursorId: String? = null
)

data class SendMessageResponse(val id: String = "")

data class MemberListResponse(val members: List<ChatMember> = emptyList())

data class MemberAddResponse(
    val success: Boolean = false,
    val member: ChatMember = ChatMember()
)

data class SuccessResponse(val success: Boolean = false)

data class ForwardResponse(
    val forwarded: List<String> = emptyList(),
    @SerializedName("to_chat") val toChat: String = ""
)

data class ReactResponse(
    @SerializedName("reaction_counts") val reactionCounts: Map<String, Int> = emptyMap(),
    @SerializedName("my_reaction") val myReaction: String? = null
)

data class DeleteMessagesResponse(val deleted: List<String> = emptyList())

data class MarkReadResponse(val read: Long = 0)

data class SearchResponse(
    val query: String = "",
    val results: List<ChatSearchResult> = emptyList()
)

interface ChatApi {

    @GET("-/list")
    suspend fun listChats(): Response<ApiResponse<List<Chat>>>

    @GET("-/new")
    suspend fun getNewChatData(): Response<ApiResponse<NewChatResponse>>

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
        @Query("before") before: Long? = null,
        @Query("before_id") beforeId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<MessageListResponse>>

    @FormUrlEncoded
    @POST("{chatId}/-/send")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Field("body") body: String,
        @Field("reply_to") replyTo: String? = null
    ): Response<ApiResponse<SendMessageResponse>>

    @Multipart
    @POST("{chatId}/-/send")
    suspend fun sendMessageWithFiles(
        @Path("chatId") chatId: String,
        @Part("body") body: RequestBody,
        @Part("reply_to") replyTo: RequestBody? = null,
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

    // message_ids is a JSON-encoded array string (e.g. ["id1","id2"]), matching
    // the web client's URLSearchParams({ message_ids: JSON.stringify(...) }).
    @FormUrlEncoded
    @POST("{chatId}/-/messages/forward")
    suspend fun forwardMessages(
        @Path("chatId") chatId: String,
        @Field("message_ids") messageIds: String,
        @Field("to_chat") toChat: String
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
        @Field("message_ids") messageIds: String
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
        @Field("message_ids") messageIds: String
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
