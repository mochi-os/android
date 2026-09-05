// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
    val key: String = "",
    val name: String = "",
    val updated: Long = 0,
    val members: Int = 0,
    val other: String = "",
    /** Messages newer than this account's read watermark. */
    val unread: Int = 0,
    val status: String = ChatStatus.ACTIVE
)

/**
 * A member's avatar through the chat app's own person-asset proxy, which can
 * serve a member whose person entity lives on another server; the direct
 * people route cannot.
 */
fun personAvatarUrl(person: String): String = "/chat/-/person/$person/asset/avatar"

data class ChatMember(
    val id: String = "",
    val name: String = ""
)

data class ChatDetail(
    val id: String = "",
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
    /** Last-edit timestamp; 0 means never edited. Drives the "edited" marker. */
    val edited: Long = 0,
    /** Reaction counts by reaction. */
    val reactions: Map<String, Int> = emptyMap(),
    /** The viewer's own reaction, null when none. */
    val reaction: String? = null,
    /** The id of the message this one quotes, null when none. */
    val reply: String? = null
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
    /** The existing one-on-one chat with this person, empty when none. */
    val chat: String = ""
)
