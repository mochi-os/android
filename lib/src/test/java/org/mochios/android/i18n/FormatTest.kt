// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Times are written the way the user's language writes them, on the clock the
 * user chose: the formatter asks the platform for the language's pattern and
 * writes with it, rather than holding one pattern for every language.
 */
class FormatTest {

    /**
     * A platform that speaks [language]: its patterns are the ones given for
     * each skeleton, and it writes them as the JDK does. It notes each
     * skeleton asked for and each zone written in.
     */
    private class Platform(
        private val language: Locale,
        private val patterns: Map<String, String>,
    ) : Clock {
        val skeletons = mutableListOf<String>()
        val zones = mutableListOf<String>()
        val spans = mutableListOf<Triple<String, Long, Long>>()

        override fun pattern(skeleton: String): String {
            skeletons += skeleton
            return patterns.getValue(skeleton)
        }

        override fun write(pattern: String, millis: Long, zone: TimeZone): String {
            zones += zone.id
            val format = SimpleDateFormat(pattern, language)
            format.timeZone = zone
            return format.format(Date(millis))
        }

        override fun span(skeleton: String, from: Long, to: Long, zone: TimeZone): String {
            spans += Triple(skeleton, from, to)
            return "span"
        }
    }

    // As ICU gives them: Japanese puts the half of the day first, Danish
    // separates hours and minutes with a dot.
    private val japanese = mapOf("hm" to "aK:mm", "Hm" to "H:mm", "h" to "aK時", "hms" to "aK:mm:ss", "Hms" to "H:mm:ss")
    private val danish = mapOf("hm" to "h.mm a", "Hm" to "HH.mm", "h" to "h a", "hms" to "h.mm.ss a", "Hms" to "HH.mm.ss")

    private val afternoon = ZonedDateTime.of(2026, 9, 28, 15, 4, 0, 0, ZoneId.of("UTC")).toEpochSecond()

    private fun format(
        clock: TimeFormat,
        platform: Platform,
        zone: String = "UTC",
        range: String = "%1${'$'}s – %2${'$'}s",
    ) = Format(UserPreferences(timeFormat = clock, timezone = zone), platform, range = range)

    // Japanese joins two ends with a wave dash, as its strings.xml has it.
    private val wave = "%1${'$'}s～%2${'$'}s"

    @Test
    fun `a time is written in the language's own shape for the clock the user reads`() {
        val japan = Platform(Locale.JAPANESE, japanese)
        assertEquals("午後3:04", format(TimeFormat.H12, japan).formatTime(afternoon))
        assertEquals("15:04", format(TimeFormat.H24, japan).formatTime(afternoon))
        assertEquals(listOf("hm", "Hm"), japan.skeletons)
        val denmark = Platform(Locale("da"), danish)
        assertEquals("15.04", format(TimeFormat.H24, denmark).formatTime(afternoon))
    }

    @Test
    fun `a time is read in the zone asked for, else the user's`() {
        val japan = Platform(Locale.JAPANESE, japanese)
        assertEquals("0:04", format(TimeFormat.H24, japan).formatTime(afternoon, "Asia/Tokyo"))
        assertEquals("11:04", format(TimeFormat.H24, japan, zone = "America/New_York").formatTime(afternoon))
        assertEquals(listOf("Asia/Tokyo", "America/New_York"), japan.zones)
    }

    @Test
    fun `an hour row names the hour alone on a twelve-hour clock and the whole reading on a twenty-four hour one`() {
        val japan = Platform(Locale.JAPANESE, japanese)
        assertEquals("午後3時", format(TimeFormat.H12, japan).formatHour(15))
        assertEquals("15:00", format(TimeFormat.H24, japan).formatHour(15))
        assertEquals("0:00", format(TimeFormat.H24, japan).formatHour(24))
    }

    @Test
    fun `a date and time keeps its seconds, written in the language's shape`() {
        val japan = Platform(Locale.JAPANESE, japanese)
        val written = format(TimeFormat.H12, japan).formatDateTime(afternoon)
        assertEquals("2026-09-28 午後3:04:00", written)
        assertEquals(listOf("hms"), japan.skeletons)
    }

    @Test
    fun `a long date and a run of days take the language's own patterns`() {
        val japan = Platform(Locale.JAPANESE, japanese + ("EEEEdMMMMy" to "y年M月d日EEEE"))
        assertEquals("2026年9月28日月曜日", format(TimeFormat.H24, japan).formatLongDate(afternoon))
        format(TimeFormat.H24, japan).formatDayRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2))
        val noon = { day: Int, month: Int -> ZonedDateTime.of(2026, month, day, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli() }
        assertEquals(listOf(Triple("dMMMMy", noon(28, 9), noon(2, 10))), japan.spans)
    }

    // As ICU gives them in English, with the day written before the times.
    private val english = mapOf("Hm" to "HH:mm", "EEEEMMMd" to "EEEE, MMM d")

    @Test
    fun `ends at different offsets are joined as the language joins two ends`() {
        val japan = Platform(Locale.JAPANESE, japanese)
        val range = format(TimeFormat.H24, japan, range = wave)
            .formatClockRange(afternoon, afternoon + 3_600, "UTC", "Asia/Tokyo")
        assertEquals("15:04～1:04", range)
    }

    @Test
    fun `two ends written apart are joined as the language joins a range, the end on a line of its own when broken`() {
        val england = Platform(Locale.ENGLISH, english)
        assertEquals("Mon 28 – Tue 29", format(TimeFormat.H24, england).formatRange("Mon 28", "Tue 29"))
        assertEquals("Mon 28 –\nTue 29", format(TimeFormat.H24, england).formatRange("Mon 28", "Tue 29", broken = true))
        assertEquals("Mon 28～\nTue 29", format(TimeFormat.H24, england, range = wave).formatRange("Mon 28", "Tue 29", broken = true))
    }

    @Test
    fun `a span across days puts its end on a line of its own after the join`() {
        val england = Platform(Locale.ENGLISH, english)
        val range = format(TimeFormat.H24, england)
            .formatTimeRange(afternoon, afternoon + 12 * 3_600, now = afternoon)
        assertEquals("Monday, Sep 28 · 15:04 –\nTuesday, Sep 29 · 03:04", range)
        val joined = format(TimeFormat.H24, england, range = wave)
            .formatTimeRange(afternoon, afternoon + 12 * 3_600, now = afternoon)
        assertEquals("Monday, Sep 28 · 15:04～\nTuesday, Sep 29 · 03:04", joined)
    }

    @Test
    fun `a day whose ends are in different zones joins its times as the language does`() {
        val england = Platform(Locale.ENGLISH, english)
        val range = format(TimeFormat.H24, england, range = wave)
            .formatTimeRange(afternoon, afternoon + 3_600, "UTC", "Europe/London", now = afternoon)
        assertEquals("Monday, Sep 28 · 15:04～17:04", range)
    }
}
