// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The top bar's refresh button, and the count of waiting posts on its corner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefreshButtonTest {

    @get:Rule
    val rule = createComposeRule()

    init {
        // Capture through the hardware renderer, as a phone draws.
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware")
    }

    private var taps = 0
    private var primary = Color.Unspecified
    private var error = Color.Unspecified

    private val badge = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
    private val spoken = SemanticsProperties.StateDescription

    private fun show(count: Int) {
        rule.setContent {
            primary = MaterialTheme.colorScheme.primary
            error = MaterialTheme.colorScheme.error
            RefreshButton(count = count, label = "$count new posts", onClick = { taps++ })
        }
        rule.waitForIdle()
    }

    @Test
    fun `with nothing waiting there is no badge and no count to speak`() {
        show(0)
        rule.onAllNodes(badge, useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("Refresh").assert(SemanticsMatcher.keyNotDefined(spoken))
    }

    @Test
    fun `the badge shows the posts waiting, and the button speaks them`() {
        show(7)
        rule.onNodeWithText("7", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Refresh").assert(SemanticsMatcher.expectValue(spoken, "7 new posts"))
    }

    @Test
    fun `past two digits the badge stops counting, though the button still gives the number`() {
        show(123)
        rule.onNodeWithText("99+", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Refresh").assert(SemanticsMatcher.expectValue(spoken, "123 new posts"))
    }

    @Test
    fun `a screen reader hears the count from the button, not as a bare number beside it`() {
        show(7)
        rule.onAllNodes(badge, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `the badge is the primary colour, where the bell's is the error colour`() {
        show(7)
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val drawn = HashSet<Color>()
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                drawn.add(pixels[x, y])
            }
        }
        assertNotEquals(primary, error)
        assertTrue("no primary among ${drawn.size} colours", primary in drawn)
        assertFalse(error in drawn)
    }

    @Test
    fun `a tap refreshes, on the badge as on the icon`() {
        show(7)
        rule.onNodeWithContentDescription("Refresh").performClick()
        assertEquals(1, taps)
        rule.onNode(badge, useUnmergedTree = true).performClick()
        assertEquals(2, taps)
    }
}
