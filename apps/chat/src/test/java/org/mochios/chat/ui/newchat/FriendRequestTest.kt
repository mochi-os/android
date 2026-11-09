// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.api.MochiError
import org.mochios.chat.model.Friend

class FriendRequestTest {

    private val friend = Friend(id = "f", name = "Dung")
    private val stranger = Friend(id = "s", name = "Tien Hoang")
    private val refusal = MochiError.ForbiddenError("This member does not accept chats from you")

    @Test
    fun `a chat the one stranger's policy refused offers them a friend request`() {
        // THE POINT: a friend may always be chatted with, so the refused
        // person is the one to ask - and the friend picked beside them is not.
        val state = NewChatUiState(friends = listOf(friend), picked = listOf(friend, stranger))
        assertEquals("s", refused(refusal, state))
    }

    @Test
    fun `a server that could not be reached offers nothing`() {
        val state = NewChatUiState(friends = listOf(friend), picked = listOf(stranger))
        assertNull(refused(MochiError.Unknown("The member could not be reached"), state))
    }

    @Test
    fun `several strangers offer nothing, as the refusal does not say which`() {
        val other = Friend(id = "o", name = "Lan")
        val state = NewChatUiState(picked = listOf(stranger, other))
        assertNull(refused(refusal, state))
    }

    @Test
    fun `the request opens People's add screen on the person`() {
        assertEquals("/people?action=add&search=s", friendRequest("s"))
        assertEquals("/people?action=add&search=a+b%26c", friendRequest("a b&c"))
    }
}
