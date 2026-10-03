// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.editor.EditorUiState
import org.mochios.calendars.ui.editor.Ending
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Recurrence
import org.mochios.calendars.ui.editor.customised
import org.mochios.calendars.ui.editor.recurred
import org.mochios.calendars.ui.editor.recurrence
import org.mochios.calendars.ui.editor.repeated
import org.mochios.calendars.ui.editor.toggled
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The editor's form as the web editor keeps it: an end before the start held
 * rather than moved, All day keeping each end's day and time, and the repeat
 * choices leaving nothing hidden behind them.
 */
class EditorStateTest {

    private val york = "America/New_York"

    private fun at(day: Int, hour: Int, minute: Int = 0, zone: String = york): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.of(zone)).toEpochSecond()

    private fun midnight(day: Int): Long = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toEpochSecond()

    private fun timed(start: Long, finish: Long) =
        EditorUiState(start = start, finish = finish, zone = Zone(york, york), isLoading = false)

    // ---- an end before the start ----

    @Test
    fun `a timed end before its start is not ordered, and one at the start is`() {
        assertFalse(timed(at(2, 10), at(2, 9)).ordered)
        assertTrue(timed(at(2, 10), at(2, 10)).ordered)
        assertTrue(timed(at(2, 10), at(2, 11)).ordered)
    }

    @Test
    fun `an all-day event may end on its first day but no earlier`() {
        val day = EditorUiState(allday = true, start = midnight(2), finish = midnight(3))
        assertTrue(day.ordered)
        // Its End picked as the day before its Start holds the start itself.
        assertFalse(day.copy(finish = midnight(2)).ordered)
    }

    // ---- all day on and off ----

    @Test
    fun `all day holds each end's own day, even late in the evening west of UTC`() {
        // 22:30 to 23:30 in New York is 02:30 to 03:30 UTC the next day.
        val day = toggled(timed(at(2, 22, 30), at(2, 23, 30)), true, york)
        assertTrue(day.allday)
        assertEquals(midnight(2), day.start)
        assertEquals(midnight(3), day.finish)
        assertEquals(1_350 to 1_410, day.clock)
    }

    @Test
    fun `all day off again puts the times and length back`() {
        val form = timed(at(2, 9, 15), at(2, 11, 45))
        val back = toggled(toggled(form, true, york), false, york)
        assertFalse(back.allday)
        assertEquals(form.start, back.start)
        assertEquals(form.finish, back.finish)
        assertNull(back.clock)
    }

    @Test
    fun `days changed while all day keep the times when it is turned off`() {
        val day = toggled(timed(at(2, 9), at(2, 10)), true, york)
        val moved = day.copy(start = midnight(5), finish = midnight(7))
        val back = toggled(moved, false, york)
        assertEquals(at(5, 9), back.start)
        assertEquals(at(6, 10), back.finish)
    }

    @Test
    fun `an event that opened all day comes back from midnight to the end of its last day`() {
        val opened = EditorUiState(allday = true, start = midnight(2), finish = midnight(4), zone = Zone(york, york))
        val back = toggled(opened, false, york)
        assertEquals(at(2, 0), back.start)
        assertEquals(at(3, 23, 59), back.finish)
    }

    @Test
    fun `turning all day on when it is already on changes nothing`() {
        val day = EditorUiState(allday = true, start = midnight(2), finish = midnight(3))
        assertEquals(day, toggled(day, true, york))
    }

    // ---- repeat ----

    @Test
    fun `a plain repeat leaves nothing of a custom rule behind`() {
        val custom = timed(at(2, 9), at(2, 10)).copy(
            custom = true,
            recurrence = recurrence("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;COUNT=10"),
        )
        val daily = repeated(custom, Frequency.DAILY)
        assertFalse(daily.custom)
        assertEquals("FREQ=DAILY", daily.recurrence.rule())
    }

    @Test
    fun `a plain repeat replaces a rule the settings could not express`() {
        val kept = timed(at(2, 9), at(2, 10)).copy(recurrence = recurrence("FREQ=MONTHLY;BYMONTHDAY=15"))
        assertEquals("FREQ=MONTHLY", repeated(kept, Frequency.MONTHLY).recurrence.rule())
    }

    @Test
    fun `custom opens weekly on an event that did not repeat, and keeps a frequency that did`() {
        val none = customised(timed(at(2, 9), at(2, 10)))
        assertTrue(none.custom)
        assertEquals(Frequency.WEEKLY, none.recurrence.frequency)
        val monthly = customised(timed(at(2, 9), at(2, 10)).copy(recurrence = Recurrence(Frequency.MONTHLY)))
        assertEquals(Frequency.MONTHLY, monthly.recurrence.frequency)
    }

    @Test
    fun `custom on a rule the settings cannot express takes what they show instead`() {
        val kept = timed(at(2, 9), at(2, 10)).copy(recurrence = recurrence("FREQ=MONTHLY;BYDAY=2TU"))
        val opened = customised(kept)
        assertNull(opened.recurrence.rule)
        assertTrue(opened.recurrence.expressible)
    }

    @Test
    fun `an end on a date starts four weeks after the event, on its day in its own zone`() {
        // 22:30 in New York on the 2nd is already the 3rd in UTC.
        val form = timed(at(2, 22, 30), at(2, 23, 30)).copy(recurrence = Recurrence(Frequency.WEEKLY))
        val ended = recurred(form, form.recurrence.copy(ending = Ending.UNTIL), york)
        assertEquals(LocalDate.of(2026, 10, 30), ended.recurrence.until)
        assertEquals("FREQ=WEEKLY;UNTIL=20261031T035959Z", ended.recurrence.rule(york))
    }

    @Test
    fun `a change in the custom settings is what the rule then says`() {
        val form = timed(at(2, 9), at(2, 10)).copy(recurrence = recurrence("FREQ=WEEKLY;BYDAY=MO"))
        val changed = recurred(form, form.recurrence.copy(interval = 3), york)
        assertEquals("FREQ=WEEKLY;INTERVAL=3;BYDAY=MO", changed.recurrence.rule())
    }
}
