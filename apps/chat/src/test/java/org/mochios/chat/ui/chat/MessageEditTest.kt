// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.chat.model.ChatMessage

/**
 * Which messages offer Edit. The server refuses the rest, but offering an
 * action that always fails is worse than not offering it.
 */
class MessageEditTest {

    private fun message(body: String = "hello", deleted: Boolean = false) =
        ChatMessage(id = "m1", created = 1_772_407_800L, body = body, deleted = deleted)

    @Test
    fun `your own live message can be edited`() {
        assertTrue(canEditMessage(message(), isOwn = true))
    }

    @Test
    fun `someone else's message cannot`() {
        assertFalse(canEditMessage(message(), isOwn = false))
    }

    @Test
    fun `a tombstone cannot be edited, even your own`() {
        assertFalse(canEditMessage(message(body = "", deleted = true), isOwn = true))
        // A tombstone that still carries its body is the same refusal.
        assertFalse(canEditMessage(message(deleted = true), isOwn = true))
    }

    @Test
    fun `an attachment-only message has no body to edit`() {
        assertFalse(canEditMessage(message(body = ""), isOwn = true))
        assertFalse(canEditMessage(message(body = "   "), isOwn = true))
    }
}
