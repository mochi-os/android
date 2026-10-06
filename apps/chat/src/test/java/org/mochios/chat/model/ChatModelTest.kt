// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatModelTest {

    /** `-/list` answers `unread` per chat; the model must not drop it. */
    @Test
    fun `the unread count from the list survives decoding`() {
        val chat = Gson().fromJson("""{"id":"c1","name":"Ann","members":2,"unread":3}""", Chat::class.java)
        assertEquals(3, chat.unread)
    }

    /** The messages page and its rows use the single-word wire keys. */
    @Test
    fun `a messages page decodes its cursor, its more flag and each row's reactions and reply`() {
        val page = Gson().fromJson(
            """{"messages":[{"id":"m1","body":"hi","reactions":{"like":2},"reaction":"like","reply":"m0"}],"more":true,"cursor":"1700000000:m0"}""",
            org.mochios.chat.api.MessageListResponse::class.java,
        )
        assertEquals(true, page.more)
        assertEquals("1700000000:m0", page.cursor)
        val row = page.messages.single()
        assertEquals(mapOf("like" to 2), row.reactions)
        assertEquals("like", row.reaction)
        assertEquals("m0", row.reply)
    }

    @Test
    fun `a friend's existing one-on-one chat arrives as chat`() {
        val friend = Gson().fromJson("""{"id":"p1","name":"Ann","chat":"c1"}""", Friend::class.java)
        assertEquals("c1", friend.chat)
    }

    @Test
    fun `a member avatar goes through the chat app's person-asset proxy`() {
        val url = personAvatarUrl("p1")
        assertEquals("/chat/-/person/p1/asset/avatar", url)
        // The direct people route cannot serve a member whose person entity
        // lives on another server.
        assertFalse(url.startsWith("/people/"))
    }

    /** The settings list and the chat's heading order members as the web does. */
    @Test
    fun `members put the viewer first, then the rest by name, numbers by value, a nameless one by its id`() {
        val members = listOf(
            ChatMember("c", "Carol"),
            ChatMember("me", "Zed"),
            ChatMember("zzz", ""),
            ChatMember("s10", "Sprint 10"),
            ChatMember("b", "bob"),
            ChatMember("s2", "Sprint 2"),
        )
        assertEquals(listOf("me", "b", "c", "s2", "s10", "zzz"), members.ordered("me").map { it.id })
    }

    /** The heading under a group chat's name reads the members in that order, the viewer as "You". */
    @Test
    fun `a group chat's heading names you first and the rest by name, and a chat of two has none`() {
        val members = listOf(ChatMember("c", "Carol"), ChatMember("me", "Zed"), ChatMember("b", "bob"))
        assertEquals("You, bob, Carol", members.heading("me", "You"))
        assertEquals("", members.take(2).heading("me", "You"))
    }
}
