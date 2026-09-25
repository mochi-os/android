// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.sync.CalendarsMapping
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Recurrence
import org.mochios.calendars.ui.editor.minutes
import org.mochios.calendars.ui.editor.recurrence

/**
 * The editor's Repeat and Reminder fields: the `RRULE` a choice builds, the
 * choice a rule reads back as, and the `VALARM` a reminder becomes.
 */
class RecurrenceTest {

    // ---- a rule the settings cannot express ----

    @Test
    fun `a rule read from an event is written back as it was while the settings are untouched`() {
        val second = "FREQ=MONTHLY;BYDAY=2TU"
        assertEquals(second, recurrence(second).rule())
        assertEquals("FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", recurrence("FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU").rule())
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1,15", recurrence("FREQ=MONTHLY;BYMONTHDAY=1,15").rule())
    }

    @Test
    fun `the settings say whether they can express what was read`() {
        assertEquals(true, recurrence("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE").expressible)
        assertEquals(true, recurrence("FREQ=MONTHLY;COUNT=5").expressible)
        assertEquals(false, recurrence("FREQ=MONTHLY;BYDAY=2TU").expressible)
        assertEquals(false, recurrence("FREQ=MONTHLY;BYMONTHDAY=15").expressible)
        assertEquals(false, recurrence("FREQ=MONTHLY;BYDAY=TU").expressible)
        assertEquals(false, recurrence("FREQ=HOURLY").expressible)
    }

    @Test
    fun `a change to the settings drops the kept rule, and no change keeps it`() {
        val read = recurrence("FREQ=MONTHLY;BYDAY=2TU")
        val same = read.revised(read.copy())
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", same.rule())
        val changed = read.revised(read.copy(frequency = Frequency.WEEKLY))
        assertNull(changed.rule)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", changed.rule())
        assertEquals(true, changed.expressible)
    }

    // ---- the rule a choice builds ----

    @Test
    fun `never repeats means no rule at all`() {
        assertNull(Recurrence(Frequency.NEVER).rule())
    }

    @Test
    fun `a plain choice is the frequency alone`() {
        assertEquals("FREQ=DAILY", Recurrence(Frequency.DAILY).rule())
        assertEquals("FREQ=WEEKLY", Recurrence(Frequency.WEEKLY).rule())
        assertEquals("FREQ=MONTHLY", Recurrence(Frequency.MONTHLY).rule())
        assertEquals("FREQ=YEARLY", Recurrence(Frequency.YEARLY).rule())
    }

    @Test
    fun `an interval of one is left out, since that is what a rule means anyway`() {
        assertEquals("FREQ=DAILY", Recurrence(Frequency.DAILY, interval = 1).rule())
        assertEquals("FREQ=DAILY;INTERVAL=3", Recurrence(Frequency.DAILY, interval = 3).rule())
    }

    @Test
    fun `picked weekdays come out in week order, whatever order they were picked in`() {
        val rule = Recurrence(Frequency.CUSTOM, interval = 2, days = setOf(4, 1, 2)).rule()
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TU,TH", rule)
    }

    @Test
    fun `weekdays are only carried by a weekly rule`() {
        assertEquals("FREQ=MONTHLY", Recurrence(Frequency.MONTHLY, days = setOf(1)).rule())
    }

    @Test
    fun `a count ends the series, and wins over an until that should not be there`() {
        assertEquals("FREQ=WEEKLY;COUNT=5", Recurrence(Frequency.WEEKLY, count = 5).rule())
        assertEquals(
            "FREQ=WEEKLY;COUNT=5",
            Recurrence(Frequency.WEEKLY, count = 5, until = 1_790_067_600).rule(),
        )
    }

    @Test
    fun `an until is written in UTC, as an RRULE wants it`() {
        // 2026-09-22 09:00:00 UTC.
        assertEquals(
            "FREQ=DAILY;UNTIL=20260922T090000Z",
            Recurrence(Frequency.DAILY, until = 1_790_067_600).rule(),
        )
    }

    // ---- the choice a rule reads back as ----

    @Test
    fun `no rule reads back as never`() {
        assertEquals(Frequency.NEVER, recurrence(null).frequency)
        assertEquals(Frequency.NEVER, recurrence("").frequency)
    }

    @Test
    fun `a plain rule reads back as the choice that built it`() {
        for (frequency in listOf(Frequency.DAILY, Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY)) {
            val rule = Recurrence(frequency).rule()
            assertEquals(rule, frequency, recurrence(rule).frequency)
        }
    }

    @Test
    fun `a rule with an interval or several weekdays is a custom one`() {
        assertEquals(Frequency.CUSTOM, recurrence("FREQ=WEEKLY;INTERVAL=2").frequency)
        assertEquals(Frequency.CUSTOM, recurrence("FREQ=WEEKLY;BYDAY=MO,WE,FR").frequency)
        assertEquals(setOf(1, 3, 5), recurrence("FREQ=WEEKLY;BYDAY=MO,WE,FR").days)
    }

    @Test
    fun `a rule the editor cannot express stays custom rather than being simplified`() {
        val read = recurrence("FREQ=MONTHLY;BYSETPOS=-1;BYDAY=FR")
        assertEquals(Frequency.CUSTOM, read.frequency)
    }

    @Test
    fun `an ordinal weekday is read as its day`() {
        assertEquals(setOf(5), recurrence("FREQ=MONTHLY;BYDAY=-1FR").days)
    }

    @Test
    fun `a count and an until read back as themselves`() {
        assertEquals(5, recurrence("FREQ=WEEKLY;COUNT=5").count)
        assertEquals(1_790_067_600L, recurrence("FREQ=DAILY;UNTIL=20260922T090000Z").until)
        assertNull(recurrence("FREQ=DAILY").count)
        assertNull(recurrence("FREQ=DAILY").until)
    }

    @Test
    fun `a custom rule survives a round trip`() {
        val rule = "FREQ=WEEKLY;INTERVAL=3;BYDAY=MO,TH;COUNT=8"
        assertEquals(rule, recurrence(rule).rule())
    }

    // ---- reminders ----

    @Test
    fun `a reminder becomes a VALARM that fires before the start`() {
        val alarm = CalendarsMapping.alarm(15, "Stand-up")
        assertEquals("VALARM", alarm.name)
        assertEquals("DISPLAY", alarm.value("ACTION"))
        assertEquals("Stand-up", alarm.value("DESCRIPTION"))
        assertEquals("-PT15M", alarm.value("TRIGGER"))
    }

    @Test
    fun `a trigger reads back as the minutes the editor shows`() {
        assertEquals(15, minutes("-PT15M"))
        assertEquals(0, minutes("PT0S"))
        assertEquals(60, minutes("-PT1H"))
        assertEquals(1440, minutes("-P1D"))
        assertEquals(-10, minutes("PT10M"))
        assertEquals(-1, minutes("20260922T090000Z"))
    }

    @Test
    fun `every reminder the editor offers survives a round trip`() {
        for (chosen in listOf(0, 5, 15, 30, 60, 1440)) {
            val alarm = CalendarsMapping.alarm(chosen, "Stand-up")
            assertEquals(chosen, minutes(alarm.value("TRIGGER")))
        }
    }
}
