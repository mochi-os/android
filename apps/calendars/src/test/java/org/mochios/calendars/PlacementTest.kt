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
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.calendar.Cut
import org.mochios.calendars.ui.calendar.MINIMUM
import org.mochios.calendars.ui.calendar.clockZone
import org.mochios.calendars.ui.calendar.cut
import org.mochios.calendars.ui.calendar.days
import org.mochios.calendars.ui.calendar.ends
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Where a timed occurrence lands in the day and week views: at its
 * wall-clock time in the user's zone, or, with events shown in their own
 * zones, each end in the zone it was written in.
 */
class PlacementTest {

    private val LONDON: ZoneId = ZoneId.of("Europe/London")

    private fun at(zone: String, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zone)).toEpochSecond()

    private val SEPTEMBER_22 = LocalDate.of(2026, 9, 22)
    private val SEPTEMBER_23 = LocalDate.of(2026, 9, 23)

    /** 10:00 to 13:00 New York on the 22nd, which is 15:00 to 18:00 in London. */
    private val meeting = Instance(
        start = at("America/New_York", 2026, 9, 22, 10),
        finish = at("America/New_York", 2026, 9, 22, 13),
        zone = Zone("America/New_York", "America/New_York"),
    )

    /** Auckland Monday 10:00 to Tahiti Sunday 15:00: a flight west across the date line. */
    private val flight = Instance(
        start = at("Pacific/Auckland", 2026, 9, 21, 10),
        finish = at("Pacific/Tahiti", 2026, 9, 20, 15),
        zone = Zone("Pacific/Auckland", "Pacific/Tahiti"),
    )

    // ---- the user's zone ----

    @Test
    fun `with zones off an occurrence sits at its time in the user's zone`() {
        assertEquals(Cut(15f, 18f), cut(meeting, SEPTEMBER_22, LONDON, zones = false))
        assertNull(cut(meeting, SEPTEMBER_23, LONDON, zones = false))
    }

    @Test
    fun `with zones off a foreign zone is ignored`() {
        // 22:00 to 23:00 London reads the same whatever zone the ends name.
        val late = Instance(
            start = at("Europe/London", 2026, 9, 22, 22),
            finish = at("Europe/London", 2026, 9, 22, 23),
            zone = Zone("Asia/Tokyo", "Asia/Tokyo"),
        )
        assertEquals(Cut(22f, 23f), cut(late, SEPTEMBER_22, LONDON, zones = false))
        assertEquals(SEPTEMBER_22 to SEPTEMBER_22, days(late, LONDON, zones = false))
    }

    // ---- its own zones ----

    @Test
    fun `with zones on each end sits at its own wall-clock time`() {
        assertEquals(Cut(10f, 13f), cut(meeting, SEPTEMBER_22, LONDON, zones = true))
    }

    @Test
    fun `a blank zone reads in the user's zone`() {
        val floating = meeting.copy(zone = Zone("", ""))
        assertEquals(Cut(15f, 18f), cut(floating, SEPTEMBER_22, LONDON, zones = true))
        val none = meeting.copy(zone = null)
        assertEquals(Cut(15f, 18f), cut(none, SEPTEMBER_22, LONDON, zones = true))
    }

    @Test
    fun `an occurrence crossing midnight draws on each day it covers`() {
        val overnight = Instance(
            start = at("Europe/London", 2026, 9, 22, 22),
            finish = at("Europe/London", 2026, 9, 23, 2),
            zone = Zone("Europe/London", "Europe/London"),
        )
        assertEquals(Cut(22f, 24f), cut(overnight, SEPTEMBER_22, LONDON, zones = true))
        assertEquals(Cut(0f, 2f), cut(overnight, SEPTEMBER_23, LONDON, zones = true))
        assertEquals(SEPTEMBER_22 to SEPTEMBER_23, days(overnight, LONDON, zones = true))
    }

    @Test
    fun `a finish on the stroke of midnight belongs to the day before`() {
        val evening = Instance(
            start = at("Europe/London", 2026, 9, 22, 22),
            finish = at("Europe/London", 2026, 9, 23, 0),
            zone = Zone("Europe/London", "Europe/London"),
        )
        assertEquals(Cut(22f, 24f), cut(evening, SEPTEMBER_22, LONDON, zones = true))
        assertNull(cut(evening, SEPTEMBER_23, LONDON, zones = true))
        assertEquals(SEPTEMBER_22 to SEPTEMBER_22, days(evening, LONDON, zones = true))
    }

    @Test
    fun `an occurrence with no length is drawn half an hour tall`() {
        val instant = meeting.copy(finish = meeting.start)
        assertEquals(Cut(10f, 10.5f), cut(instant, SEPTEMBER_22, LONDON, zones = true))
    }

    // ---- ends before it starts ----

    @Test
    fun `a flight west across the date line reads backwards`() {
        assertTrue(ends(flight, LONDON, zones = true).backwards)
        assertFalse(ends(flight, LONDON, zones = false).backwards)
        assertFalse(ends(meeting, LONDON, zones = true).backwards)
    }

    @Test
    fun `a backwards occurrence sits on its start day alone at the minimum height`() {
        val monday = LocalDate.of(2026, 9, 21)
        assertEquals(Cut(10f, 10f + MINIMUM, backwards = true), cut(flight, monday, LONDON, zones = true))
        assertNull(cut(flight, LocalDate.of(2026, 9, 20), LONDON, zones = true))
        assertNull(cut(flight, LocalDate.of(2026, 9, 22), LONDON, zones = true))
        assertEquals(monday to monday, days(flight, LONDON, zones = true))
    }

    @Test
    fun `an end later on the same day by the clock is not backwards`() {
        // 10:00 London to 06:00 New York is the same instant span as 10:00
        // to 11:00 London; by the clock the end reads before the start.
        val toNewYork = Instance(
            start = at("Europe/London", 2026, 9, 22, 10),
            finish = at("Europe/London", 2026, 9, 22, 11),
            zone = Zone("Europe/London", "America/New_York"),
        )
        assertTrue(ends(toNewYork, LONDON, zones = true).backwards)
        // 10:00 London to 13:00 New York reads forwards, three hours on.
        val longer = toNewYork.copy(finish = at("America/New_York", 2026, 9, 22, 13))
        assertEquals(Cut(10f, 13f), cut(longer, SEPTEMBER_22, LONDON, zones = true))
    }

    // ---- the clock text ----

    @Test
    fun `the clock reads in the end's own zone only with zones on`() {
        assertEquals("America/New_York", clockZone("America/New_York", zones = true))
        assertNull(clockZone("America/New_York", zones = false))
        assertNull(clockZone("", zones = true))
        assertNull(clockZone(null, zones = true))
    }
}
