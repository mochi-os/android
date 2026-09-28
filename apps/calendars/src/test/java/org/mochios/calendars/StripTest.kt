// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.editor.Block
import org.mochios.calendars.ui.editor.Span
import org.mochios.calendars.ui.editor.Strip
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The day strip under the editor's times: when it shows, and what a drag or a tap does. */
class StripTest {

    private val LONDON = "Europe/London"
    private val DAY = LocalDate.of(2026, 9, 30)
    private val NINE = 9 * 60

    private fun at(minutes: Int, zone: String = LONDON): Long =
        ZonedDateTime.of(DAY.atStartOfDay(), ZoneId.of(zone)).plusMinutes(minutes.toLong()).toEpochSecond()

    // ---- when it shows ----

    @Test
    fun `a timed event on one day in one zone has a span`() {
        assertEquals(Span(DAY, NINE, NINE + 60), Strip.span(at(NINE), at(NINE + 60), false, Zone(LONDON, LONDON)))
    }

    @Test
    fun `an all-day event, one over several days, and a flight across zones have none`() {
        assertNull(Strip.span(at(NINE), at(NINE + 60), true, Zone(LONDON, LONDON)))
        assertNull(Strip.span(at(NINE), at(NINE + 26 * 60), false, Zone(LONDON, LONDON)))
        assertNull(Strip.span(at(NINE), at(NINE + 480), false, Zone(LONDON, "America/New_York")))
    }

    @Test
    fun `one zone under two names is one zone`() {
        val kolkata = ZonedDateTime.of(DAY.atTime(9, 0), ZoneId.of("Asia/Kolkata")).toEpochSecond()
        assertNotNull(Strip.span(kolkata, kolkata + 3_600, false, Zone("Asia/Calcutta", "Asia/Kolkata")))
    }

    @Test
    fun `a minute of the day reads back as the instant it is in the zone`() {
        assertEquals(at(14 * 60 + 5), Strip.instant(DAY, 14 * 60 + 5, LONDON))
    }

    // ---- dragging and tapping ----

    @Test
    fun `a drag moves the event by whole steps, keeping its length`() {
        assertEquals(NINE + 30, Strip.moved(NINE, NINE + 60, 32.0))
        assertEquals(NINE - 15, Strip.moved(NINE, NINE + 60, -17.0))
        assertEquals(NINE + 5, Strip.moved(NINE + 3, NINE + 63, 0.0))
    }

    @Test
    fun `the event stays within the day`() {
        assertEquals(0, Strip.moved(60, 120, -600.0))
        assertEquals(Strip.LAST - 60, Strip.moved(22 * 60, 23 * 60, 600.0))
    }

    @Test
    fun `the end stretches by whole steps, never to or before the start`() {
        assertEquals(NINE + 90, Strip.resized(NINE, NINE + 60, 28.0))
        assertEquals(NINE + 5, Strip.resized(NINE, NINE + 60, -120.0))
        assertEquals(Strip.LAST, Strip.resized(NINE, NINE + 60, 24 * 60.0))
    }

    @Test
    fun `a tap moves the event to the step it falls in, keeping its length`() {
        assertEquals(14 * 60 + 5, Strip.placed(NINE, NINE + 60, 14 * 60 + 7.5))
        assertEquals(Strip.LAST - 60, Strip.placed(NINE, NINE + 60, 23 * 60 + 50.0))
    }

    // ---- the day's other events ----

    private fun instance(event: String, start: Long, finish: Long, allday: Boolean = false) =
        Instance(event = event, summary = "Lunch", colour = "#60a5fa", start = start, finish = finish, allday = allday)

    @Test
    fun `each timed event is placed by its minutes of the day in the zone`() {
        val lunch = instance("e1", at(12 * 60), at(13 * 60 + 30))
        assertEquals(
            listOf(Block(12 * 60, 13 * 60 + 30, "Lunch", "#60a5fa")),
            Strip.blocks(listOf(lunch), DAY, LONDON, null, 0),
        )
        val tokyo = Strip.blocks(listOf(lunch), DAY, "Asia/Tokyo", null, 0)
        assertEquals(20 * 60, tokyo.single().start)
    }

    @Test
    fun `an event over midnight is cut at the edges of the day`() {
        val late = instance("e1", at(-60), at(60))
        val night = instance("e2", at(23 * 60), at(25 * 60))
        assertEquals(
            listOf(0 to 60, 23 * 60 to 24 * 60),
            Strip.blocks(listOf(late, night), DAY, LONDON, null, 0).map { it.start to it.finish },
        )
    }

    @Test
    fun `all-day events, other days, and the occurrence being edited are left out`() {
        val listed = listOf(
            instance("e1", at(0), at(24 * 60), allday = true),
            instance("e1", at(-180), at(-120)),
            instance("e2", at(NINE), at(NINE + 60)),
            instance("e3", at(NINE), at(NINE + 30)),
            // Another occurrence of the event being edited stays.
            instance("e2", at(18 * 60), at(18 * 60 + 45)),
        )
        assertEquals(listOf(30, 45), Strip.blocks(listed, DAY, LONDON, "e2", at(NINE)).map { it.finish - it.start })
    }
}
