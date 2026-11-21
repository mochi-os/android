// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.api.MochiError
import kotlin.time.Duration

/**
 * A game's chat keeps the older pages it has read when the newest page is
 * read again after a move, a frame or a return to the screen; only a page
 * sharing nothing with them replaces them. A first page that fails is an
 * error to show; a send says whether it went.
 */
class GameChatTest {

    private data class Row(val id: String, val created: Long, val text: String = "line $created")

    private fun rows(range: IntRange) = range.map { at -> Row("r$at", at.toLong()) }

    /** What each read answers: the newest page for null, else the page before the cursor. */
    private val pages = mutableMapOf<String?, GameChatPage<Row>>()

    /** Reads that fail, by cursor. */
    private val failing = mutableSetOf<String?>()

    /** Reads held until their gate opens, by cursor. */
    private val gates = mutableMapOf<String?, CompletableDeferred<Unit>>()

    private var reads = 0
    private var sendFails = false
    private val reported = mutableListOf<MochiError>()

    private fun chat(scope: CoroutineScope) = GameChat(
        scope = scope,
        fetch = { before ->
            reads += 1
            gates[before]?.await()
            if (before in failing) throw MochiError.ServerError(503, "down")
            pages.getValue(before)
        },
        post = { if (sendFails) throw MochiError.NetworkError() },
        key = { row -> row.id },
        created = { row -> row.created },
        match = { row -> "${row.created}|${row.text}" },
        report = { error -> reported += error },
        debounce = Duration.ZERO,
    )

    private suspend fun settle() = repeat(10) { yield() }

    @Test
    fun `a refresh keeps the older pages read and their cursor`() = runBlocking {
        pages[null] = GameChatPage(rows(5..9).reversed(), more = true, cursor = "c5")
        pages["c5"] = GameChatPage(rows(0..4), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        chat.loadMore()
        settle()
        assertEquals(rows(0..9), chat.state.value.messages)
        pages[null] = GameChatPage(rows(6..10), more = true, cursor = "c6")
        chat.refresh()
        settle()
        assertEquals("THE POINT: the older page survives the refresh", rows(0..10), chat.state.value.messages)
        assertFalse("the history is still read to its start", chat.state.value.hasMore)
        assertNull(chat.state.value.cursor)
    }

    @Test
    fun `a newest page sharing nothing with the loaded rows replaces them`() = runBlocking {
        pages[null] = GameChatPage(rows(0..4), more = true, cursor = "c0")
        val chat = chat(this)
        chat.load()
        settle()
        // More than a page was said while the screen was away.
        pages[null] = GameChatPage(rows(20..24), more = true, cursor = "c20")
        chat.refresh()
        settle()
        assertEquals(rows(20..24), chat.state.value.messages)
        assertEquals("c20", chat.state.value.cursor)
    }

    @Test
    fun `an older page asked for before a replacement is not stitched on after it`() = runBlocking {
        pages[null] = GameChatPage(rows(5..9), more = true, cursor = "c5")
        pages["c5"] = GameChatPage(rows(0..4), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        val gate = CompletableDeferred<Unit>()
        gates["c5"] = gate
        chat.loadMore()
        settle()
        pages[null] = GameChatPage(rows(20..24), more = true, cursor = "c20")
        chat.refresh()
        settle()
        gate.complete(Unit)
        settle()
        assertEquals("THE POINT: no gap hidden by stitching", rows(20..24), chat.state.value.messages)
        assertEquals("c20", chat.state.value.cursor)
        assertFalse(chat.state.value.isLoadingMore)
    }

    @Test
    fun `a first page that fails is an error to show, and a retry clears it`() = runBlocking {
        failing += null
        pages[null] = GameChatPage(rows(0..2), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        assertTrue("THE POINT: the empty chat says why", chat.state.value.error is MochiError.ServerError)
        assertFalse(chat.state.value.isLoading)
        failing.clear()
        chat.load()
        settle()
        assertNull(chat.state.value.error)
        assertEquals(rows(0..2), chat.state.value.messages)
    }

    @Test
    fun `a refresh that fails keeps what is loaded and says nothing`() = runBlocking {
        pages[null] = GameChatPage(rows(0..2), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        failing += null
        chat.refresh()
        settle()
        assertEquals(rows(0..2), chat.state.value.messages)
        assertNull(chat.state.value.error)
    }

    @Test
    fun `an older page that fails is reported`() = runBlocking {
        pages[null] = GameChatPage(rows(5..9), more = true, cursor = "c5")
        failing += "c5"
        val chat = chat(this)
        chat.load()
        settle()
        chat.loadMore()
        settle()
        assertEquals(1, reported.size)
        assertTrue(chat.state.value.hasMore)
        assertFalse(chat.state.value.isLoadingMore)
    }

    @Test
    fun `a frame's row is kept until the page brings it`() = runBlocking {
        pages[null] = GameChatPage(rows(0..2), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        chat.receive(Row("r3", 3))
        chat.receive(Row("r3", 3))
        assertEquals(rows(0..3), chat.state.value.messages)
    }

    @Test
    fun `a send reports its failure and leaves the list alone`() = runBlocking {
        pages[null] = GameChatPage(rows(0..2), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        val before = reads
        sendFails = true
        var outcome: MochiError? = MochiError.Unknown()
        chat.send("good game") { error -> outcome = error }
        settle()
        assertTrue(outcome is MochiError.NetworkError)
        assertEquals(before, reads)
        sendFails = false
        chat.send("good game") { error -> outcome = error }
        settle()
        assertNull(outcome)
        assertEquals("a sent message reads the newest page", before + 1, reads)
    }

    @Test
    fun `two frames alike from one player in one second both show`() = runBlocking {
        pages[null] = GameChatPage(rows(0..2), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        chat.receive(Row("m1", 9, "gg"))
        chat.receive(Row("m2", 9, "gg"))
        val ids = chat.state.value.messages.map { row -> row.id }
        assertEquals("THE POINT: neither folds into the other", listOf("r0", "r1", "r2", "m1", "m2"), ids)
    }

    @Test
    fun `an older server's frames stand in until their rows arrive, one for one`() = runBlocking {
        pages[null] = GameChatPage(rows(0..2), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        chat.receive(Row(frameRowId(null), 9, "gg"))
        chat.receive(Row(frameRowId(null), 9, "gg"))
        assertEquals("both frames show", 5, chat.state.value.messages.size)
        pages[null] = GameChatPage(rows(0..2) + Row("m1", 9, "gg") + Row("m2", 9, "gg"), more = false, cursor = null)
        chat.refresh()
        settle()
        assertEquals(
            "THE POINT: each stored row takes one stand-in's place",
            listOf("r0", "r1", "r2", "m1", "m2"),
            chat.state.value.messages.map { row -> row.id },
        )
    }

    @Test
    fun `an older server's frame for a row already read adds nothing`() = runBlocking {
        pages[null] = GameChatPage(rows(0..2) + Row("m1", 9, "gg"), more = false, cursor = null)
        val chat = chat(this)
        chat.load()
        settle()
        chat.receive(Row(frameRowId(null), 9, "gg"))
        assertEquals(listOf("r0", "r1", "r2", "m1"), chat.state.value.messages.map { row -> row.id })
    }

    @Test
    fun `a frame's id is the stored row's, or one of the device's own`() {
        assertEquals("m1", frameRowId("m1"))
        assertTrue("THE POINT: never shared", frameRowId(null) != frameRowId(null))
        assertTrue(frameRowId("") != frameRowId(""))
    }
}
