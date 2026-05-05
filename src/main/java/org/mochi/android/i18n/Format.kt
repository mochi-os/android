package org.mochi.android.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.mochi.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Locale-aware formatters that consult the user's [UserPreferences].
 *
 * Mirror of web's `useFormat()` in `lib/web/src/hooks/use-format.ts`. The
 * formatters in this class are pure — they don't read any global state,
 * which means a single instance is safe to capture, copy, and reuse.
 *
 * Pure-string formatters ([formatDate], [formatTime], [formatDateTime],
 * [formatNumber], [formatFileSize]) do not require Composable scope. The
 * relative-time formatter [formatTimestamp] does, because it pulls
 * "X minutes ago" / "yesterday" from `stringResource`. Use [LocalFormat]
 * inside Composables; use [PreferencesManager.format] for ViewModels.
 */
class Format(val preferences: UserPreferences) {

    /** Epoch seconds → user-format date (no time). */
    fun formatDate(epochSeconds: Long): String {
        if (epochSeconds <= 0) return ""
        val date = Date(epochToMillis(epochSeconds))
        return formatDateInternal(date)
    }

    /** Epoch seconds → user-format time (no date). */
    fun formatTime(epochSeconds: Long): String {
        if (epochSeconds <= 0) return ""
        return formatTimeInternal(Date(epochToMillis(epochSeconds)))
    }

    /** Epoch seconds → "$date $time" using both user formats. */
    fun formatDateTime(epochSeconds: Long): String {
        if (epochSeconds <= 0) return ""
        val date = Date(epochToMillis(epochSeconds))
        return "${formatDateInternal(date)} ${formatTimeInternal(date)}"
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

    /** Currency: minor units → formatted price (e.g. 1500 + "£" → "£15.00"). */
    fun formatCurrency(amountMinor: Long, symbol: String): String {
        return symbol + formatNumberInternal(amountMinor / 100.0, 2)
    }

    private fun formatDateInternal(date: Date): String {
        val tz = TimeZone.getTimeZone(preferences.timezone)
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

    private fun formatTimeInternal(date: Date): String {
        val tz = TimeZone.getTimeZone(preferences.timezone)
        val pattern = when (preferences.timeFormat) {
            TimeFormat.H12 -> "h:mm:ss a"
            TimeFormat.H24 -> "HH:mm:ss"
        }
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = tz
        return sdf.format(date)
    }

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
 * Composable wrapper around the [Format.formatTimestamp] logic in web. Picks
 * relative or absolute display from [UserPreferences.timestampDisplay], and
 * draws the relative-time strings from `stringResource` so they're localised.
 *
 * Use this for any UI that wants "5m ago" / yesterday / fall-back-to-date.
 */
@Composable
fun Format.formatTimestamp(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""

    val now = System.currentTimeMillis() / 1000
    val diff = now - epochSeconds

    val absolute: () -> String = { formatDateTime(epochSeconds) }

    val display = preferences.timestampDisplay
    val useRelative = when (display) {
        TimestampDisplay.RELATIVE -> true
        TimestampDisplay.ABSOLUTE -> false
        TimestampDisplay.AUTO -> diff in 0 until 86_400
    }
    if (!useRelative) return absolute()

    return when {
        diff < 0 -> stringResource(R.string.format_time_just_now)
        diff < 60 -> stringResource(R.string.format_time_just_now)
        diff < 3_600 -> stringResource(R.string.format_time_minutes_ago, (diff / 60).toInt())
        diff < 86_400 -> stringResource(R.string.format_time_hours_ago, (diff / 3_600).toInt())
        diff < 604_800 -> stringResource(R.string.format_time_days_ago, (diff / 86_400).toInt())
        diff < 2_592_000 -> stringResource(R.string.format_time_weeks_ago, (diff / 604_800).toInt())
        diff < 31_536_000 -> stringResource(R.string.format_time_months_ago, (diff / 2_592_000).toInt())
        else -> stringResource(R.string.format_time_years_ago, (diff / 31_536_000).toInt())
    }
}

/**
 * Compact relative timestamp ("5m", "2h", "3d", "2w") for tight UI surfaces.
 * Falls back to [Format.formatDate] for old timestamps. Mirrors web's
 * `formatRelativeTime` in `lib/web/src/lib/locale-format.ts`.
 */
@Composable
fun Format.formatRelativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""

    val now = System.currentTimeMillis() / 1000
    val diff = now - epochSeconds

    return when {
        diff < 0 -> stringResource(R.string.format_time_just_now)
        diff < 60 -> stringResource(R.string.format_time_just_now)
        diff < 3_600 -> stringResource(R.string.format_time_minutes_short, (diff / 60).toInt())
        diff < 86_400 -> stringResource(R.string.format_time_hours_short, (diff / 3_600).toInt())
        diff < 604_800 -> stringResource(R.string.format_time_days_short, (diff / 86_400).toInt())
        diff < 2_592_000 -> stringResource(R.string.format_time_weeks_short, (diff / 604_800).toInt())
        diff < 31_536_000 -> stringResource(R.string.format_time_months_short, (diff / 2_592_000).toInt())
        else -> formatDate(epochSeconds)
    }
}

/**
 * The [Format] instance to use in Composables. The default is built from
 * [UserPreferences] defaults — any app that wants real preferences should
 * wrap its tree in [FormatProvider].
 */
val LocalFormat = compositionLocalOf { Format(UserPreferences()) }

/**
 * Compose entry point. Subscribes to [PreferencesManager.preferences] and
 * rebuilds the [Format] (and therefore everything that reads [LocalFormat])
 * every time the preferences change.
 */
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
