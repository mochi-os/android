package org.mochios.chat.model

import com.google.gson.annotations.SerializedName
import org.mochios.android.model.Attachment

// Chat status values mirrored from the server (chats.status). 'active' = current
// member; 'left'/'removed' = departed but kept read-only; 'deleted' = hidden
// tombstone (never sent in the chat list).
object ChatStatus {
    const val ACTIVE = "active"
    const val LEFT = "left"
    const val REMOVED = "removed"
    const val DELETED = "deleted"
}

data class Chat(
    val id: String = "",
    val fingerprint: String = "",
    val identity: String = "",
    val key: String = "",
    val name: String = "",
    val updated: Long = 0,
    val members: Int = 0,
    val other: String = "",
    val status: String = ChatStatus.ACTIVE
)

data class ChatMember(
    val id: String = "",
    val name: String = ""
)

data class ChatDetail(
    val id: String = "",
    val fingerprint: String = "",
    val identity: String = "",
    val key: String = "",
    val name: String = "",
    val updated: Long = 0,
    val members: List<ChatMember> = emptyList(),
    val status: String = ChatStatus.ACTIVE
)

data class ChatViewResponse(
    val chat: ChatDetail = ChatDetail(),
    val identity: String = ""
)

data class ChatMessage(
    val id: String = "",
    val chat: String = "",
    val member: String = "",
    val name: String = "",
    val body: String = "",
    val created: Long = 0,
    val attachments: List<Attachment> = emptyList(),
    val deleted: Boolean = false,
    @SerializedName("reaction_counts") val reactionCounts: Map<String, Int> = emptyMap(),
    @SerializedName("my_reaction") val myReaction: String? = null,
    @SerializedName("reply_to") val replyTo: String? = null
)

/** A message hit from `:chat/-/search`. */
data class ChatSearchResult(
    val id: String = "",
    val member: String = "",
    val name: String = "",
    val body: String = "",
    val excerpt: String = "",
    val created: Long = 0
)

data class Friend(
    val id: String = "",
    val identity: String = "",
    val name: String = "",
    @SerializedName("class") val klass: String = "",
    val chatId: String = ""
)
