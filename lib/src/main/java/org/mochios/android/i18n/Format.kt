// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.mochios.android.R
import android.icu.text.DateIntervalFormat
import android.icu.text.DateTimePatternGenerator
import android.icu.util.DateInterval
import android.icu.util.ULocale
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * How the platform writes a moment in the user's language: the pattern the
 * language writes a skeleton with, such as "hm" for a 12-hour clock or "Hm"
 * for a 24-hour one, and a moment or a span of days written by it. Tests stand
 * in for the platform, whose ICU is not there on the JVM.
 */
interface Clock {
    /** The language's pattern for [skeleton]. */
    fun pattern(skeleton: String): String

    /** [millis] written in [pattern], read in [zone], in the digits 0 to 9. */
    fun write(pattern: String, millis: Long, zone: TimeZone): String

    /** The days from [from] to [to], as the language writes a span of them by [skeleton]. */
    fun span(skeleton: String, from: Long, to: Long, zone: TimeZone): String
}

/**
 * The phone's own ICU, in the language the app speaks. Its digits are 0 to 9
 * whatever the language's own, as the web and the server write a time.
 */
object PlatformClock : Clock {
    private val patterns = ConcurrentHashMap<String, String>()

    private fun locale(): ULocale = ULocale.forLocale(Locale.getDefault()).setKeywordValue("numbers", "latn")

    override fun pattern(skeleton: String): String {
        val locale = Locale.getDefault()
        return patterns.getOrPut("${locale.toLanguageTag()} $skeleton") {
            DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton)
        }
    }

    override fun write(pattern: String, millis: Long, zone: TimeZone): String {
        val format = android.icu.text.SimpleDateFormat(pattern, locale())
        format.timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
        return format.format(Date(millis))
    }

    override fun span(skeleton: String, from: Long, to: Long, zone: TimeZone): String {
        val format = DateIntervalFormat.getInstance(skeleton, locale())
        format.timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
        return format.format(DateInterval(from, to), StringBuffer(), java.text.FieldPosition(0)).toString()
    }
}

/**
 * Locale-aware formatters over [UserPreferences]. The plain formatters are pure
 * and safe to reuse; only [formatTimestamp] needs Composable scope, since its
 * relative strings come from `stringResource`. Times are written the way the
 * user's language writes them, through [clock], on the clock the user chose.
 */
class Format(val preferences: UserPreferences, private val clock: Clock = PlatformClock) {

    /**
     * Epoch seconds → user-format date (no time). [zone] is an IANA zone to
     * read the date in instead of the user's own, for a time that belongs to
     * another place; blank or unknown means the user's.
     */
    fun formatDate(epochSeconds: Long, zone: String? = null): String {
        if (epochSeconds <= 0) return ""
        val date = Date(epochToMillis(epochSeconds))
        return formatDateInternal(date, zoneOf(zone))
    }


    /**
     * Epoch seconds → user-format time of day, without seconds: what a
     * calendar grid, an agenda row and a time picker show, as the user's
     * language writes it: "15:04", "3:04 PM", "午後3:04", "15.04". [zone]
     * reads the clock in another IANA zone than the user's; blank or unknown
     * means the user's.
     */
    fun formatTime(epochSeconds: Long, zone: String? = null): String {
        if (epochSeconds <= 0) return ""
        return clock.write(clock.pattern(twelve("hm", "Hm")), epochToMillis(epochSeconds), zoneOf(zone))
    }

    /**
     * The label on a time grid's hour row: "09:00", or "9 AM" where the user
     * reads a twelve-hour clock, each as the user's language writes it.
     * [hour] is 0 to 24.
     */
    fun formatHour(hour: Int): String {
        val clamped = hour.coerceIn(0, 24) % 24
        return clock.write(clock.pattern(twelve("h", "Hm")), clamped * 3_600_000L, TimeZone.getTimeZone("UTC"))
    }

    /**
     * Epoch seconds → the long date the calendar's headings and an event's
     * summary give: "Monday 28 September 2026", as the user's language writes
     * it. [zone] reads the day in another IANA zone than the user's.
     */
    fun formatLongDate(epochSeconds: Long, zone: String? = null): String {
        if (epochSeconds <= 0) return ""
        return clock.write(clock.pattern("EEEEdMMMMy"), epochToMillis(epochSeconds), zoneOf(zone))
    }

