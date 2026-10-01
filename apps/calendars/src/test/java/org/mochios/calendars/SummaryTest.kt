// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.i18n.Clock
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.TimeFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.util.Zones
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.ui.calendar.Wording
import org.mochios.calendars.ui.calendar.summary
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * An occurrence's summary reads its span as the web's does: the long date
 * and the times without seconds, the date once for one day, an all-day
 * occurrence by its day or its run of days, and the ends' own zones beneath
 * when it was written in another zone than the user's.
 */
class SummaryTest {

    /** A platform writing British English, as ICU gives its patterns. */
    private val british = object : Clock {
        private val patterns = mapOf("hm" to "h:mm a", "Hm" to "HH:mm", "EEEEdMMMMy" to "EEEE d MMMM y")

        override fun pattern(skeleton: String): String = patterns.getValue(skeleton)

        override fun write(pattern: String, millis: Long, zone: TimeZone): String {
            val format = SimpleDateFormat(pattern, Locale.UK)
            format.timeZone = zone
            return format.format(Date(millis))
        }

        override fun span(skeleton: String, from: Long, to: Long, zone: TimeZone): String {
            val format = SimpleDateFormat("d MMMM y", Locale.UK)
            format.timeZone = zone
            return format.format(Date(from)) + " – " + format.format(Date(to))
        }
    }

    /** A platform that resolves zones as ICU does, where Asia/Kolkata's own name is Asia/Calcutta. */
    private val icu = object : Zones.Registry {
        override fun canonical(zone: String): String? =
            mapOf("Asia/Kolkata" to "Asia/Calcutta", "Asia/Calcutta" to "Asia/Calcutta")[zone]

        override fun places(): Collection<String> = emptyList()
    }

    private val wording = Wording(
        allday = { "All day · $it" },
        day = { date, from, to -> "$date, $from to $to" },
        range = { from, to -> "$from to $to" },
    )

    private fun format(zone: String = "Europe/London") =
        Format(UserPreferences(timeFormat = TimeFormat.H24, timezone = zone), british)

    private fun at(day: Int, hour: Int, minute: Int = 0, zone: String = "Europe/London"): Long =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, ZoneId.of(zone)).toEpochSecond()

    private fun timed(start: Long, finish: Long, zone: Zone? = null) =
        Instance(event = "e", summary = "Flight", start = start, finish = finish, zone = zone)

    @Test
    fun `a timed occurrence on one day reads its long date once and its times without seconds`() {
        val (span, own) = summary(timed(at(28, 10), at(28, 11, 30)), format(), zones = false, wording, icu)
        assertEquals("Monday 28 September 2026, 10:00 to 11:30", span)
        assertNull(own)
    }

    @Test
    fun `one over midnight reads each end's date and time`() {
        val (span, _) = summary(timed(at(28, 22), at(29, 6)), format(), zones = false, wording, icu)
        assertEquals("Monday 28 September 2026 22:00 to Tuesday 29 September 2026 06:00", span)
    }

    @Test
    fun `one ending on the stroke of midnight belongs to its day`() {
        val (span, _) = summary(timed(at(28, 22), at(29, 0)), format(), zones = false, wording, icu)
        assertEquals("Monday 28 September 2026, 22:00 to 00:00", span)
    }

    @Test
    fun `an all-day occurrence reads as its day or its run of days`() {
        val day = Instance(event = "e", allday = true, date = "2026-09-28", start = at(28, 0), finish = at(29, 0))
        assertEquals("All day · Monday 28 September 2026" to null, summary(day, format(), false, wording, icu))
        val trip = day.copy(finish = at(28, 0) + 3 * 86_400)
        assertEquals("All day · 28 September 2026 – 30 September 2026" to null, summary(trip, format(), false, wording, icu))
    }

    @Test
    fun `an all-day occurrence reads by its date, whatever zone its instants fall in`() {
        // Expanded by a server nine hours ahead: the instants begin on the 27th here.
        val day = Instance(
            event = "e", allday = true, date = "2026-09-28",
            start = at(27, 15), finish = at(28, 15),
        )
        assertEquals("All day · Monday 28 September 2026", summary(day, format(), false, wording, icu).first)
    }

    @Test
    fun `one written in another zone reads in the user's, with its own zones beneath`() {
        val flight = timed(at(28, 10), at(28, 13, zone = "America/New_York"), Zone("Europe/London", "America/New_York"))
        val (span, own) = summary(flight, format(), zones = false, wording, icu)
        assertEquals("Monday 28 September 2026, 10:00 to 18:00", span)
        assertEquals("Monday 28 September 2026, 10:00 London to 13:00 New York", own)
        val (zoned, none) = summary(flight, format(), zones = true, wording, icu)
        assertEquals("Monday 28 September 2026, 10:00 London to 13:00 New York", zoned)
        assertNull(none)
    }

    @Test
    fun `one written under another name of the user's zone is not another zone`() {
        val kolkata = "Asia/Kolkata"
        val meeting = timed(at(28, 10, zone = kolkata), at(28, 11, zone = kolkata), Zone("Asia/Calcutta", "Asia/Calcutta"))
        val (span, own) = summary(meeting, format(kolkata), zones = false, wording, icu)
        assertEquals("Monday 28 September 2026, 10:00 to 11:00", span)
        assertNull(own)
    }
}
