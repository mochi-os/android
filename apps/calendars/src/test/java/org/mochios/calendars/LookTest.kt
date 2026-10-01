// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.mochios.calendars.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.model.tint
import org.mochios.calendars.ui.calendar.CARRIED
import org.mochios.calendars.ui.calendar.DIM
import org.mochios.calendars.ui.calendar.Look
import org.mochios.calendars.ui.calendar.band
import org.mochios.calendars.ui.calendar.Mark
import org.mochios.calendars.ui.calendar.days
import org.mochios.calendars.ui.calendar.interval
import org.mochios.calendars.ui.calendar.last
import org.mochios.calendars.ui.calendar.look
import org.mochios.calendars.ui.calendar.marks
import org.mochios.calendars.ui.calendar.opacity
import org.mochios.calendars.ui.calendar.same
import org.mochios.calendars.ui.calendar.stack
import org.mochios.calendars.ui.calendar.status
import org.mochios.calendars.ui.calendar.past
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * How the views draw an occurrence: faded once it is over, in a month or
 * multiweek cell as a bar or as a two-line entry, with its start time or
 * without, with its marks between its title and its time on one line or
 * after the time on a stacked second line, which of a cell's groups stacks
 * on top and how tall it stands above the other, and what its status, title,
 * span, colour and selection make of it.
 */
class LookTest {

