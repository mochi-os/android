// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.calendars.ui.dialogs.DeleteEventDialog
import org.mochios.calendars.ui.dialogs.ScopeDialog
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Recurrence
import org.mochios.calendars.ui.editor.RepeatField
import org.mochios.calendars.ui.editor.recurrence
import org.mochios.calendars.ui.editor.ruleSummary
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The editor's Repeat field and its questions as the web editor has them: the
 * custom settings, a rule they cannot express said in words, and the titles
 * that say what a save, a move or a delete does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepeatFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val format = Format(UserPreferences())
    private val chosen = mutableListOf<Any>()

    private fun show(recurrence: Recurrence, custom: Boolean, weekStartsOn: Int = 1) {
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides Format(UserPreferences(weekStartsOn = weekStartsOn))) {
                Box(Modifier.width(352.dp)) {
                    RepeatField(
                        recurrence = recurrence,
                        custom = custom,
                        start = 1_790_071_200,
                        allday = false,
                        zone = "Europe/London",
                        onRepeat = { chosen += it },
                        onCustom = { chosen += "custom" },
                        onChange = { chosen += it },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun summary(rule: String) = ruleSummary(rule, context.resources, format, Locale.UK)

    // ---- a rule said in words ----

    @Test
    fun `a rule the settings cannot express is said in words, never as RRULE text`() {
        assertEquals(
            "Every 2 weeks, on Tuesday and Thursday, 10 times",
            summary("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;COUNT=10"),
        )
        assertEquals("Every month, on the second Tuesday", summary("FREQ=MONTHLY;BYDAY=2TU"))
        assertEquals("Every month, on the last day of the month", summary("FREQ=MONTHLY;BYMONTHDAY=-1"))
        assertEquals("Every month, on days 1 and 15", summary("FREQ=MONTHLY;BYMONTHDAY=1,15"))
        assertEquals("Every year, in March and September", summary("FREQ=YEARLY;BYMONTH=3,9"))
        assertEquals("Every day, once", summary("FREQ=DAILY;COUNT=1"))
    }

    @Test
    fun `a rule with parts no summary can say is not described`() {
        assertNull(summary("FREQ=MONTHLY;BYSETPOS=-1;BYDAY=FR"))
        assertNull(summary("FREQ=HOURLY"))
        assertNull(summary("FREQ=MONTHLY;BYDAY=6TU"))
    }

    @Test
    fun `a kept rule shows as Custom with its words beneath`() {
        show(recurrence("FREQ=MONTHLY;BYDAY=2TU"), custom = false)
        rule.onNodeWithText(context.getString(R.string.calendars_repeat_custom)).assertExists()
        rule.onNodeWithTag("kept-rule").assertExists()
        rule.onNodeWithText("Every month, on the second Tuesday").assertExists()
        assertEquals(0, rule.onAllNodesWithText("FREQ=MONTHLY;BYDAY=2TU").fetchSemanticsNodes().size)
    }

    // ---- the custom settings ----

    @Test
    fun `the custom settings offer every frequency, an interval to type and three ends`() {
        show(Recurrence(Frequency.WEEKLY), custom = true)
        rule.onNodeWithText(context.getString(R.string.calendars_repeat_frequency)).assertExists()
        rule.onAllNodesWithContentDescription(context.getString(R.string.calendars_repeat_interval))
            .fetchSemanticsNodes().single()
        rule.onNodeWithText(context.getString(R.string.calendars_repeat_end_never)).performClick()
        rule.waitForIdle()
        for (end in listOf(R.string.calendars_repeat_until, R.string.calendars_repeat_after)) {
            rule.onNodeWithText(context.getString(end)).assertExists()
        }
    }

    @Test
    fun `the weekdays run in the user's week order`() {
        show(Recurrence(Frequency.WEEKLY), custom = true, weekStartsOn = 6)
        val names = (0..6).map { offset ->
            // Saturday first: 6, 0, 1 ... 5, Sunday 0.
            val day = (6 + offset) % 7
            DayOfWeek.of(if (day == 0) 7 else day).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
        val lefts = names.map { name -> rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot }
        for (index in 1 until lefts.size) {
            val before = lefts[index - 1]
            val after = lefts[index]
            assertTrue("${names[index]} after ${names[index - 1]}", after.top > before.top || after.left > before.left)
        }
    }

    @Test
    fun `a plain choice is chosen as itself, Custom opens the settings`() {
        show(Recurrence(Frequency.WEEKLY), custom = false)
        rule.onNodeWithText(context.getString(R.string.calendars_repeat_weekly)).performClick()
        rule.onNodeWithText(context.getString(R.string.calendars_repeat_daily)).performClick()
        rule.waitForIdle()
        assertEquals(listOf<Any>(Frequency.DAILY), chosen)
    }

    // ---- the questions ----

    @Test
    fun `a delete asks with the web's title and no claim that it cannot be undone`() {
        rule.setContent { DeleteEventDialog(deleting = false, onDismiss = {}, onConfirm = {}) }
        rule.onNodeWithText(context.getString(R.string.calendars_scope_delete)).assertExists()
        assertEquals("Delete this event", context.getString(R.string.calendars_scope_delete))
        assertEquals(0, rule.onAllNodesWithText("This cannot be undone.").fetchSemanticsNodes().size)
    }

    @Test
    fun `the question says what is done to the event`() {
        rule.setContent {
            ScopeDialog(
                title = context.getString(R.string.calendars_scope_move),
                onDismiss = {},
                onOne = {},
                onAll = {},
            )
        }
        rule.onNodeWithText("Move this event").assertExists()
        assertEquals("Save this event", context.getString(R.string.calendars_scope_save))
        assertEquals("At the time of the event", context.getString(R.string.calendars_reminder_time))
    }
}
