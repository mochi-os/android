// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.api.MochiError
import org.mochios.chat.model.ChatMessage

/**
 * The state transitions behind the composer, the failure surface and the
 * follow-to-newest scroll.
 */
class ChatStateTest {

    private fun message(id: String) = ChatMessage(id = id, body = id)

    private val loaded = listOf(message("a"), message("b"))

    @Test
    fun `a failed send keeps the draft and the reply target`() {
        val state = ChatUiState(draft = "hello", replyingTo = message("r"), messages = loaded)
        val after = state.afterSend(sent = false)
        assertEquals("hello", after.draft)
        assertEquals(message("r"), after.replyingTo)
    }

    @Test
    fun `an accepted send clears the composer`() {
        val state = ChatUiState(draft = "hello", replyingTo = message("r"), messages = loaded)
        val after = state.afterSend(sent = true)
        assertEquals("", after.draft)
        assertNull(after.replyingTo)
        assertTrue(after.pendingAttachments.isEmpty())
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