    /**
     * A run of days as the user's language writes one: "14 – 20 September
     * 2026", "28 September – 2 October 2026". The days are dates, read in no
     * zone.
     */
    fun formatDayRange(first: LocalDate, last: LocalDate): String {
        val noon = { day: LocalDate -> day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }
        return clock.span("dMMMMy", noon(first), noon(last), TimeZone.getTimeZone("UTC"))
    }

    /**
     * Epoch seconds → "$date $time" using both user formats. [zone] reads
     * both in another IANA zone than the user's; blank or unknown means the
     * user's.
     */
    fun formatDateTime(epochSeconds: Long, zone: String? = null): String {
        if (epochSeconds <= 0) return ""
        val date = Date(epochToMillis(epochSeconds))
        val tz = zoneOf(zone)
        return "${formatDateInternal(date, tz)} ${formatTimeInternal(date, tz)}"
    }

    /**
     * Format a value with the user's number format (groupings + decimal mark).
     * `decimals` defaults to 0 for integers, 2 otherwise.
     */
    fun formatNumber(value: Number, decimals: Int? = null): String {
        val d = value.toDouble()
        return formatNumberInternal(d, decimals)
    }

    /**
     * Items joined as the app's language writes a list ("A, B, and C",
     * "A、B和C"). Mirrors the web's `formatList`.
     */
    fun formatList(items: List<String>): String =
        android.icu.text.ListFormatter.getInstance(Locale.getDefault()).format(items)

    /**
     * Bytes → "1.2 MB". Number portion uses the user's number format; unit
     * suffixes ("B", "KB", "MB", "GB") stay in Latin to match web.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var i = 0
        while (size >= 1024.0 && i < units.size - 1) {
            size /= 1024.0
            i++
        }
        val numStr = if (i == 0) {
            formatNumberInternal(size, 0)
        } else {
            formatNumberInternal(size, 1)
        }
        return "$numStr ${units[i]}"
    }


    /**
     * The zone [formatDate] and [formatDateTime] render in. Anything bucketing
     * timestamps into days must bucket in this zone, not the device's, or a
     * message near midnight lands under a header showing a different date.
     */
    val timeZone: TimeZone get() = TimeZone.getTimeZone(preferences.timezone)

    /**
     * The zone a caller named, or the user's own when it named none or one
     * the platform does not know. Through `ZoneId` rather than `TimeZone`
     * alone, which answers GMT for any name it does not know.
     */
    private fun zoneOf(zone: String?): TimeZone {
        if (zone.isNullOrBlank()) return timeZone
        return runCatching { TimeZone.getTimeZone(java.time.ZoneId.of(zone)) }.getOrDefault(timeZone)
    }

    private fun formatDateInternal(date: Date, tz: TimeZone = timeZone): String {
        val pattern = when (preferences.dateFormat) {
            DateFormat.YYYY_MM_DD -> "yyyy-MM-dd"
            DateFormat.DD_SLASH_MM_YYYY -> "dd/MM/yyyy"
            DateFormat.DD_DOT_MM_YYYY -> "dd.MM.yyyy"
            DateFormat.MM_SLASH_DD_YYYY -> "MM/dd/yyyy"
            DateFormat.D_MMM_YYYY -> "d MMM yyyy"
        }
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = tz
        return sdf.format(date)
    }

    private fun formatTimeInternal(date: Date, tz: TimeZone = timeZone): String =
        clock.write(clock.pattern(twelve("hms", "Hms")), date.time, tz)

    /** The [skeleton] for a twelve-hour clock where the user reads one, else [otherwise]. */
    private fun twelve(skeleton: String, otherwise: String): String =
        if (preferences.timeFormat == TimeFormat.H12) skeleton else otherwise

