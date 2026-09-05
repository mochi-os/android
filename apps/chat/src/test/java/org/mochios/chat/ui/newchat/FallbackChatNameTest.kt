// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.chat.model.Friend

class FallbackChatNameTest {

    private val people = listOf(Friend(id = "a", name = "Ann"), Friend(id = "b", name = "Bob"))

    @Test
    fun `the selected people name the chat`() {
        assertEquals("Ann, Bob", fallbackChatName(people, setOf("a", "b"), fallback = "Sohbet"))
    }

    @Test
    fun `the caller's translated fallback names it when nobody resolves`() {
        assertEquals("Sohbet", fallbackChatName(people, setOf("ghost"), fallback = "Sohbet"))
    }
}
