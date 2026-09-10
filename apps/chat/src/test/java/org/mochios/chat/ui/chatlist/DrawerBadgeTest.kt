// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chatlist

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.chat.model.Chat

class DrawerBadgeTest {

    @Test
    fun `other conversations show the server's unread count`() {
        assertEquals(4, unreadBadge(Chat(id = "c2", unread = 4), open = "c1"))
    }

    @Test
    fun `the open conversation shows no badge`() {
        assertEquals(0, unreadBadge(Chat(id = "c1", unread = 4), open = "c1"))
    }
}
