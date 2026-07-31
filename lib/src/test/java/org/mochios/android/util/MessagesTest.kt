// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Game chat arrives by two routes. REST rows carry a server uid; WebSocket
 * frames carry no id, so the client synthesises one — and deduplicating on
 * that synthetic id both fails to match the REST row (duplicate) and collides
 * between two messages from one sender in the same second (silent loss).
 */
class MessagesTest {

    private data class Message(
        val id: String,
        val created: Long,
        val body: String,
        val name: String,
        val type: String = "message",
    )

    private val key: (Message) -> String = { "${it.created}|${it.body}|${it.name}|${it.type}" }
    private val created: (Message) -> Long = { it.created }

    private fun merge(existing: List<Message>, incoming: List<Message>) =
        mergeMessages(existing, incoming, key, created)

    /** The duplicate: same content, different ids, because one came by socket. */
    @Test
    fun `a socket echo of a row already loaded is dropped`() {
        val fromRest = Message("01H8XUID", 100, "hello", "Alice")
        val fromSocket = Message("ws-message-100-alice", 100, "hello", "Alice")
        assertEquals(listOf(fromRest), merge(listOf(fromRest), listOf(fromSocket)))
    }

    /**
     * The loss. Two messages from one sender in the same second synthesise the
     * same id, so an id-keyed guard drops the second. Keyed on content they
     * are distinct, because the bodies differ.
     */
    @Test
    fun `two messages from one sender in one second both survive`() {
        val first = Message("ws-message-100-alice", 100, "hello", "Alice")
        val second = Message("ws-message-100-alice", 100, "are you there", "Alice")
        val merged = merge(listOf(first), listOf(second))
        assertEquals(2, merged.size)
        assertEquals(listOf("hello", "are you there"), merged.map { it.body })
    }

    @Test
    fun `a genuinely repeated message is still dropped`() {
        val message = Message("a", 100, "hello", "Alice")
        assertEquals(1, merge(listOf(message), listOf(message.copy(id = "b"))).size)
    }

    /** The scrollback case: an older page merges in front, not over the top. */
    @Test
    fun `an older page merges without discarding what is loaded`() {
        val loaded = listOf(Message("c", 300, "third", "Alice"))
        val older = listOf(
            Message("a", 100, "first", "Bob"),
            Message("b", 200, "second", "Alice"),
        )
        assertEquals(
            listOf("first", "second", "third"),
            merge(loaded, older).map { it.body },
        )
    }

    /** A refresh returning the newest page must not drop scrollback. */
    @Test
    fun `refreshing with the newest page keeps older messages`() {
        val withScrollback = listOf(
            Message("a", 100, "old", "Bob"),
            Message("b", 200, "recent", "Alice"),
        )
        val newestPage = listOf(
            Message("b", 200, "recent", "Alice"),
            Message("c", 300, "newest", "Bob"),
        )
        assertEquals(
            listOf("old", "recent", "newest"),
            merge(withScrollback, newestPage).map { it.body },
        )
    }

    @Test
    fun `the result is ordered by created regardless of arrival`() {
        val out = merge(
            listOf(Message("b", 200, "second", "A")),
            listOf(Message("c", 300, "third", "A"), Message("a", 100, "first", "A")),
        )
        assertEquals(listOf(100L, 200L, 300L), out.map { it.created })
    }

    @Test
    fun `merging nothing returns the original list untouched`() {
        val existing = listOf(Message("a", 100, "hello", "Alice"))
        assertEquals(existing, merge(existing, emptyList()))
        assertEquals(existing, merge(existing, listOf(existing.first().copy(id = "z"))))
    }

    @Test
    fun `a single socket frame appends`() {
        val existing = listOf(Message("a", 100, "hello", "Alice"))
        val frame = Message("ws-x", 200, "hi", "Bob")
        assertEquals(
            listOf("hello", "hi"),
            mergeMessage(existing, frame, key, created).map { it.body },
        )
    }

    // ---------------- appendDistinct: paginated lists ----------------

    private fun append(existing: List<Message>, incoming: List<Message>) =
        appendDistinct(existing, incoming, key = { it.id })

    /**
     * Feeds pages by offset over a time-decaying score and schedules a rescore
     * of that column when page 1 is fetched, so page 2 legitimately re-sends
     * rows. Appended bare these become duplicate LazyColumn keys and Compose
     * throws.
     */
    @Test
    fun `an overlapping page contributes only its new rows`() {
        val page1 = listOf(
            Message("a", 300, "first", "X"),
            Message("b", 200, "second", "X"),
        )
        val page2 = listOf(
            Message("b", 200, "second", "X"),
            Message("c", 100, "third", "X"),
        )
        assertEquals(listOf("a", "b", "c"), append(page1, page2).map { it.id })
    }

    /** Forums repeats every pinned post on every page. */
    @Test
    fun `a wholly repeated page adds nothing`() {
        val page1 = listOf(Message("pin", 300, "pinned", "X"), Message("a", 200, "one", "X"))
        assertEquals(page1, append(page1, page1))
    }

    /**
     * The reason mergeMessages is wrong for these lists: the server's order is
     * relevance or pinned-then-score, so nothing may be re-sorted locally.
     */
    @Test
    fun `server order survives, even when it contradicts created`() {
        val page1 = listOf(
            Message("low", 100, "most relevant", "X"),
            Message("high", 900, "less relevant", "X"),
        )
        val page2 = listOf(Message("mid", 500, "least relevant", "X"))
        assertEquals(
            listOf("low", "high", "mid"),
            append(page1, page2).map { it.id },
        )
    }

    @Test
    fun `appending nothing returns the original list`() {
        val existing = listOf(Message("a", 100, "one", "X"))
        assertEquals(existing, append(existing, emptyList()))
    }
}
