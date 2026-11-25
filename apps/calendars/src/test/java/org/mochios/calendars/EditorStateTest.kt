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
import org.mochios.calendars.model.Hours
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.editor.EditorUiState
import org.mochios.calendars.ui.editor.Ending
import org.mochios.calendars.ui.editor.Frequency
import org.mochios.calendars.ui.editor.Recurrence
import org.mochios.calendars.ui.editor.customised
import org.mochios.calendars.ui.editor.recurred
import org.mochios.calendars.ui.editor.recurrence
import org.mochios.calendars.ui.editor.Reading
import org.mochios.calendars.ui.editor.finished
import org.mochios.calendars.ui.editor.listed
import org.mochios.calendars.ui.editor.repeated
import org.mochios.calendars.ui.editor.started
import org.mochios.calendars.ui.editor.starting
import org.mochios.calendars.ui.editor.toggled
import org.mochios.calendars.ui.editor.zoned
import java.time.LocalDate
import java.time.LocalDateTime
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

    // An all-day event over the 2nd and 3rd, with no times of its own.
    private val opened = EditorUiState(allday = true, start = midnight(2), finish = midnight(4), zone = Zone(york, york))

    @Test
    fun `an event that opened all day comes back at the working hours on another day, for the default length, on its days`() {
        val back = toggled(opened, false, york, Hours(), 60, LocalDateTime.of(2026, 9, 28, 14, 37))
        assertEquals(at(2, 8), back.start)
        assertEquals(at(3, 9), back.finish)
    }

    @Test
    fun `an event that opened all day today comes back at the next whole hour, as a new event starts`() {
        val back = toggled(opened, false, york, Hours(), 60, LocalDateTime.of(2026, 10, 2, 14, 37))
        assertEquals(at(2, 15), back.start)
        assertEquals(at(3, 16), back.finish)
    }

    @Test
    fun `an event that opened all day keeps its day when today has no whole hour left`() {
        val back = toggled(opened, false, york, Hours(), 60, LocalDateTime.of(2026, 10, 2, 23, 10))
        assertEquals(at(2, 8), back.start)
        assertEquals(at(3, 9), back.finish)
    }

    @Test
    fun `an event that opened all day comes back at the working hours and length it is given`() {
        val back = toggled(opened, false, york, Hours(start = 9, finish = 17), 30, LocalDateTime.of(2026, 9, 28, 14, 37))
        assertEquals(at(2, 9), back.start)
        assertEquals(at(3, 9, 30), back.finish)
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

    // ---- a time the clocks skip or read twice ----
    //
    // Each is placed where the server places it, Go's time.Date: New York's
    // skipped hour on 10 March 2024 an hour back, in winter time; London's
    // hour on 27 October 2024 that comes round twice in winter time; Havana's
    // skipped midnight on 10 March 2024 an hour back, at 23:00 the evening
    // before; and Amman's midnight on 30 October 2020, which comes round
    // twice, the second time. The platform would put each the other way.

    private val london = "Europe/London"

    /** An instant, written in UTC. */
    private fun utc(text: String) = java.time.Instant.parse(text).epochSecond

    @Test
    fun `all day off puts times the clocks skip or read twice where the server places them`() {
        // 02:30 to 03:30 on New York's 10 March, and 01:30 to 02:30 on
        // London's 27 October, kept aside while all day.
        val day = { date: LocalDate, zone: String, clock: Pair<Int, Int> ->
            EditorUiState(
                allday = true,
                start = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                finish = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                zone = Zone(zone, zone),
                clock = clock,
            )
        }
        val skipping = toggled(day(LocalDate.of(2024, 3, 10), york, 150 to 210), false, york)
        val twice = toggled(day(LocalDate.of(2024, 10, 27), london, 90 to 150), false, london)
        assertEquals(
            "THE POINT: 01:30 in New York's winter time, and London's 01:30 in winter time",
            listOf(utc("2024-03-10T06:30:00Z"), utc("2024-03-10T07:30:00Z"), utc("2024-10-27T01:30:00Z"), utc("2024-10-27T02:30:00Z")),
            listOf(skipping.start, skipping.finish, twice.start, twice.finish),
        )
    }

    @Test
    fun `a new event opened in an hour the clocks skip or read twice starts where the server places it`() {
        // At 01:15 the next whole hour is 02:00, which New York's clocks skip
        // on 10 March 2024, and at 00:15 it is 01:00, which London's read
        // twice on 27 October 2024.
        assertEquals(
            "THE POINT: 01:00 in New York's winter time, and London's 01:00 in winter time",
            listOf(utc("2024-03-10T06:00:00Z"), utc("2024-10-27T01:00:00Z")),
            listOf(
                starting(ZonedDateTime.of(2024, 3, 10, 1, 15, 0, 0, ZoneId.of(york)), Hours()),
                starting(ZonedDateTime.of(2024, 10, 27, 0, 15, 0, 0, ZoneId.of(london)), Hours()),
            ),
        )
    }

    @Test
    fun `an all-day occurrence a save follows is the one the server lists at a midnight the clocks skip or read twice`() {
        val day = { date: LocalDate ->
            EditorUiState(
                allday = true,
                start = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                finish = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                recurrence = Recurrence(Frequency.DAILY),
            )
        }
        assertEquals(
            "THE POINT: an hour back in Havana, the second midnight in Amman",
            listOf(utc("2024-03-10T04:00:00Z"), utc("2020-10-29T22:00:00Z")),
            listOf(listed(day(LocalDate.of(2024, 3, 10)), "America/Havana"), listed(day(LocalDate.of(2020, 10, 30)), "Asia/Amman")),
        )
    }

    /** An hour from 10:00 on [date] in [zone], as typed. */
    private fun hour(date: LocalDate, zone: String): EditorUiState {
        val opens = date.atTime(10, 0)
        return EditorUiState(
            start = opens.atZone(ZoneId.of(zone)).toEpochSecond(),
            finish = opens.plusHours(1).atZone(ZoneId.of(zone)).toEpochSecond(),
            zone = Zone(zone, zone),
            reading = Reading(opens, opens.plusHours(1)),
        )
    }

    /** What a state holds of its ends: each instant, then each reading. */
    private fun ends(state: EditorUiState) = listOf(state.start, state.finish, state.reading.start, state.reading.finish)

    @Test
    fun `a start picked at a time the clocks skip or read twice is held as typed and placed where the server places it`() {
        // The end moves with it, as far on its clock.
        val skipping = started(hour(LocalDate.of(2024, 3, 10), york), LocalDateTime.of(2024, 3, 10, 2, 30), york)
        val twice = started(hour(LocalDate.of(2024, 10, 27), london), LocalDateTime.of(2024, 10, 27, 1, 30), london)
        assertEquals(
            "THE POINT: 02:30 as typed, at 01:30 in New York's winter time; London's 01:30 in winter time",
            listOf(
                utc("2024-03-10T06:30:00Z"), utc("2024-03-10T07:30:00Z"),
                LocalDateTime.of(2024, 3, 10, 2, 30), LocalDateTime.of(2024, 3, 10, 3, 30),
                utc("2024-10-27T01:30:00Z"), utc("2024-10-27T02:30:00Z"),
                LocalDateTime.of(2024, 10, 27, 1, 30), LocalDateTime.of(2024, 10, 27, 2, 30),
            ),
            ends(skipping) + ends(twice),
        )
    }

    @Test
    fun `an end picked at a time the clocks skip or read twice is held as typed and placed where the server places it`() {
        val skipping = finished(hour(LocalDate.of(2024, 3, 10), york), LocalDateTime.of(2024, 3, 10, 2, 30), york)
        val twice = finished(hour(LocalDate.of(2024, 10, 27), london), LocalDateTime.of(2024, 10, 27, 1, 30), london)
        assertEquals(
            "THE POINT: 02:30 as typed, at 01:30 in New York's winter time; London's 01:30 in winter time",
            listOf(utc("2024-03-10T06:30:00Z"), LocalDateTime.of(2024, 3, 10, 2, 30), utc("2024-10-27T01:30:00Z"), LocalDateTime.of(2024, 10, 27, 1, 30)),
            listOf(skipping.finish, skipping.reading.finish, twice.finish, twice.reading.finish),
        )
    }

    @Test
    fun `a start moved to a zone whose clocks skip or read it twice keeps its reading and is placed where the server places it`() {
        // 02:30 in London on 10 March 2024 moved to New York, and 01:30 in
        // New York on 27 October 2024 moved to London.
        val at = { date: LocalDateTime, zone: String ->
            EditorUiState(
                start = date.atZone(ZoneId.of(zone)).toEpochSecond(),
                finish = date.plusHours(1).atZone(ZoneId.of(zone)).toEpochSecond(),
                zone = Zone(zone, zone),
            )
        }
        val skipping = zoned(at(LocalDateTime.of(2024, 3, 10, 2, 30), london), Zone(york, york), london)
        val twice = zoned(at(LocalDateTime.of(2024, 10, 27, 1, 30), york), Zone(london, london), london)
        assertEquals(
            "THE POINT: 02:30 kept, at 01:30 in New York's winter time; London's 01:30 in winter time",
            listOf(utc("2024-03-10T06:30:00Z"), LocalDateTime.of(2024, 3, 10, 2, 30), utc("2024-10-27T01:30:00Z"), LocalDateTime.of(2024, 10, 27, 1, 30)),
            listOf(skipping.start, skipping.reading.start, twice.start, twice.reading.start),
        )
    }
}
