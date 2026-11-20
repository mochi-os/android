// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.messages

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.market.model.MarketThread
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A conversation in the inbox is the other party's: their face, and their
 * name or fingerprint, whichever side of the sale the reader is on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InboxRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val thread = MarketThread(id = "t1", listing = "l1", buyer = "buyer", seller = "seller", title = "Teapot")

    @Test
    fun `a buyer's conversation is with the seller`() {
        assertEquals("seller", thread.otherParty("buyer"))
        assertEquals("buyer", thread.otherParty("seller"))
    }

    @Test
    fun `the server's other party is taken as it says`() {
        assertEquals("seller", thread.copy(other = "seller").otherParty(null))
    }

    @Test
    fun `no side is taken before the reader is known`() {
        assertNull(thread.otherParty(null))
    }

    @Test
    fun `a party with no name is shown by fingerprint, never by id`() {
        AppContext.set(context)
        rule.setContent {
            InboxRow(thread = thread.copy(otherFingerprint = "abcdefghi"), identity = "buyer", onClick = {})
        }
        rule.onNodeWithText("abc-def-ghi").assertExists()
        rule.onAllNodesWithText("buyer").assertCountEquals(0)
        rule.onAllNodesWithText("seller").assertCountEquals(0)
    }
}
