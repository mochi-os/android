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
        assertEquals(3, lastLazyIndex(grouped, hasMore = true))
        assertEquals(2, lastLazyIndex(grouped, hasMore = false))
    }

    /** The shipped bug: messages.size - 1 was short by headers + 1. */
    @Test
    fun `counting messages alone lands short of the newest row`() {
        val messages = listOf(message("a", lateEvening), message("b", lateEvening + 60))
        val grouped = groupMessagesByDate(messages, utc)
        assertEquals(3, lastLazyIndex(grouped, hasMore = true))
        assertEquals(1, messages.size - 1)
    }

    @Test
    fun `an empty list has no last index to scroll to`() {
        assertEquals(0, lastLazyIndex(emptyList(), hasMore = false))
        assertEquals(0, lastLazyIndex(emptyList(), hasMore = true))
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
}
