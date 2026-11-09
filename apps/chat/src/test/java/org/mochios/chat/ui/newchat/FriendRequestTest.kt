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
    fun `a chat the one stranger's policy refused names them, to offer a friend request`() {
        // THE POINT: a friend may always be chatted with, so the refused
        // person is the one to ask - and the friend picked beside them is not.
        val state = NewChatUiState(friends = listOf(friend), picked = listOf(friend, stranger))
        assertEquals(Refusal(stranger, refusal), refused(refusal, state))
    }

    @Test
    fun `a server that could not be reached is no refusal`() {
        val state = NewChatUiState(friends = listOf(friend), picked = listOf(stranger))
        assertNull(refused(MochiError.Unknown("The member could not be reached"), state))
    }

    @Test
    fun `several strangers make a refusal naming no one, as the server does not say which`() {
        val other = Friend(id = "o", name = "Lan")
        val state = NewChatUiState(picked = listOf(stranger, other))
        assertEquals(Refusal(null, refusal), refused(refusal, state))
    }

    @Test
    fun `the request opens the person's page in People`() {
        assertEquals("/people/s", friendRequest("s"))
        assertEquals("/people/a%2Fb", friendRequest("a/b"))
    }
}
