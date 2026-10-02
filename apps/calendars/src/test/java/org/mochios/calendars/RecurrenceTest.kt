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
import org.mochios.android.sync.CalendarsMapping
import org.mochios.android.sync.EventComponent
import org.mochios.android.sync.property
import org.mochios.calendars.ui.editor.Ending
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Recurrence
import org.mochios.calendars.ui.editor.alarmMinutes
import org.mochios.calendars.ui.editor.defaultReminders
import org.mochios.calendars.ui.editor.nextReminder
import org.mochios.calendars.ui.editor.reminderLeads
import org.mochios.calendars.ui.editor.minutes
import org.mochios.calendars.ui.editor.recurrence
import java.time.LocalDate

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
        assertEquals(false, recurrence("FREQ=DAILY;COUNT=3;UNTIL=20261001").expressible)
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
        val rule = Recurrence(Frequency.WEEKLY, interval = 2, days = setOf(4, 1, 2)).rule()
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TU,TH", rule)
    }

    @Test
    fun `weekdays are only carried by a weekly rule`() {
        assertEquals("FREQ=MONTHLY", Recurrence(Frequency.MONTHLY, days = setOf(1)).rule())
    }

    @Test
    fun `a count ends the series only when the end is a count`() {
        assertEquals("FREQ=WEEKLY;COUNT=5", Recurrence(Frequency.WEEKLY, ending = Ending.COUNT, count = 5).rule())
        // The count field keeps its number while another end is chosen.
        assertEquals("FREQ=WEEKLY", Recurrence(Frequency.WEEKLY, count = 5).rule())
        val day = LocalDate.of(2026, 9, 22)
        assertEquals(
            "FREQ=WEEKLY;COUNT=5",
            Recurrence(Frequency.WEEKLY, ending = Ending.COUNT, count = 5, until = day).rule(),
        )
    }

    @Test
    fun `an end on a date takes in the whole of its day, in UTC, as an RRULE wants it`() {
        val day = LocalDate.of(2026, 9, 22)
        // The last second of 22 September in London, an hour ahead of UTC.
        assertEquals(
            "FREQ=DAILY;UNTIL=20260922T225959Z",
            Recurrence(Frequency.DAILY, ending = Ending.UNTIL, until = day).rule("Europe/London"),
        )
        assertEquals(
            "FREQ=DAILY;UNTIL=20260922T235959Z",
            Recurrence(Frequency.DAILY, ending = Ending.UNTIL, until = day).rule(),
        )
    }

    @Test
    fun `an all-day series ends on a date, as its start is one`() {
        val day = LocalDate.of(2026, 9, 22)
        assertEquals(
            "FREQ=DAILY;UNTIL=20260922",
            Recurrence(Frequency.DAILY, ending = Ending.UNTIL, until = day).rule("Europe/London", allday = true),
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
            val read = recurrence(rule)
            assertEquals(rule, frequency, read.frequency)
            assertTrue(rule, read.plain)
        }
    }

    @Test
    fun `a rule with an interval, weekdays or an end needs the custom settings`() {
        // Every one of these would hide part of itself behind a plain choice.
        for (rule in listOf(
            "FREQ=DAILY;INTERVAL=2",
            "FREQ=WEEKLY;BYDAY=TU",
            "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            "FREQ=MONTHLY;COUNT=10",
            "FREQ=YEARLY;UNTIL=20301231",
        )) {
            assertFalse(rule, recurrence(rule).plain)
        }
        assertEquals(setOf(1, 3, 5), recurrence("FREQ=WEEKLY;BYDAY=MO,WE,FR").days)
        assertEquals(Frequency.DAILY, recurrence("FREQ=DAILY;INTERVAL=2").frequency)
        assertEquals(Frequency.MONTHLY, recurrence("FREQ=MONTHLY;COUNT=10").frequency)
    }

    @Test
    fun `an ordinal weekday is read as its day`() {
        assertEquals(setOf(5), recurrence("FREQ=MONTHLY;BYDAY=-1FR").days)
    }

    @Test
    fun `a count and an until read back as themselves, the until as its day in the start's zone`() {
        val counted = recurrence("FREQ=WEEKLY;COUNT=5")
        assertEquals(Ending.COUNT, counted.ending)
        assertEquals(5, counted.count)
        // 01:30 UTC on the 23rd is still the 22nd in New York.
        val until = recurrence("FREQ=DAILY;UNTIL=20260923T013000Z", "America/New_York")
        assertEquals(Ending.UNTIL, until.ending)
        assertEquals(LocalDate.of(2026, 9, 22), until.until)
        assertEquals(LocalDate.of(2026, 9, 22), recurrence("FREQ=DAILY;UNTIL=20260922").until)
        assertEquals(Ending.NEVER, recurrence("FREQ=DAILY").ending)
        assertNull(recurrence("FREQ=DAILY").until)
    }

    @Test
    fun `a custom rule survives a round trip`() {
        val rule = "FREQ=WEEKLY;INTERVAL=3;BYDAY=MO,TH;COUNT=8"
        assertEquals(rule, recurrence(rule).rule())
        val read = recurrence(rule).copy(rule = null)
        assertEquals(rule, read.rule())
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
    fun `only the alarms the reminder setting can say are read`() {
        fun alarm(trigger: String, parameter: String? = null, argument: String? = null) =
            EventComponent("VALARM", listOf(property("TRIGGER", trigger, parameter, argument)))
        assertEquals(15, alarmMinutes(alarm("-PT15M")))
        assertEquals(0, alarmMinutes(alarm("PT0S")))
        assertNull("relative to the end", alarmMinutes(alarm("-PT15M", "RELATED", "END")))
        assertNull("at a fixed time", alarmMinutes(alarm("20260922T090000Z", "VALUE", "DATE-TIME")))
        assertNull("after the start", alarmMinutes(alarm("PT10M")))
        assertNull("no trigger", alarmMinutes(EventComponent("VALARM")))
    }

    @Test
    fun `a reminder set elsewhere to a time not offered joins the choices in its place`() {
        assertEquals(listOf(0, 5, 15, 30, 60, 1440), reminderLeads(15))
        assertEquals(listOf(0, 5, 10, 15, 30, 60, 1440), reminderLeads(10))
        assertEquals(listOf(0, 5, 15, 30, 60, 1440, 2880), reminderLeads(2880))
    }

    @Test
    fun `reminders added by hand start at the time of the event and each is longer`() {
        val added = mutableListOf<Int>()
        repeat(6) { added += nextReminder(added) }
        assertEquals(listOf(0, 5, 15, 30, 60, 1440), added)
    }

    @Test
    fun `beside a default reminder, the one added is the shortest the event lacks`() {
        assertEquals(0, nextReminder(listOf(15)))
        assertEquals(5, nextReminder(listOf(15, 0)))
        assertEquals(30, nextReminder(listOf(15, 0, 5)))
    }

    @Test
    fun `with every reminder taken, the longest is offered again`() {
        assertEquals(1440, nextReminder(listOf(0, 5, 15, 30, 60, 1440)))
    }

    @Test
    fun `a new event opens with the default reminder, or none`() {
        assertEquals(listOf(15), defaultReminders(15))
        assertEquals(emptyList<Int>(), defaultReminders(-1))
    }

    @Test
    fun `every reminder the editor offers survives a round trip`() {
        for (chosen in listOf(0, 5, 15, 30, 60, 1440)) {
            val alarm = CalendarsMapping.alarm(chosen, "Stand-up")
            assertEquals(chosen, minutes(alarm.value("TRIGGER")))
        }
    }
}
