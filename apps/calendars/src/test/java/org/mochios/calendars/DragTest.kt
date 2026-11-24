// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.calendar.Cut
import org.mochios.calendars.ui.calendar.Dwell
import org.mochios.calendars.ui.calendar.PAGE_HOLD
import org.mochios.calendars.ui.calendar.Preview
import org.mochios.calendars.ui.calendar.clocked
import org.mochios.calendars.ui.calendar.edge
import org.mochios.calendars.ui.calendar.whole
import org.mochios.calendars.ui.editor.EventForm
import org.mochios.calendars.ui.calendar.at
import org.mochios.calendars.ui.calendar.dropped
import org.mochios.calendars.ui.calendar.moved
import org.mochios.calendars.ui.calendar.resized
import org.mochios.calendars.ui.calendar.snap
import org.mochios.calendars.ui.calendar.takes
import org.mochios.calendars.ui.editor.Scope
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Where a dragged block lands: on the quarter hour, inside the day, and at
 * the instants that reads as by the clock the block is placed by.
 */
class DragTest {

    private val LONDON: ZoneId = ZoneId.of("Europe/London")
    private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

    private fun clock(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toEpochSecond()

    private val MONDAY = LocalDate.of(2026, 9, 21)
    private val TUESDAY = LocalDate.of(2026, 9, 22)

    /** 10:00 to 11:30 London on the Monday. */
    private val meeting = Instance(
        start = clock(LONDON, 2026, 9, 21, 10),
        finish = clock(LONDON, 2026, 9, 21, 11, 30),
        zone = Zone("Europe/London", "Europe/London"),
    )

    // ---- the snap ----

    @Test
    fun `a time snaps to the nearest quarter hour`() {
        assertEquals(10f, snap(10f + 7 / 60f), 0.0001f)
        assertEquals(10.25f, snap(10f + 8 / 60f), 0.0001f)
        assertEquals(10.5f, snap(10.49f), 0.0001f)
    }

    @Test
    fun `a moved block keeps its length and lands on the grid`() {
        // Ten o'clock carried 47 minutes down: 10:45, still an hour and a half.
        assertEquals(Cut(10.75f, 12.25f), moved(Cut(10f, 11.5f), 47 / 60f))
        assertEquals(Cut(9.5f, 11f), moved(Cut(10f, 11.5f), -0.5f))
    }

    @Test
    fun `a moved block stops at the day's ends`() {
        assertEquals(Cut(22.5f, 24f), moved(Cut(10f, 11.5f), 13f))
        assertEquals(Cut(0f, 1.5f), moved(Cut(10f, 11.5f), -12f))
    }

    @Test
    fun `a resized block ends on the grid and no nearer the start than a quarter hour`() {
        assertEquals(Cut(10f, 12.25f), resized(Cut(10f, 11.5f), 12.3f))
        assertEquals(Cut(10f, 10.25f), resized(Cut(10f, 11.5f), 9f))
        assertEquals(Cut(10f, 24f), resized(Cut(10f, 11.5f), 30f))
    }

    // ---- the instants ----

    @Test
    fun `hours from midnight read by the clock in the zone`() {
        assertEquals(clock(LONDON, 2026, 9, 21, 10, 45), at(MONDAY, 10.75f, LONDON))
        assertEquals(clock(LONDON, 2026, 9, 21, 10, 20), at(MONDAY, 620 / 60f, LONDON))
        assertEquals(TUESDAY.atStartOfDay(LONDON).toEpochSecond(), at(MONDAY, 24f, LONDON))
    }

    @Test
    fun `a move carries the occurrence by as far as the block moved, to another day too`() {
        val (start, finish) = dropped(
            meeting, MONDAY, Cut(10f, 11.5f), TUESDAY, Cut(11.25f, 12.75f), LONDON, LONDON, resize = false,
        )
        assertEquals(clock(LONDON, 2026, 9, 22, 11, 15), start)
        assertEquals(clock(LONDON, 2026, 9, 22, 12, 45), finish)
    }

    @Test
    fun `a move of a block standing for part of an occurrence moves the whole occurrence`() {
        // Sunday 23:00 to Monday 01:00 stands on the Monday as 00:00 to 01:00.
        val late = meeting.copy(
            start = clock(LONDON, 2026, 9, 20, 23),
            finish = clock(LONDON, 2026, 9, 21, 1),
        )
        val (start, finish) = dropped(late, MONDAY, Cut(0f, 1f), MONDAY, Cut(2f, 3f), LONDON, LONDON, resize = false)
        assertEquals(clock(LONDON, 2026, 9, 21, 1), start)
        assertEquals(clock(LONDON, 2026, 9, 21, 3), finish)
    }

    @Test
    fun `a resize leaves the start alone and ends where the bottom is`() {
        val (start, finish) = dropped(meeting, MONDAY, Cut(10f, 11.5f), MONDAY, Cut(10f, 12f), LONDON, LONDON, resize = true)
        assertEquals(meeting.start, start)
        assertEquals(clock(LONDON, 2026, 9, 21, 12), finish)
    }

    @Test
    fun `a block placed by another clock moves by that clock`() {
        // 10:00 New York, drawn at 10:00 with events in their own zones,
        // carried to 11:00 on the same column: an hour later in New York.
        val abroad = meeting.copy(
            start = clock(NEW_YORK, 2026, 9, 21, 10),
            finish = clock(NEW_YORK, 2026, 9, 21, 11),
            zone = Zone("America/New_York", "America/New_York"),
        )
        val (start, _) = dropped(abroad, MONDAY, Cut(10f, 11f), MONDAY, Cut(11f, 12f), NEW_YORK, NEW_YORK, resize = false)
        assertEquals(clock(NEW_YORK, 2026, 9, 21, 11), start)
    }

    // ---- what a move takes ----

    /**
     * "This event" moved the 21st's occurrence onto the 22nd's start: a move
     * of the override takes it alone, and a move of the 22nd's own
     * occurrence leaves the override where it is.
     */
    @Test
    fun `a move of one occurrence takes it by its identity, not by its start`() {
        val own = meeting.copy(event = "e1", start = clock(LONDON, 2026, 9, 22, 10), recurring = true)
        val moved = own.copy(exception = true, replaced = clock(LONDON, 2026, 9, 21, 10))
        assertTrue(takes(moved, Scope.ONE, moved))
        assertFalse(takes(moved, Scope.ONE, own))
        assertFalse(takes(own, Scope.ONE, moved))
        assertTrue(takes(own, Scope.ALL, moved))
    }

    @Test
    fun `a move of an event that does not repeat takes its one occurrence`() {
        val single = meeting.copy(event = "e2")
        assertTrue(takes(single, Scope.ONE, single.copy(start = single.start + 3_600)))
        assertFalse(takes(single, Scope.ONE, single.copy(event = "e3")))
    }

    @Test
    fun `this and following takes the occurrences from the one moved on`() {
        val first = meeting.copy(event = "e1", recurring = true)
        val later = first.copy(start = first.start + 86_400)
        assertTrue(takes(first, Scope.FOLLOWING, later))
        assertFalse(takes(later, Scope.FOLLOWING, first))
    }

    /**
     * A daily series from Monday the 21st, its Tuesday dragged with "This and
     * following": an override is taken by its place in the series, the
     * occurrence it replaces, as the series is cut there, not by where "This
     * event" moved it.
     */
    @Test
    fun `this and following takes an override by its place in the series, not where it was moved to`() {
        val monday = meeting.copy(event = "e1", recurring = true)
        val tuesday = monday.copy(start = monday.start + 86_400)
        // Monday's occurrence moved onto Wednesday, and Thursday's onto Sunday the 20th.
        val early = monday.copy(start = monday.start + 2 * 86_400, exception = true, replaced = monday.start)
        val late = monday.copy(start = monday.start - 86_400, exception = true, replaced = monday.start + 3 * 86_400)
        assertFalse("THE POINT: Monday's, though moved after Tuesday, is before it in the series", takes(tuesday, Scope.FOLLOWING, early))
        assertTrue("THE POINT: Thursday's, though moved before Tuesday, is after it in the series", takes(tuesday, Scope.FOLLOWING, late))
        assertTrue("an override dragged is cut at its own place too", takes(late, Scope.FOLLOWING, monday.copy(start = monday.start + 4 * 86_400)))
        assertFalse(takes(late, Scope.FOLLOWING, tuesday))
    }

    // ---- turning the page at the side ----

    @Test
    fun `a page turns once the finger has rested at the side for the hold, then again each hold`() {
        val dwell = Dwell()
        assertEquals(0, dwell.step(1, 1_000))
        assertEquals(0, dwell.step(1, 1_000 + PAGE_HOLD - 1))
        assertEquals(1, dwell.step(1, 1_000 + PAGE_HOLD))
        assertEquals(0, dwell.step(1, 1_000 + PAGE_HOLD + 10))
        assertEquals(1, dwell.step(1, 1_000 + 2 * PAGE_HOLD))
    }

    @Test
    fun `leaving the side or crossing to the other starts the count again`() {
        val dwell = Dwell()
        dwell.step(1, 0)
        assertEquals(0, dwell.step(0, 300))
        assertEquals(0, dwell.step(1, 400))
        assertEquals(0, dwell.step(1, 400 + PAGE_HOLD - 1))
        assertEquals(0, dwell.step(-1, 400 + PAGE_HOLD))
        assertEquals(-1, dwell.step(-1, 400 + 2 * PAGE_HOLD))
    }

    @Test
    fun `the first day's side pages back, the last day's forward, and the gutter counts as the first side`() {
        // Left to right: days run from 52 to 400.
        assertEquals(-1, edge(60f, 52f, 400f, 28f))
        assertEquals(-1, edge(20f, 52f, 400f, 28f))
        assertEquals(0, edge(200f, 52f, 400f, 28f))
        assertEquals(1, edge(390f, 52f, 400f, 28f))
        // Right to left: the first day is on the right.
        assertEquals(-1, edge(390f, 348f, 0f, 28f))
        assertEquals(1, edge(10f, 348f, 0f, 28f))
    }

    // ---- into and out of the band ----

    @Test
    fun `a block dropped in the band becomes all day from the day it landed on, over as many days`() {
        val form = EventForm(start = clock(LONDON, 2026, 9, 21, 23), finish = clock(LONDON, 2026, 9, 22, 1), zone = Zone("Europe/London", "Europe/London"))
        val day = whole(form, 2, 2, LONDON)
        assertTrue(day.allday)
        assertEquals(LocalDate.of(2026, 9, 23).atStartOfDay(ZoneOffset.UTC).toEpochSecond(), day.start)
        assertEquals(LocalDate.of(2026, 9, 25).atStartOfDay(ZoneOffset.UTC).toEpochSecond(), day.finish)
    }

    @Test
    fun `a block read on its days in the user's zone lands on the day dropped, whatever zone it was written in`() {
        // 23:30 Monday in London is already Tuesday in Berlin, where the
        // views put it; dropped on Wednesday it is all day on Wednesday.
        val berlin = ZoneId.of("Europe/Berlin")
        val form = EventForm(start = clock(LONDON, 2026, 9, 21, 23, 30), finish = clock(LONDON, 2026, 9, 22, 0, 30), zone = Zone("Europe/London", "Europe/London"))
        val day = whole(form, 1, 1, berlin)
        assertEquals(LocalDate.of(2026, 9, 23).atStartOfDay(ZoneOffset.UTC).toEpochSecond(), day.start)
    }

    @Test
    fun `a bar dropped in the grid becomes timed on the day it landed on, at the user's clock`() {
        val form = EventForm(
            start = LocalDate.of(2026, 9, 21).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
            finish = LocalDate.of(2026, 9, 22).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
            allday = true,
        )
        val timed = clocked(form, 1, 11.25f, 3_600, NEW_YORK)
        assertEquals(false, timed.allday)
        assertEquals(clock(NEW_YORK, 2026, 9, 22, 11, 15), timed.start)
        assertEquals(clock(NEW_YORK, 2026, 9, 22, 12, 15), timed.finish)
    }

    // ---- what a move cuts at, and draws ----

    /**
     * A weekly series from Monday 21 September, three of its occurrences moved
     * by "This event": the 28th's back to Sunday the 27th, the 21st's on to
     * Thursday 1 October, and 5 October's back to Saturday the 26th. "This
     * and following" from the 28th's cuts the series by each one's place, as
     * the server does, not by where each now starts.
     */
    @Test
    fun `this and following cuts the series at the place of the occurrence, not at where overrides now start`() {
        val first = meeting.copy(event = "e1", recurring = true)
        val week = 7 * 86_400L
        val dragged = first.copy(start = first.start + week - 86_400, exception = true, replaced = first.start + week)
        val early = first.copy(start = first.start + week + 3 * 86_400, exception = true, replaced = first.start)
        val late = first.copy(start = first.start + 5 * 86_400, exception = true, replaced = first.start + 2 * week)
        assertFalse("THE POINT: the 21st's, though it now starts after the 27th, is before the cut", takes(dragged, Scope.FOLLOWING, early))
        assertTrue("THE POINT: 5 October's, though it now starts before the 27th, is after the cut", takes(dragged, Scope.FOLLOWING, late))
        assertTrue("and the dragged one itself", takes(dragged, Scope.FOLLOWING, dragged))
    }

    /**
     * A listing that holds the event "This and following" made, read before
     * the move's preview lets go, draws the occurrences once: the preview
     * draws them where they go, as the old event's, until a listing asked for
     * after the answer comes.
     */
    @Test
    fun `a move's preview hides the events its save made, which it draws itself`() {
        val first = meeting.copy(event = "e1", recurring = true)
        val moved = first.copy(start = first.start + 3_600, finish = first.finish + 3_600, readonly = true, mark = "e1:moved:1")
        val preview = Preview(
            covers = { occurrence -> takes(first, Scope.FOLLOWING, occurrence) },
            placed = listOf(moved),
            place = null,
            known = setOf("e1" to first.start, "e1" to moved.start),
            hides = { false },
        )
        // The new event's occurrence where the move put it, as a listing has it.
        val made = first.copy(event = "e3", start = moved.start, finish = moved.finish)
        assertEquals("until the save is answered", 2, preview.over(listOf(made)).size)
        preview.made = setOf("e3")
        assertEquals("THE POINT: drawn once, as the preview draws it", listOf(moved), preview.over(listOf(made)))
    }
}
