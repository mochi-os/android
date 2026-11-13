// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.chat.model.ChatMessage
import java.util.TimeZone

/**
 * The list emits a load-older row and day headers, not one item per message.
 */
class MessageListTest {

    private fun message(id: String, created: Long) =
        ChatMessage(id = id, created = created, body = id)

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    // 2026-03-01 23:30 UTC — the same instant is 2026-03-02 in Sydney.
    private val lateEvening = 1772407800L

    @Test
    fun `the last index counts headers and the load-older row, not messages`() {
        val messages = listOf(message("a", lateEvening), message("b", lateEvening + 60))
        val grouped = groupMessagesByDate(messages, utc)
        // One header plus two messages, and the load-older row above them.
        assertEquals(3, grouped.size)
        assertEquals(3, lastLazyIndex(grouped, more = true))
        assertEquals(2, lastLazyIndex(grouped, more = false))
    }

    /** The shipped bug: messages.size - 1 was short by headers + 1. */
    @Test
    fun `counting messages alone lands short of the newest row`() {
        val messages = listOf(message("a", lateEvening), message("b", lateEvening + 60))
        val grouped = groupMessagesByDate(messages, utc)
        assertEquals(3, lastLazyIndex(grouped, more = true))
        assertEquals(1, messages.size - 1)
    }

    @Test
    fun `an empty list has no last index to scroll to`() {
        assertEquals(0, lastLazyIndex(emptyList(), more = false))
        assertEquals(0, lastLazyIndex(emptyList(), more = true))
    }

    @Test
    fun `each day boundary gets exactly one header`() {
        val day = 24 * 60 * 60L
        val grouped = groupMessagesByDate(
            listOf(message("a", lateEvening), message("b", lateEvening + day), message("c", lateEvening + day + 60)),
            utc,
        )
        assertEquals(2, grouped.count { it is MessageListEntry.DateHeader })
        assertEquals(3, grouped.count { it is MessageListEntry.MessageItem })
    }

    @Test
    fun `a message from the socket is added in time order`() {
        val held = listOf(message("a", 100), message("c", 300))
        assertEquals(listOf("a", "b", "c"), held.adding(message("b", 200), more = false).map { it.id })
        assertEquals(listOf("a", "c", "d"), held.adding(message("d", 400), more = false).map { it.id })
        // Same second: the id breaks the tie, as the server orders a page.
        assertEquals(listOf("a", "b", "c"), listOf(message("a", 100), message("c", 100)).adding(message("b", 100), more = false).map { it.id })
    }

    @Test
    fun `a message older than everything loaded waits for paging while older pages remain`() {
        val held = listOf(message("b", 200), message("c", 300))
        assertEquals(listOf("b", "c"), held.adding(message("a", 100), more = true).map { it.id })
        assertEquals(listOf("a", "b", "c"), held.adding(message("a", 100), more = false).map { it.id })
    }

    @Test
    fun `an edit is applied only over an older one, and never to a tombstone`() {
        val held = message("a", 100).copy(edited = 50)
        assertEquals("newer", held.rewritten("newer", 60).body)
        assertEquals("a", held.rewritten("same", 50).body)
        assertEquals("a", held.rewritten("older", 40).body)
        val tombstone = held.tombstone()
        assertEquals("", tombstone.rewritten("again", 70).body)
    }

    /**
     * 23:30 -> 00:10 UTC spans midnight in UTC but not in Sydney, so the zone
     * decides the header count.
     */
    @Test
    fun `the zone decides where a day boundary falls`() {
        val messages = listOf(message("a", lateEvening), message("b", lateEvening + 40 * 60))
        val inUtc = groupMessagesByDate(messages, utc)
        val inSydney = groupMessagesByDate(messages, TimeZone.getTimeZone("Australia/Sydney"))
        assertEquals(2, inUtc.count { it is MessageListEntry.DateHeader })
        assertEquals(1, inSydney.count { it is MessageListEntry.DateHeader })
    }

    @Test
    fun `a run of history from before the account was added shows as one line where it starts`() {
        val day = 24 * 60 * 60L
        fun sealed(id: String, created: Long, encrypted: Int) =
            ChatMessage(id = id, created = created, encrypted = encrypted)
        val grouped = groupMessagesByDate(
            listOf(
                sealed("a", lateEvening, 2),
                // The next day, but within the run: no header of its own.
                sealed("b", lateEvening + day, 2),
                message("c", lateEvening + day + 60),
                sealed("d", lateEvening + day + 120, 2),
                sealed("e", lateEvening + day + 180, 2),
                sealed("f", lateEvening + day + 240, 3),
                sealed("g", lateEvening + day + 300, 1),
            ),
            utc,
        )
        assertEquals(
            listOf("date", "earlier a", "date", "message c", "earlier d", "message f", "message g"),
            grouped.map {
                when (it) {
                    is MessageListEntry.DateHeader -> "date"
                    is MessageListEntry.EarlierHistory -> "earlier " + it.message.id
                    is MessageListEntry.MessageItem -> "message " + it.message.id
                }
            },
        )
    }

    @Test
    fun `a deleted message or one with text is never history`() {
        assertEquals(true, isEarlierHistory(ChatMessage(id = "a", encrypted = 2)))
        assertEquals(false, isEarlierHistory(ChatMessage(id = "a", encrypted = 2, deleted = true)))
        assertEquals(false, isEarlierHistory(ChatMessage(id = "a", encrypted = 2, body = "text")))
        assertEquals(false, isEarlierHistory(ChatMessage(id = "a", encrypted = 3)))
    }
}
