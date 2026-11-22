// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A date field holds a calendar day: it is that day for every reader, west of
 * UTC and east of it, and a day before 1970 is a day like any other. A moment
 * stored by an older writer is read as its date in the user's zone.
 */
class CalendarDateTest {

    /** The platform's writing, as the JDK does it in English. */
    private object English : Clock {
        override fun pattern(skeleton: String): String = skeleton

        override fun write(pattern: String, millis: Long, zone: TimeZone): String {
            val format = SimpleDateFormat(pattern, Locale.ENGLISH)
            format.timeZone = zone
            return format.format(Date(millis))
        }

        override fun span(skeleton: String, from: Long, to: Long, zone: TimeZone): String = ""
    }

    private fun format(zone: String, date: DateFormat = DateFormat.DD_SLASH_MM_YYYY) =
        Format(UserPreferences(dateFormat = date, timezone = zone), clock = English)

    private val west = "America/Los_Angeles"

    // UTC+14: noon UTC is already the next day there.
    private val east = "Pacific/Kiritimati"

    @Test
    fun `a day is written as that day west of UTC`() {
        assertEquals("17/11/2026", format(west).formatDate(LocalDate.of(2026, 11, 17)))
    }

    @Test
    fun `a day is written as that day far east of UTC`() {
        assertEquals("17/11/2026", format(east).formatDate(LocalDate.of(2026, 11, 17)))
    }

    @Test
    fun `a day on or before 1970 is written, not left blank`() {
        assertEquals("01/01/1970", format(west).formatDate(LocalDate.of(1970, 1, 1)))
        assertEquals("20/07/1969", format(east).formatDate(LocalDate.of(1969, 7, 20)))
    }

    @Test
    fun `the user's date pattern applies`() {
        assertEquals("2026-11-17", format(west, DateFormat.YYYY_MM_DD).formatDate(LocalDate.of(2026, 11, 17)))
        assertEquals("17 Nov 2026", format(west, DateFormat.D_MMM_YYYY).formatDate(LocalDate.of(2026, 11, 17)))
    }

    @Test
    fun `an ISO day is that day in any zone`() {
        assertEquals(LocalDate.of(2026, 11, 17), format(west).calendarDate("2026-11-17"))
        assertEquals(LocalDate.of(2026, 11, 17), format(east).calendarDate(" 2026-11-17 "))
        assertEquals(LocalDate.of(1965, 3, 1), format(west).calendarDate("1965-03-01"))
    }

    @Test
    fun `an older writer's epoch is its date in the user's zone`() {
        // 2023-11-14 22:13:20 UTC: still the 14th in Los Angeles, the 15th in Kiritimati.
        assertEquals(LocalDate.of(2023, 11, 14), format(west).calendarDate("1700000000"))
        assertEquals(LocalDate.of(2023, 11, 15), format(east).calendarDate("1700000000"))
        assertEquals(LocalDate.of(2023, 11, 15), format(east).calendarDate("1700000000.0"))
        assertEquals(LocalDate.of(2023, 11, 15), format(east).calendarDate("1700000000000"))
        assertEquals(LocalDate.of(1969, 12, 31), format("UTC").calendarDate("-86400"))
    }

    @Test
    fun `an imported date-time is its date in the user's zone`() {
        assertEquals(LocalDate.of(2023, 12, 31), format(west).calendarDate("2024-01-01T01:00:00+01:00"))
        assertEquals(LocalDate.of(2024, 1, 1), format("UTC").calendarDate("2024-01-01T00:00:00Z"))
    }

    @Test
    fun `anything else is no date`() {
        assertNull(format(west).calendarDate(""))
        assertNull(format(west).calendarDate("next week"))
        assertNull(format(west).calendarDate("NaN"))
        assertNull(format(west).calendarDate("Infinity"))
        assertNull(calendarDate("2026-13-01", ZoneId.of("UTC")))
    }

    @Test
    fun `today is the user's today`() {
        // 2026-11-17 06:00 UTC: the 16th in Los Angeles, the 17th in Kiritimati.
        val now = 1_794_895_200_000L
        assertEquals(LocalDate.of(2026, 11, 16), format(west).today(now))
        assertEquals(LocalDate.of(2026, 11, 17), format(east).today(now))
    }

    @Test
    fun `a date picker's selection is the day it was opened on`() {
        val day = LocalDate.of(1969, 7, 20)
        assertEquals(-14_256_000_000L, pickerMillis(day))
        assertEquals(day, pickerDate(pickerMillis(day)))
        assertEquals(LocalDate.of(2026, 11, 17), pickerDate(pickerMillis(LocalDate.of(2026, 11, 17))))
        // A moment before 1970 is in the day it falls in, not the one after.
        assertEquals(LocalDate.of(1969, 12, 31), pickerDate(-1L))
    }
}
