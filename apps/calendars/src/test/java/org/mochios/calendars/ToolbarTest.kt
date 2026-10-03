// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.calendars.ui.calendar.CalendarUiState
import org.mochios.calendars.ui.calendar.Toolbar
import org.mochios.calendars.ui.calendar.title
import org.mochios.calendars.ui.router.CalendarsSection
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import org.mochios.android.R as MochiR

/** The calendar's toolbar as the web's: its titles, its date picker and search from any view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rGB-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolbarTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val format = Format(UserPreferences())
    private val day = LocalDate.of(2026, 10, 3)
    private var searched = 0
    private var chosen: LocalDate? = null

    private fun show(view: String) {
        rule.setContent {
            Toolbar(
                state = CalendarUiState(view = view, anchor = day),
                title = "October 2026",
                onMenu = {},
                onToday = {},
                onPrevious = {},
                onNext = {},
                onDate = { chosen = it },
                onSearch = { searched++ },
                onView = {},
                onWorkweek = {},
            )
        }
    }

    @Test
    fun `each view's title is the web's, written as the language writes it`() {
        assertEquals("Saturday 3 October 2026", title(CalendarsSection.DAY, day, day, day, format))
        assertEquals("October 2026", title(CalendarsSection.MONTH, day, day, day, format))
        // The list pages on from its month, so its title is the month too.
        assertEquals("October 2026", title(CalendarsSection.LIST, day, day, day.plusDays(91), format))
        assertEquals(
            format.formatDayRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)),
            title(CalendarsSection.WEEK, day, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4), format),
        )
    }

    @Test
    fun `the title opens a date picker that moves the calendar to the day chosen`() {
        show(CalendarsSection.MONTH)
        rule.onNodeWithText("October 2026").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(MochiR.string.common_save)).performClick()
        rule.waitForIdle()
        assertEquals(day, chosen)
    }

    @Test
    fun `search is offered from every view but the list, which has its own box`() {
        val search = context.getString(R.string.calendars_list_search_events)
        show(CalendarsSection.MONTH)
        rule.onNodeWithContentDescription(search).performClick()
        assertEquals(1, searched)
    }

    @Test
    fun `the list view's toolbar carries no second search`() {
        show(CalendarsSection.LIST)
        val search = context.getString(R.string.calendars_list_search_events)
        assertEquals(0, rule.onAllNodesWithContentDescription(search).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("October 2026").fetchSemanticsNodes().size)
    }
}
