// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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

/** The colour picker's compact form, as the event editor uses it, in a phone's width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorPickerTest {

    @get:Rule
    val rule = createComposeRule()

    private var hex by mutableStateOf("")
    private var cleared = 0

    private fun show(initial: String, collapsible: Boolean = true) {
        hex = initial
        rule.setContent {
            Box(Modifier.width(352.dp)) {
                ColorPicker(
                    hex = hex,
                    onHexChange = { hex = it },
                    collapsible = collapsible,
                    onClear = {
                        cleared++
                        hex = ""
                    },
                )
            }
        }
        rule.waitForIdle()
    }

    /** The hex box, there only while the full picker is open. */
    private fun boxes() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size

    @Test
    fun `a preset colour shows the presets with Custom, and the picker opens on Custom`() {
        show(COLOR_PICKER_PRESETS[2])
        assertEquals(0, boxes())
        rule.onNodeWithText("Custom").performClick()
        rule.waitForIdle()
        assertEquals(1, boxes())
    }

    @Test
    fun `Custom sits above Clear, on the row of presets`() {
        show(COLOR_PICKER_PRESETS[2])
        val custom = rule.onNodeWithText("Custom").fetchSemanticsNode().boundsInRoot
        val clear = rule.onNodeWithText("Clear").fetchSemanticsNode().boundsInRoot
        assertTrue("above: ${custom.bottom} against ${clear.top}", custom.bottom <= clear.top)
    }

    @Test
    fun `Custom stays on the first line of presets when they wrap, level with it`() {
        show(COLOR_PICKER_PRESETS[2])
        val custom = rule.onNodeWithText("Custom").fetchSemanticsNode().boundsInRoot
        val swatches = rule.onAllNodes(hasClickAction() and !hasText("Custom") and !hasText("Clear"))
            .fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(COLOR_PICKER_PRESETS.size, swatches.size)
        val first = swatches.minOf { it.top }
        val lines = swatches.map { it.top }.distinct()
        // A phone's width cannot hold them all beside Custom.
        assertTrue("wrapped onto ${lines.size} lines", lines.size > 1)
        val line = swatches.first { it.top == first }
        assertEquals(line.center.y, custom.center.y, 1f)
        // Custom sits to the right of every swatch, not on a line of its own.
        assertTrue(swatches.all { it.right <= custom.left })
    }

    @Test
    fun `Clear empties the colour and goes once there is none`() {
        show(COLOR_PICKER_PRESETS[2])
        rule.onNodeWithText("Clear").performClick()
        rule.waitForIdle()
        assertEquals(1, cleared)
        assertEquals("", hex)
        assertEquals(0, rule.onAllNodes(hasText("Clear")).fetchSemanticsNodes().size)
    }

    @Test
    fun `a colour that is no preset opens the picker by itself`() {
        show("#123456")
        assertEquals(1, boxes())
    }

    @Test
    fun `the full picker is unchanged where it is not compact`() {
        show(COLOR_PICKER_PRESETS[2], collapsible = false)
        assertEquals(1, boxes())
        assertEquals(0, rule.onAllNodes(hasText("Custom")).fetchSemanticsNodes().size)
    }
}