    private fun formatNumberInternal(value: Double, decimals: Int?): String {
        val abs = kotlin.math.abs(value)
        val isNeg = value < 0
        val dec = decimals ?: if (abs == abs.toLong().toDouble()) 0 else 2
        val fixed = String.format(Locale.ROOT, "%.${dec}f", abs)
        val parts = fixed.split('.')
        val intPart = parts[0]
        val decPart = if (parts.size > 1) parts[1] else ""

        val fmt = preferences.numberFormat
        val grouped: String = if (fmt.isIndian) {
            // Last 3 digits, then groups of 2
            if (intPart.length <= 3) {
                intPart
            } else {
                val last3 = intPart.takeLast(3)
                var rest = intPart.dropLast(3)
                val pieces = mutableListOf<String>()
                while (rest.length > 2) {
                    pieces.add(0, rest.takeLast(2))
                    rest = rest.dropLast(2)
                }
                if (rest.isNotEmpty()) pieces.add(0, rest)
                pieces.joinToString(fmt.groupChar.toString()) + fmt.groupChar + last3
            }
        } else {
            // Standard groups of 3
            val pieces = mutableListOf<String>()
            var rest = intPart
            while (rest.length > 3) {
                pieces.add(0, rest.takeLast(3))
                rest = rest.dropLast(3)
            }
            pieces.add(0, rest)
            pieces.joinToString(fmt.groupChar.toString())
        }

        val out = if (decPart.isNotEmpty()) "$grouped${fmt.decimalChar}$decPart" else grouped
        return if (isNeg) "-$out" else out
    }

    fun epochToMillis(epoch: Long): Long =
        if (epoch < 1_000_000_000_000L) epoch * 1000L else epoch
}

/**
 * Relative or absolute timestamp per [UserPreferences.timestampDisplay]; the
 * relative strings are localised.
 */
@Composable
fun Format.formatTimestamp(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""

    // Normalise through epochToMillis so the diff matches how formatDateTime
    // reads the same value, and stays correct whether the input is epoch
    // seconds or milliseconds.
    val diff = (System.currentTimeMillis() - epochToMillis(epochSeconds)) / 1000

    val absolute: () -> String = { formatDateTime(epochSeconds) }

    val display = preferences.timestampDisplay
    val useRelative = when (display) {
        TimestampDisplay.RELATIVE -> true
        TimestampDisplay.ABSOLUTE -> false
        // "now" and slight clock skew (diff <= 0) are still recent, so a just
        // sent/received item reads "just now" instead of the absolute date.
        TimestampDisplay.AUTO -> diff < 86_400
    }
    if (!useRelative) return absolute()

    return when {
        diff < 0 -> stringResource(R.string.format_time_just_now)
        diff < 60 -> stringResource(R.string.format_time_just_now)
        diff < 3_600 -> stringResource(R.string.format_time_minutes_ago, formatNumber(diff / 60))
        diff < 86_400 -> stringResource(R.string.format_time_hours_ago, formatNumber(diff / 3_600))
        diff < 604_800 -> stringResource(R.string.format_time_days_ago, formatNumber(diff / 86_400))
        diff < 2_592_000 -> stringResource(R.string.format_time_weeks_ago, formatNumber(diff / 604_800))
        diff < 31_536_000 -> stringResource(R.string.format_time_months_ago, formatNumber(diff / 2_592_000))
        else -> stringResource(R.string.format_time_years_ago, formatNumber(diff / 31_536_000))
    }
}

/**
 * Compact relative timestamp ("5m", "2h", "3d") for tight surfaces; ABSOLUTE
 * and old timestamps use [formatDate].
 */
@Composable
fun Format.formatRelativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""

    val display = preferences.timestampDisplay
    if (display == TimestampDisplay.ABSOLUTE) return formatDate(epochSeconds)

    val diff = (System.currentTimeMillis() - epochToMillis(epochSeconds)) / 1000

    return when {
        diff < 0 -> stringResource(R.string.format_time_just_now)
        diff < 60 -> stringResource(R.string.format_time_just_now)
        diff < 3_600 -> stringResource(R.string.format_time_minutes_short, formatNumber(diff / 60))
        diff < 86_400 -> stringResource(R.string.format_time_hours_short, formatNumber(diff / 3_600))
        diff < 604_800 -> stringResource(R.string.format_time_days_short, formatNumber(diff / 86_400))
        diff < 2_592_000 -> stringResource(R.string.format_time_weeks_short, formatNumber(diff / 604_800))
        diff < 31_536_000 -> stringResource(R.string.format_time_months_short, formatNumber(diff / 2_592_000))
        else -> formatDate(epochSeconds)
    }
}

/**
 * The [Format] for Composables; defaults to [UserPreferences] defaults, so wrap
 * the tree in [FormatProvider].
 */
val LocalFormat = compositionLocalOf { Format(UserPreferences()) }

@Composable
fun FormatProvider(
    manager: PreferencesManager,
    content: @Composable () -> Unit
) {
    val prefs by manager.preferences.collectAsState()
    val format = remember(prefs) { Format(prefs) }
    CompositionLocalProvider(LocalFormat provides format) {
        content()
    }
}
