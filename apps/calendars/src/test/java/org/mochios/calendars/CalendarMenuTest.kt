// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.ui.components.CalendarAction
import org.mochios.calendars.ui.components.CalendarRow
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A calendar's menu offers Export for every calendar, and Import only for one
 * the user can write in: a subscription and the birthdays calendar are filled
 * from elsewhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val import = context.getString(R.string.calendars_import)
    private val export = context.getString(R.string.calendars_export)

    /** Opens [calendar]'s menu, answering the actions its items ask for. */
    private fun open(calendar: Calendar): List<CalendarAction> {
        val chosen = mutableListOf<CalendarAction>()
        rule.setContent { CalendarRow(calendar = calendar, shown = true, onToggle = {}, onAction = { chosen += it }) }
        rule.onNodeWithContentDescription(context.getString(R.string.calendars_calendar_menu, calendar.name)).performClick()
        return chosen
    }

    @Test
    fun `a calendar of the user's own offers import and export`() {
        val chosen = open(Calendar(id = "own", name = "Work"))
        rule.onNodeWithText(export).assertExists()
        rule.onNodeWithText(import).performClick()
        assertEquals(listOf(CalendarAction.IMPORT), chosen)
    }

    @Test
    fun `a calendar of the user's own offers Settings`() {
        val chosen = open(Calendar(id = "own", name = "Work"))
        rule.onNodeWithText(context.getString(MochiR.string.settings_title)).performClick()
        assertEquals(listOf(CalendarAction.SETTINGS), chosen)
    }

    @Test
    fun `Settings sits just above Delete, after Export`() {
        open(Calendar(id = "own", name = "Work"))
        fun top(text: String) = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        val settings = top(context.getString(MochiR.string.settings_title))
        assertTrue(top(export) < settings)
        assertTrue(settings < top(context.getString(R.string.calendars_delete)))
    }

    @Test
    fun `a subscription has no Settings, as it takes no import`() {
        open(Calendar(id = "subscribed", name = "Holidays", kind = Calendar.KIND_SUBSCRIPTION, readonly = true))
        rule.onNodeWithText(context.getString(MochiR.string.settings_title)).assertDoesNotExist()
    }

    @Test
    fun `a linked calendar is written in, so it takes an import`() {
        open(Calendar(id = "linked", name = "Shared", kind = Calendar.KIND_LINKED))
        rule.onNodeWithText(import).assertExists()
        rule.onNodeWithText(export).assertExists()
    }

    @Test
    fun `a subscription offers export but not import`() {
        val chosen = open(Calendar(id = "subscribed", name = "Holidays", kind = Calendar.KIND_SUBSCRIPTION, readonly = true))
        rule.onNodeWithText(import).assertDoesNotExist()
        rule.onNodeWithText(export).performClick()
        assertEquals(listOf(CalendarAction.EXPORT), chosen)
    }

    @Test
    fun `the birthdays calendar offers export but not import`() {
        open(Calendar(id = "birthdays", name = "Birthdays", kind = Calendar.KIND_BIRTHDAYS, readonly = true))
        rule.onNodeWithText(import).assertDoesNotExist()
        rule.onNodeWithText(export).assertExists()
    }

    @Test
    fun `a subscription can be renamed, as on the web, and the birthdays calendar cannot`() {
        val rename = context.getString(R.string.calendars_rename)
        val chosen = open(Calendar(id = "subscribed", name = "Holidays", kind = Calendar.KIND_SUBSCRIPTION, readonly = true))
        rule.onNodeWithText(rename).performClick()
        assertEquals(listOf(CalendarAction.RENAME), chosen)
    }

    @Test
    fun `the birthdays calendar keeps its own name`() {
        open(Calendar(id = "birthdays", name = "Birthdays", kind = Calendar.KIND_BIRTHDAYS, readonly = true))
        rule.onNodeWithText(context.getString(R.string.calendars_rename)).assertDoesNotExist()
    }

    @Test
    fun `Poll now comes before the calendar address, as on the web`() {
        open(Calendar(id = "subscribed", name = "Holidays", kind = Calendar.KIND_SUBSCRIPTION, readonly = true))
        val poll = rule.onNodeWithText(context.getString(R.string.calendars_poll)).fetchSemanticsNode().boundsInRoot
        val copy = rule.onNodeWithText(context.getString(R.string.calendars_link_copy)).fetchSemanticsNode().boundsInRoot
        assertTrue(poll.top < copy.top)
    }

    @Test
    fun `the address and device questions use the web's words`() {
        assertEquals("Calendar address", context.getString(R.string.calendars_link_title))
        assertEquals("Copy calendar address", context.getString(R.string.calendars_link_copy))
        assertEquals("Delete device?", context.getString(R.string.calendars_device_delete_title))
        assertEquals(
            "The device will no longer be able to sync contacts or calendars.",
            context.getString(R.string.calendars_device_delete_message),
        )
    }
}
