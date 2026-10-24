// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chatlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatStatus

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
    fun `unread keeps the open chat once it has been read`() {
        assertTrue(matchesChatFilter(group, "", ChatFilter.UNREAD, open = "g"))
    }

    @Test
    fun `unread skips a chat the user has left`() {
        val left = direct.copy(status = ChatStatus.LEFT)
        assertFalse(matchesChatFilter(left, "", ChatFilter.UNREAD))
    }

    @Test
    fun `groups keeps every chat that isn't a 1-1`() {
        assertFalse(matchesChatFilter(direct, "", ChatFilter.GROUPS))
        assertTrue(matchesChatFilter(group, "", ChatFilter.GROUPS))
    }

    @Test
    fun `groups keeps a group everyone else has left`() {
        assertTrue(matchesChatFilter(group.copy(members = 1), "", ChatFilter.GROUPS))
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
