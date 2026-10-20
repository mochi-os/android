// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chatlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.chat.model.Chat

class ChatFilterTest {

    private val direct = Chat(id = "d", name = "Dung Nguyen Tien", members = 2, unread = 3)
    private val group = Chat(id = "g", name = "Weekend trip", members = 4)

    @Test
    fun `all keeps every chat`() {
        assertTrue(matchesChatFilter(direct, "", ChatFilter.ALL))
        assertTrue(matchesChatFilter(group, "", ChatFilter.ALL))
    }

    @Test
    fun `unread keeps only chats with unread messages`() {
        assertTrue(matchesChatFilter(direct, "", ChatFilter.UNREAD))
        assertFalse(matchesChatFilter(group, "", ChatFilter.UNREAD))
    }

    @Test
    fun `groups keeps only chats of more than two members`() {
        assertFalse(matchesChatFilter(direct, "", ChatFilter.GROUPS))
        assertTrue(matchesChatFilter(group, "", ChatFilter.GROUPS))
    }

    @Test
    fun `the search matches the chat name ignoring case`() {
        assertTrue(matchesChatFilter(group, " weekEND ", ChatFilter.ALL))
        assertFalse(matchesChatFilter(direct, "weekend", ChatFilter.ALL))
    }

    @Test
    fun `the search and the chip both have to match`() {
        assertFalse(matchesChatFilter(direct, "dung", ChatFilter.GROUPS))
    }
}
