// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Native graphics: legacy mode neither measures text nor applies the strip's turn.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollapsedBoardColumnTest {

    @get:Rule
    val rule = createComposeRule()

    private var expanded = false

    private fun show() {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(40.dp, 400.dp)) {
                    CollapsedBoardColumn(name = "Review", count = "3", onExpand = { expanded = true })
                }
            }
        }
    }

    @Test
    fun `the strip shows the column's name and count`() {
        show()
        rule.onNodeWithText("Review").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun `tapping the strip expands the column, and says so to a screen reader`() {
        show()
        val strip = rule.onNode(hasClickAction())
        assertEquals("Expand", strip.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        strip.performClick()
        assertTrue(expanded)
    }

    @Test
    fun `the name runs down the strip rather than across it`() {
        show()
        // The text's own node: the strip's tap merges its texts into the strip.
        val name = rule.onNodeWithText("Review", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the name is ${name.width} wide and ${name.height} tall", name.height > name.width)
    }
}
