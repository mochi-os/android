// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.chat.model.ChatMessage
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The state transitions behind the composer, the failure surface and the
 * follow-to-newest scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatStateTest {

    private fun message(id: String) = ChatMessage(id = id, body = id)

    private val loaded = listOf(message("a"), message("b"))

    private val picture = Uri.parse("content://media/1")

    private fun failed(text: String = "hello", thread: String = "") =
        Unsent(key = 7, text = text, attachments = listOf(picture), reply = message("r"), thread = thread, error = MochiError.NetworkError())

    @Test
    fun `a send takes the message out of the composer as it starts`() {
        val state = ChatUiState(draft = "hello", replyingTo = message("r"), pendingAttachments = listOf(picture), messages = loaded)
        val after = state.sending()
        assertEquals("", after.draft)
        assertNull(after.replyingTo)
        assertTrue(after.pendingAttachments.isEmpty())
        assertTrue(after.isSending)
    }

    @Test
    fun `a failed send goes back into a composer still empty, and says why once`() {
        val after = ChatUiState(messages = loaded).sending().unsent(failed())
        assertEquals("hello", after.draft)
        assertEquals(listOf(picture), after.pendingAttachments)
        assertEquals(message("r"), after.replyingTo)
        assertEquals(MochiError.NetworkError(), after.notice)
        assertTrue(after.unsent.isEmpty())
    }

    @Test
    fun `a failed send leaves the next message in the composer and is listed unsent`() {
        for (next in listOf(
            ChatUiState(draft = "the next one", messages = loaded),
            ChatUiState(pendingAttachments = listOf(picture), messages = loaded),
            ChatUiState(replyingTo = message("q"), messages = loaded),
        )) {
            val after = next.unsent(failed())
            assertEquals(next.draft, after.draft)
            assertEquals(next.pendingAttachments, after.pendingAttachments)
            assertEquals(next.replyingTo, after.replyingTo)
            assertEquals(listOf(failed()), after.unsent)
            assertNull("the row says why", after.notice)
        }
    }

    @Test
    fun `a thread reply that failed with its thread closed waits with the unsent, not in the composer`() {
        val after = ChatUiState(messages = loaded).unsent(failed(thread = "m1"))
        assertEquals("", after.draft)
        assertEquals(listOf(failed(thread = "m1")), after.unsent)
    }

    @Test
    fun `a message's length is counted in code points, as the server counts it`() {
        assertEquals(1, length("😀"))
        assertEquals(3, length("é😀a"))
        val full = "😀".repeat(MESSAGE_MAXIMUM)
        assertEquals(2 * MESSAGE_MAXIMUM, full.length)
        assertFalse(overLimit(full))
        assertTrue(overLimit(full + "a"))
    }

    @Test
    fun `a deleted message keeps its thread`() {
        val root = message("a").copy(replies = 2, latest = 50, answers = listOf(message("r1"), message("r2")), warning = "distrusted")
        val gone = root.tombstone()
        assertTrue(gone.deleted)
        assertEquals("", gone.body)
        assertEquals(2, gone.replies)
        assertEquals(50, gone.latest)
        assertEquals(listOf("r1", "r2"), gone.answers.map { answer -> answer.id })
        assertEquals("a tombstone warns of nothing", "", gone.warning)
    }

    @Test
    fun `an edit's warning replaces the message's, and only a newer edit applies`() {
        val held = message("a").copy(edited = 10, warning = "distrusted")
        assertEquals("mismatched", held.rewritten("new", 20, "mismatched").warning)
        assertEquals("", held.rewritten("new", 20, "").warning)
        assertEquals("distrusted", held.rewritten("older", 5, "mismatched").warning)
    }

    @Test
    fun `a message arriving takes down only a reader at the newest, the user's own always`() {
        assertTrue(followsReader(following = true, own = false, first = false, searching = false))
        assertTrue("the user's own", followsReader(following = false, own = true, first = false, searching = false))
        assertTrue("on opening", followsReader(following = false, own = false, first = true, searching = false))
        assertFalse("a reader up the list keeps their place", followsReader(following = false, own = false, first = false, searching = false))
        assertFalse("the search's match has the list", followsReader(following = true, own = true, first = false, searching = true))
    }

    @Test
    fun `the jump button counts the messages after the one last shown`() {
        val list = loaded + message("c") + message("d")
        assertEquals(2, arrivals(message("b"), list))
        assertEquals(0, arrivals(message("d"), list))
        assertEquals(0, arrivals(null, list))
    }

    @Test
    fun `an edit has the composer that holds the message edited, the draft waiting`() {
        val main = ChatUiState(draft = "half written", editing = message("a"), editDraft = "a, fixed")
        assertEquals("a, fixed", main.composed())
        assertEquals("half written", main.copy(editing = null, editDraft = "").composed())
        val reply = message("r1").copy(thread = "a")
        val thread = ChatUiState(draft = "half written", editing = reply, editDraft = "r1, fixed", thread = ThreadState(root = message("a"), draft = "a reply"))
        assertEquals("the main composer keeps its draft", "half written", thread.composed())
        assertEquals("r1, fixed", thread.threadComposed())
        assertEquals("a reply", thread.copy(editing = null).threadComposed())
    }

    @Test
    fun `a failure with nothing loaded is the page error`() {
        val state = ChatUiState().failed(MochiError.Unknown("refused"))
        assertEquals(MochiError.Unknown("refused"), state.error)
        assertNull(state.notice)
    }

    @Test
    fun `a failure over a loaded conversation is a notice, since the page error never renders then`() {
        val state = ChatUiState(messages = loaded).failed(MochiError.Unknown("refused"))
        assertEquals(MochiError.Unknown("refused"), state.notice)
        assertNull(state.error)
    }

    @Test
    fun `the first page follows to the newest message`() {
        assertTrue(followsNewest(emptyList(), loaded))
    }

    @Test
    fun `a message arriving or being sent follows`() {
        assertTrue(followsNewest(loaded, loaded + message("c")))
    }

    @Test
    fun `paging older history in leaves the reader where they are`() {
        // The list grew, but at the old end: the newest message is unchanged.
        assertFalse(followsNewest(loaded, listOf(message("z")) + loaded))
    }

    @Test
    fun `deleting the newest message does not yank the list`() {
        assertFalse(followsNewest(loaded, listOf(message("a"))))
    }

    @Test
    fun `an unchanged or emptied list does not follow`() {
        assertFalse(followsNewest(loaded, loaded.toList()))
        assertFalse(followsNewest(loaded, emptyList()))
    }
}
