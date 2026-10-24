// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.chat.model.Friend

class NewGroupChatTest {

    private val picked = listOf(Friend(id = "t", name = "Tien Hoang"), Friend(id = "d", name = "Dung"))

    @Test
    fun `a group is named after the caller and the picked people, all sorted by name`() {
        assertEquals("Chin, Dung, Tien Hoang", groupChatName("Chin", picked, fallback = "Chat"))
    }

    @Test
    fun `the caller's name sorts among the others rather than first`() {
        assertEquals("Dung, Tien Hoang, Zoe", groupChatName("Zoe", picked, fallback = "Chat"))
        assertEquals("Dung, Lan, Tien Hoang", groupChatName("Lan", picked, fallback = "Chat"))
    }

    @Test
    fun `a group without the caller's name still names the picked people`() {
        assertEquals("Dung, Tien Hoang", groupChatName("", picked, fallback = "Chat"))
    }

    @Test
    fun `a large group's default name stops at the server's limit`() {
        val many = (1..200).map { n -> Friend(id = "p$n", name = "Person $n") }
        val name = groupChatName("Chin", many, fallback = "Chat")
        assertEquals(NAME_MAXIMUM, name.length)
        assertTrue(name.startsWith("Chin, Person 1, Person 2, Person 3,"))
    }

    @Test
    fun `a default name cut at the limit never splits a character in two`() {
        // 999 letters then an emoji: the limit falls between its two halves.
        val one = listOf(Friend(id = "a", name = "a".repeat(NAME_MAXIMUM - 1) + "😀"))
        val name = groupChatName("", one, fallback = "Chat")
        assertEquals(NAME_MAXIMUM - 1, name.length)
        assertFalse(name.last().isHighSurrogate())
    }

    @Test
    fun `a group of nameless people falls back to the translated name`() {
        assertEquals("Chat", groupChatName("", listOf(Friend(id = "x")), fallback = "Chat"))
    }

    @Test
    fun `a chat of the caller and exactly the picked people is the same group`() {
        assertTrue(sameGroup(setOf("me", "t", "d"), setOf("t", "d")))
    }

    @Test
    fun `a chat with someone extra is a different group`() {
        assertFalse(sameGroup(setOf("me", "t", "d", "x"), setOf("t", "d")))
    }

    @Test
    fun `a chat missing a picked person is a different group`() {
        assertFalse(sameGroup(setOf("me", "t", "x"), setOf("t", "d")))
    }
}