    private val LONDON: ZoneId = ZoneId.of("Europe/London")
    private val MONDAY = LocalDate.of(2026, 9, 21)
    private val TUESDAY = LocalDate.of(2026, 9, 22)
    private val WEDNESDAY = LocalDate.of(2026, 9, 23)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute, 0, 0, LONDON).toEpochSecond()

    private fun timed(start: Long, finish: Long) = Instance(event = "e", start = start, finish = finish)

    /** An all-day occurrence as the server sends it: its date, from the user's midnight, whole days long. */
    private fun allday(date: LocalDate, days: Long): Instance {
        val midnight = date.atStartOfDay(LONDON).toEpochSecond()
        return Instance(event = "e", allday = true, date = date.toString(), start = midnight, finish = midnight + days * 86400)
    }

    private fun lastOf(instance: Instance): LocalDate =
        last(LocalDate.parse(instance.date), instance.start, instance.finish)

    // ---- past ----

    @Test
    fun `a timed occurrence is over once its finish has passed`() {
        val meeting = timed(at(TUESDAY, 10), at(TUESDAY, 11))
        assertTrue(past(meeting, TUESDAY, at(TUESDAY, 12), TUESDAY))
    }

    @Test
    fun `a timed occurrence under way is not over`() {
        val meeting = timed(at(TUESDAY, 10), at(TUESDAY, 11))
        assertFalse(past(meeting, TUESDAY, at(TUESDAY, 10, 30), TUESDAY))
    }

    @Test
    fun `a timed occurrence finishing this moment is not yet over`() {
        val meeting = timed(at(TUESDAY, 10), at(TUESDAY, 11))
        assertFalse(past(meeting, TUESDAY, at(TUESDAY, 11), TUESDAY))
    }

    @Test
    fun `a timed occurrence that has not started is not over`() {
        val meeting = timed(at(WEDNESDAY, 10), at(WEDNESDAY, 11))
        assertFalse(past(meeting, WEDNESDAY, at(TUESDAY, 12), TUESDAY))
    }

    @Test
    fun `a timed occurrence with no length is over once its start has passed`() {
        val reminder = timed(at(TUESDAY, 10), at(TUESDAY, 10))
        assertTrue(past(reminder, TUESDAY, at(TUESDAY, 10, 1), TUESDAY))
        assertFalse(past(reminder, TUESDAY, at(TUESDAY, 9, 59), TUESDAY))
    }

    @Test
    fun `a timed occurrence ending before it starts is judged by its start`() {
        // Across zones a finish can read before the start; the start still has to pass.
        val flight = timed(at(TUESDAY, 10), at(TUESDAY, 8))
        assertFalse(past(flight, TUESDAY, at(TUESDAY, 9), TUESDAY))
        assertTrue(past(flight, TUESDAY, at(TUESDAY, 11), TUESDAY))
    }

    @Test
    fun `an all-day occurrence is over once its last day is before today`() {
        val holiday = allday(MONDAY, 1)
        assertTrue(past(holiday, lastOf(holiday), at(TUESDAY, 0, 1), TUESDAY))
    }

    @Test
    fun `an all-day occurrence is not over during its own day`() {
        // Late in the day its finish instant is near, but the day is not yet over.
        val holiday = allday(TUESDAY, 1)
        assertFalse(past(holiday, lastOf(holiday), at(TUESDAY, 23, 59), TUESDAY))
    }

    @Test
    fun `an all-day occurrence is judged by its day rather than its finish instant`() {
        // Expanded by a server in another zone, the finish instant can be
        // before now while the date is still today.
        val holiday = Instance(event = "e", allday = true, date = TUESDAY.toString(), start = at(MONDAY, 10), finish = at(TUESDAY, 10))
        assertFalse(past(holiday, lastOf(holiday), at(TUESDAY, 12), TUESDAY))
    }

    @Test
    fun `a multi-day all-day occurrence is not over until its last day has gone`() {
        val trip = allday(MONDAY, 2)
        assertEquals(TUESDAY, lastOf(trip))
        assertFalse(past(trip, lastOf(trip), at(TUESDAY, 12), TUESDAY))
        assertTrue(past(trip, lastOf(trip), at(WEDNESDAY, 0), WEDNESDAY))
    }

    // ---- look ----

    @Test
    fun `an all-day occurrence is a bar with no time`() {
        assertEquals(Look(bar = true, time = false), look(allday(TUESDAY, 1), TUESDAY, TUESDAY, TUESDAY))
    }

    @Test
    fun `a multi-day all-day occurrence has no time on any day`() {
        val trip = allday(MONDAY, 3)
        for (day in listOf(MONDAY, TUESDAY, WEDNESDAY)) {
            assertEquals(Look(bar = true, time = false), look(trip, MONDAY, WEDNESDAY, day))
        }
    }

    @Test
    fun `a timed occurrence within a day is a line with its time`() {
        val meeting = timed(at(TUESDAY, 10), at(TUESDAY, 11))
        val (first, last) = days(meeting, LONDON, false)
        assertEquals(Look(bar = false, time = true), look(meeting, first, last, TUESDAY))
    }

    @Test
    fun `a timed occurrence crossing midnight is a bar with its time on its first day only`() {
        val overnight = timed(at(MONDAY, 22), at(TUESDAY, 2))
        val (first, last) = days(overnight, LONDON, false)
        assertEquals(MONDAY, first)
        assertEquals(TUESDAY, last)
        assertEquals(Look(bar = true, time = true), look(overnight, first, last, MONDAY))
        assertEquals(Look(bar = true, time = false), look(overnight, first, last, TUESDAY))
    }

    @Test
    fun `a timed occurrence ending on the stroke of midnight stays a line`() {
        // Its finish belongs to the day before, so it covers one day only.
        val evening = timed(at(MONDAY, 23), at(TUESDAY, 0))
        val (first, last) = days(evening, LONDON, false)
        assertEquals(Look(bar = false, time = true), look(evening, first, last, MONDAY))
    }

    // ---- marks ----

    @Test
    fun `an occurrence with nothing to say carries no marks`() {
        assertEquals(emptyList<Mark>(), marks(Instance(event = "e")))
    }

    @Test
    fun `a reminder is a bell`() {
        assertEquals(listOf(Mark.REMINDER), marks(Instance(event = "e", alarm = true)))
    }

    @Test
    fun `one of a series carries the repeat glyph`() {
        assertEquals(listOf(Mark.REPEAT), marks(Instance(event = "e", recurring = true)))
    }

    @Test
    fun `a changed occurrence carries its own glyph in place of the repeat`() {
        assertEquals(listOf(Mark.EXCEPTION), marks(Instance(event = "e", recurring = true, exception = true)))
    }

    @Test
    fun `the bell comes before the repeat glyph`() {
        assertEquals(listOf(Mark.REMINDER, Mark.REPEAT), marks(Instance(event = "e", recurring = true, alarm = true)))
    }

    @Test
    fun `a block ending before it starts says so after the others`() {
        val flight = Instance(event = "e", recurring = true, exception = true, alarm = true)
        assertEquals(listOf(Mark.REMINDER, Mark.EXCEPTION, Mark.BACKWARDS), marks(flight, backwards = true))
        assertEquals(listOf(Mark.BACKWARDS), marks(Instance(event = "e"), backwards = true))
    }

    @Test
    fun `stacked beneath the title the bell comes after the repeat glyph`() {
        assertEquals(
            listOf(Mark.REPEAT, Mark.REMINDER),
            marks(Instance(event = "e", recurring = true, alarm = true), stacked = true),
        )
    }

    @Test
    fun `stacked the changed and backwards glyphs come before the bell`() {
        val flight = Instance(event = "e", recurring = true, exception = true, alarm = true)
        assertEquals(listOf(Mark.EXCEPTION, Mark.BACKWARDS, Mark.REMINDER), marks(flight, backwards = true, stacked = true))
        assertEquals(listOf(Mark.REMINDER), marks(Instance(event = "e", alarm = true), stacked = true))
    }

    // ---- band ----

    @Test
    fun `a cell with no bars holds none`() {
        assertNull(band(0, 16f, 2f, 78f, 26f, below = true))
    }

    @Test
    fun `bars that leave room for a timed entry take their own height`() {
        // Two bars, 34, and a gap and an entry beneath, 28: 62 of 78.
        assertNull(band(2, 16f, 2f, 78f, 26f, below = true))
        // Four bars need 70, which a cell of 78 holds when there is nothing timed.
        assertNull(band(4, 16f, 2f, 78f, 26f, below = false))
    }

    @Test
    fun `bars that would crowd out the timed entries are held to what is left`() {
        // Four bars need 70; 78 less a gap and an entry leaves 50.
        assertEquals(50f, band(4, 16f, 2f, 78f, 26f, below = true))
    }

    @Test
    fun `bars that do not fit a cell with nothing timed are held to the cell`() {
        assertEquals(78f, band(6, 16f, 2f, 78f, 26f, below = false))
    }

    @Test
    fun `held bars always keep one bar showing`() {
        assertEquals(16f, band(3, 16f, 2f, 30f, 26f, below = true))
    }

    @Test
    fun `timed entries stacked above the bars are held to leave room for one bar`() {
        // Three timed entries need 82; 78 less a gap and a bar leaves 60.
        assertEquals(60f, band(3, 26f, 2f, 78f, 16f, below = true))
    }

    // ---- stack ----

    @Test
    fun `the bars stack above the timed entries unless all-day events go last`() {
        assertEquals(listOf("bars", "lines"), stack("bars", "lines", "first"))
        assertEquals(listOf("lines", "bars"), stack("bars", "lines", "last"))
        assertEquals(listOf("bars", "lines"), stack("bars", "lines", ""))
    }

    // ---- status ----

    @Test
    fun `a cancelled or tentative status is read whatever its case`() {
        assertTrue(Instance(status = "CANCELLED").cancelled)
        assertTrue(Instance(status = "cancelled").cancelled)
        assertTrue(Instance(status = " TENTATIVE ").tentative)
        assertFalse(Instance(status = "TENTATIVE").cancelled)
    }

    @Test
    fun `a confirmed occurrence or one with no status is neither`() {
        for (value in listOf("CONFIRMED", "")) {
            assertFalse(Instance(status = value).cancelled)
            assertFalse(Instance(status = value).tentative)
            assertNull(status(Instance(status = value)))
        }
    }

    @Test
    fun `the summary says Cancelled or Tentative`() {
        assertEquals(R.string.calendars_status_cancelled, status(Instance(status = "CANCELLED")))
        assertEquals(R.string.calendars_status_tentative, status(Instance(status = "TENTATIVE")))
    }

    @Test
    fun `a cancelled occurrence is dimmed as a past one is, and a carried one fades further`() {
        assertEquals(1f, opacity(carried = false, over = false, cancelled = false))
        assertEquals(DIM, opacity(carried = false, over = false, cancelled = true))
        assertEquals(DIM, opacity(carried = false, over = true, cancelled = false))
        assertEquals(CARRIED, opacity(carried = true, over = true, cancelled = true))
    }

    // ---- title ----

    @Test
    fun `an occurrence with a blank title has none`() {
        assertTrue(Instance(summary = "").untitled)
        assertTrue(Instance(summary = "   ").untitled)
        assertFalse(Instance(summary = "Lunch").untitled)
    }

    // ---- span ----

    private val clock = { at: Long, zone: String? -> "$at@${zone ?: "user"}" }
    private val join = { start: String, finish: String -> "$start to $finish" }

    @Test
    fun `a timed occurrence reads from its start to its finish`() {
        val meeting = Instance(start = 100, finish = 200)
        assertEquals("100@user to 200@user", interval(meeting, false, clock, join))
    }

    @Test
    fun `with events in their own zones each end reads in its own`() {
        val flight = Instance(start = 100, finish = 200, zone = Zone(start = "Europe/London", finish = "America/New_York"))
        assertEquals("100@Europe/London to 200@America/New_York", interval(flight, true, clock, join))
        assertEquals("100@user to 200@user", interval(flight, false, clock, join))
    }

    @Test
    fun `an occurrence with no length reads its start alone`() {
        assertEquals("100@user", interval(Instance(start = 100, finish = 100), false, clock, join))
    }

    // ---- colour ----

    @Test
    fun `an occurrence with a colour of its own is drawn in it`() {
        assertEquals("#1a2b3c", tint("#1a2b3c", "#ffffff"))
        assertEquals("#1A2B3C", tint(" #1A2B3C ", "#ffffff"))
    }

    @Test
    fun `one with no colour or one that will not parse takes its calendar's`() {
        assertEquals("#abcdef", tint(null, "#abcdef"))
        assertEquals("#abcdef", tint("", "#abcdef"))
        assertEquals("#abcdef", tint("red", "#abcdef"))
        assertEquals("#abcdef", tint("#12345", "#abcdef"))
        assertEquals("", tint("nonsense", null))
    }

    // ---- selection ----

    @Test
    fun `the open occurrence is the same event at the same start`() {
        val open = Instance(event = "e", start = 100)
        assertTrue(same(Instance(event = "e", start = 100), open))
        assertFalse(same(Instance(event = "e", start = 200), open))
        assertFalse(same(Instance(event = "f", start = 100), open))
        assertFalse(same(open, null))
    }
}
