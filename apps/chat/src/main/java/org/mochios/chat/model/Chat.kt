// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.model

import com.google.gson.annotations.SerializedName
import org.mochios.android.model.Attachment
import org.mochios.android.util.NaturalCompare

// Chat status values mirrored from the server (chats.status). 'active' = current
// member; 'left'/'removed' = departed but kept read-only; 'deleted' = hidden
// tombstone (never sent in the chat list); 'invited' = a room on another
// network the user is invited to and has not accepted.
object ChatStatus {
    const val ACTIVE = "active"
    const val LEFT = "left"
    const val REMOVED = "removed"
    const val DELETED = "deleted"
    const val INVITED = "invited"
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
    val status: String = ChatStatus.ACTIVE,
    /**
     * A conversation on another network: the user's account it is had
     * through, that account's address and its network; all empty for Mochi.
     */
    val account: String = "",
    val address: String = "",
    val network: String = "",
)

/**
 * A member's avatar through the chat app's own person-asset proxy, which can
 * serve a member whose person entity lives on another server; the direct
 * people route cannot.
 */
fun personAvatarUrl(person: String): String = "/chat/-/person/$person/asset/avatar"

data class ChatMember(
    val id: String = "",
    val name: String = "",
    /** For someone from Matrix or XMPP in a group, the member whose server relays them. */
    val relay: String = "",
    /** In a room on another network: owner, admin, moderator or visitor. */
    val role: String = "",
)

/**
 * The members in the order the web lists them: [identity], the viewer, first,
 * then everyone else by name, a member with none by its id.
 */
fun List<ChatMember>.ordered(identity: String): List<ChatMember> {
    val (you, others) = partition { it.id == identity }
    return you + others.sortedWith(compareBy(NaturalCompare) { it.name.ifBlank { it.id } })
}

/**
 * The line under a group chat's name: its members in [ordered] order, the
 * viewer as [you]. Blank for a chat of two, whose heading is the other person.
 */
fun List<ChatMember>.heading(identity: String, you: String): String =
    if (size <= 2) "" else ordered(identity).joinToString(", ") { if (it.id == identity) you else it.name }

data class ChatDetail(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val updated: Long = 0,
    val members: List<ChatMember> = emptyList(),
    val status: String = ChatStatus.ACTIVE,
    /** As on [Chat]: the account a conversation on another network is had through. */
    val account: String = "",
    val address: String = "",
    val network: String = "",
    /** A room's id on its network. */
    val room: String = "",
    /** Whether its network encrypts it, which the server does for it. */
    val encrypted: Int = 0,
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
    val reply: String? = null,
    /** The post on another network the message's first link quotes. */
    val quote: ChatQuote? = null,
    /** The message a reply in a thread is under; empty on the main timeline. */
    val thread: String = "",
    /** On the main timeline: how many replies the message's thread has, and when the latest came. */
    val replies: Int = 0,
    val latest: Long = 0,
    /** Whether it came encrypted: with no text or files while its keys have not come. */
    val encrypted: Int = 0,
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
