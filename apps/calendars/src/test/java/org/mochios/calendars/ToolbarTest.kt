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
import org.mochios.android.R as MochiR
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.ui.calendar.CalendarUiState
import org.mochios.calendars.ui.calendar.Toolbar
import org.mochios.calendars.ui.router.CalendarsSection
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The calendar's toolbar: a month title that opens the date panel, then
 * today, a new event, search from every view, as the web's box is, and the
 * view switcher, in the web's order, with no arrows, the views paging by a
 * swipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rGB-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolbarTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 10, 3)
    private var titled = 0
    private var searched = 0
    private var created = 0

    private var menus = 0
    private var left = 0

    private fun show(view: String, searching: Boolean = false, alone: Calendar? = null) {
        rule.setContent {
            Toolbar(
                state = CalendarUiState(view = view, anchor = day),
                title = "Oct 2026",
                picking = false,
                onMenu = { menus++ },
                onTitle = { titled++ },
                onToday = {},
                onView = {},
                onWorkweek = {},
                onCreate = { created++ },
                searching = searching,
                onSearch = { searched++ },
                alone = alone,
                onLeave = { left++ },
            )
        }
    }

    @Test
    fun `a calendar on its own is named over the title, with Back in place of the drawer`() {
        show(CalendarsSection.MONTH, alone = Calendar(id = "c2", name = "Work"))
        rule.onNodeWithText("Work").assertExists()
        rule.onNodeWithText("Oct 2026").assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.calendars_drawer_open)).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_back)).performClick()
        assertEquals(1, left)
        assertEquals(0, menus)
    }

    @Test
    fun `the whole calendar has the drawer's button`() {
        show(CalendarsSection.MONTH)
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_back)).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.calendars_drawer_open)).performClick()
        assertEquals(1, menus)
    }

    @Test
    fun `a new event is offered in the bar`() {
        show(CalendarsSection.WEEK)
        rule.onNodeWithContentDescription(context.getString(R.string.calendars_event_new)).performClick()
        assertEquals(1, created)
    }

    @Test
    fun `the bar's buttons run as the web's do, today, a new event, search, then the view`() {
        show(CalendarsSection.MONTH)
        val order = listOf(
            R.string.calendars_today,
            R.string.calendars_event_new,
            R.string.calendars_list_search,
            R.string.calendars_view,
        ).map { label ->
            rule.onNodeWithContentDescription(context.getString(label)).fetchSemanticsNode().boundsInRoot.left
        }
        assertEquals(order.sorted(), order)
        assertEquals(order.size, order.toSet().size)
    }

    @Test
    fun `a calendar on its own offers a new event too`() {
        show(CalendarsSection.MONTH, alone = Calendar(id = "c2", name = "Work"))
        rule.onNodeWithContentDescription(context.getString(R.string.calendars_event_new)).performClick()
        assertEquals(1, created)
    }

    @Test
    fun `the title opens the date panel`() {
        show(CalendarsSection.MONTH)
        rule.onNodeWithText("Oct 2026").performClick()
        assertEquals(1, titled)
    }

    @Test
    fun `there are no previous and next arrows`() {
        show(CalendarsSection.WEEK)
        for (label in listOf(R.string.calendars_previous, R.string.calendars_next)) {
            val found = rule.onAllNodesWithContentDescription(context.getString(label))
            assertEquals(0, found.fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `search is offered in the list view`() {
        show(CalendarsSection.LIST)
        rule.onNodeWithContentDescription(context.getString(R.string.calendars_list_search)).performClick()
        assertEquals(1, searched)
    }

    @Test
    fun `the other views offer search too`() {
        show(CalendarsSection.MONTH)
        rule.onNodeWithContentDescription(context.getString(R.string.calendars_list_search)).performClick()
        assertEquals(1, searched)
    }

    @Test
    fun `while searching the bar is a search field in place of the title`() {
        show(CalendarsSection.LIST, searching = true)
        assertEquals(0, rule.onAllNodesWithText("Oct 2026").fetchSemanticsNodes().size)
        val placeholder = context.getString(R.string.calendars_list_search_events)
        assertEquals(1, rule.onAllNodesWithText(placeholder).fetchSemanticsNodes().size)
    }
}
