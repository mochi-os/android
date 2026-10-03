// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.calendars.ui.editor.EventColour
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The editor's colour field, as the web's: presets with Custom, and a text box for a colour no picker shows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventColourTest {

    @get:Rule
    val rule = createComposeRule()

    private val value = RuntimeEnvironment.getApplication().getString(R.string.calendars_event_colour_value)
    private var colour by mutableStateOf("")

    private fun show(initial: String) {
        colour = initial
        rule.setContent {
            Box(Modifier.width(352.dp)) {
                EventColour(colour = colour, enabled = true, onColour = { colour = it })
            }
        }
        rule.waitForIdle()
    }

    private fun boxes() = rule.onAllNodes(hasContentDescription(value)).fetchSemanticsNodes().size

    @Test
    fun `a colour written as a name is kept in a text box that edits it`() {
        show("turquoise")
        assertEquals(1, boxes())
        rule.onNodeWithContentDescription(value).performTextReplacement("navy")
        rule.waitForIdle()
        assertEquals("navy", colour)
    }

    @Test
    fun `a hex colour has no text box of its own`() {
        show("#f87171")
        assertEquals(0, boxes())
    }

    @Test
    fun `Clear empties the colour`() {
        show("#f87171")
        rule.onNodeWithText("Clear").performClick()
        rule.waitForIdle()
        assertEquals("", colour)
    }
}
