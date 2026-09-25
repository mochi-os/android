// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.ui.editor.shownIn

/**
 * An all-day day is held as its UTC midnight; shown in a zone west of UTC
 * that instant is the evening before, so the form reads it as a date in UTC.
 */
class EventDayTest {

    private val midnight = LocalDate.of(2026, 9, 24).atStartOfDay(ZoneOffset.UTC).toEpochSecond()

    @Test
    fun `an all-day day reads in UTC, where its midnight is its own date`() {
        val zone = shownIn(true, "America/New_York")
        assertEquals(ZoneOffset.UTC, zone)
        assertEquals(LocalDate.of(2026, 9, 24), Instant.ofEpochSecond(midnight).atZone(zone).toLocalDate())
    }

    @Test
    fun `read in New York the same instant would be the day before`() {
        val there = Instant.ofEpochSecond(midnight).atZone(ZoneId.of("America/New_York")).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 23), there)
    }

    @Test
    fun `a day picked for an all-day event writes back as its own UTC midnight`() {
        // The field's write-back: the picked date at the shown time, in the shown zone.
        val zone = shownIn(true, "America/New_York")
        val shown = Instant.ofEpochSecond(midnight).atZone(zone)
        val picked = LocalDate.of(2026, 9, 25).atTime(shown.toLocalTime()).atZone(zone).toEpochSecond()
        assertEquals(LocalDate.of(2026, 9, 25).atStartOfDay(ZoneOffset.UTC).toEpochSecond(), picked)
    }

    @Test
    fun `a timed end reads in its own zone, or the device's for one it does not know`() {
        assertEquals(ZoneId.of("America/New_York"), shownIn(false, "America/New_York"))
        assertEquals(ZoneId.systemDefault(), shownIn(false, "Nowhere/Invalid"))
    }
}
