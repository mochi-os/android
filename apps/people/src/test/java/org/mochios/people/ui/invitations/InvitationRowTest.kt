// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.invitations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.people.model.FriendInvite
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An invitation's row on a phone: the name keeps the row's width on one line,
 * cut short with an ellipsis rather than wrapped, and a received invite's
 * Accept and Decline sit beneath it, wrapping onto a second line where the
 * screen is too narrow for both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvitationRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val long = "Alexandra Konstantinopoulou-Vanderberg of the Northern Isles"

    // No id, so no avatar is fetched: the initials stand in.
    private fun invite(name: String) = FriendInvite(name = name, fingerprint = "jnbyw7qtk")

    private fun received(name: String, width: Dp = 411.dp) {
        rule.setContent {
            Box(Modifier.width(width)) { ReceivedRow(invite = invite(name), onAccept = {}, onDecline = {}) }
        }
        rule.waitForIdle()
    }

    private fun sent(name: String) {
        rule.setContent {
            Box(Modifier.width(411.dp)) { SentRow(invite = invite(name), onCancel = {}) }
        }
        rule.waitForIdle()
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    fun `a received invite's name keeps one line with its buttons beneath it`() {
        received("Test Seller")
        assertEquals(1, layout("Test Seller").lineCount)
        val name = rule.onNodeWithText("Test Seller", useUnmergedTree = true).getBoundsInRoot()
        val accept = rule.onNodeWithText("Accept").getBoundsInRoot()
        assertTrue("Accept at ${accept.top}, name ends at ${name.bottom}", accept.top >= name.bottom)
        assertEquals(1, layout("jnb-yw7-qtk").lineCount)
    }

    @Test
    fun `a received invite's long name ends in an ellipsis on one line`() {
        received(long)
        val text = layout(long)
        assertEquals(1, text.lineCount)
        assertTrue(text.isLineEllipsized(0))
    }

    @Test
    fun `on a narrow screen Decline wraps under Accept with its label whole`() {
        received("Test Seller", width = 240.dp)
        assertEquals(1, layout("Decline").lineCount)
        val accept = rule.onNodeWithText("Accept").getBoundsInRoot()
        val decline = rule.onNodeWithText("Decline").getBoundsInRoot()
        assertTrue("Decline at ${decline.top}, Accept ends at ${accept.bottom}", decline.top >= accept.bottom)
    }

    @Test
    fun `a sent invite's long name ends in an ellipsis on one line`() {
        sent(long)
        val text = layout(long)
        assertEquals(1, text.lineCount)
        assertTrue(text.isLineEllipsized(0))
        assertEquals(1, layout("Cancel").lineCount)
    }
}
