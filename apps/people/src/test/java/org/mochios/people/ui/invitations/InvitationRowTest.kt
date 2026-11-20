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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.people.model.FriendInvite
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An invitation's row on a phone: one line, the name cut short with an
 * ellipsis rather than wrapped, and a received invite's Accept and Decline
 * icon buttons at its end, named for screen readers by their labels.
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

    private fun received(
        name: String,
        width: Dp = 411.dp,
        onAccept: () -> Unit = {},
        onDecline: () -> Unit = {},
    ) {
        rule.setContent {
            Box(Modifier.width(width)) { ReceivedRow(invite = invite(name), onAccept = onAccept, onDecline = onDecline) }
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
    fun `a received invite's name, Accept and Decline share one line`() {
        received("Test Seller")
        val text = layout("Test Seller")
        assertEquals(1, text.lineCount)
        assertFalse(text.isLineEllipsized(0))
        assertEquals(1, layout("jnb-yw7-qtk").lineCount)
        val name = rule.onNodeWithText("Test Seller", useUnmergedTree = true).getBoundsInRoot()
        val accept = rule.onNodeWithContentDescription("Accept").getBoundsInRoot()
        val decline = rule.onNodeWithContentDescription("Decline").getBoundsInRoot()
        for ((label, button) in listOf("Accept" to accept, "Decline" to decline)) {
            assertTrue(
                "$label spans ${button.top}-${button.bottom}, name ${name.top}-${name.bottom}",
                button.top < name.bottom && button.bottom > name.top,
            )
        }
        assertTrue("Accept starts at ${accept.left}, name ends at ${name.right}", accept.left >= name.right)
        assertTrue("Decline starts at ${decline.left}, Accept ends at ${accept.right}", decline.left >= accept.right)
    }

    @Test
    fun `a received invite's long name ends in an ellipsis on one line`() {
        received(long)
        val text = layout(long)
        assertEquals(1, text.lineCount)
        assertTrue(text.isLineEllipsized(0))
    }

    @Test
    fun `Accept and Decline are found and answer by their labels`() {
        var accepted = 0
        var declined = 0
        received("Test Seller", onAccept = { accepted++ }, onDecline = { declined++ })
        rule.onNodeWithContentDescription("Accept").performClick()
        assertEquals(1 to 0, accepted to declined)
        rule.onNodeWithContentDescription("Decline").performClick()
        assertEquals(1 to 1, accepted to declined)
    }

    @Test
    fun `on a narrow screen the name keeps room beside the buttons`() {
        received("Test Seller", width = 280.dp)
        val name = rule.onNodeWithText("Test Seller", useUnmergedTree = true).getBoundsInRoot()
        val decline = rule.onNodeWithContentDescription("Decline").getBoundsInRoot()
        assertEquals(1, layout("Test Seller").lineCount)
        assertTrue("name is ${name.right - name.left} wide", name.right - name.left >= 60.dp)
        assertTrue("Decline ends at ${decline.right}", decline.right <= 280.dp)
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
