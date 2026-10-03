// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.ui.editor.CalendarField
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The editor's calendar field: no label of its own, only the field and its list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val name = RuntimeEnvironment.getApplication().getString(R.string.calendars_event_calendar)
    private var chosen = ""

    private fun show() {
        rule.setContent {
            CalendarField(
                calendars = listOf(Calendar(id = "c1", name = "Home"), Calendar(id = "c2", name = "Work")),
                selected = "c1",
                onSelect = { chosen = it },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `the field shows the calendar with no label above it, and is named for screen readers`() {
        show()
        assertEquals(0, rule.onAllNodesWithText(name).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription(name).assertExists()
        rule.onNodeWithText("Home").assertExists()
    }

    @Test
    fun `the list offers every calendar and picks one`() {
        show()
        rule.onNodeWithText("Home").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Work").performClick()
        rule.waitForIdle()
        assertEquals("c2", chosen)
    }
}
