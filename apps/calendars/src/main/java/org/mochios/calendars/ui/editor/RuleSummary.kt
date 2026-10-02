// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import android.content.res.Resources
import org.mochios.android.i18n.Format
import org.mochios.calendars.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** The parts a summary can put into words; a rule with any other is not described. */
private val SAID = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "COUNT", "UNTIL", "WKST")

private val TOKEN = Regex("^([+-]?\\d{1,2})?(SU|MO|TU|WE|TH|FR|SA)$")

/**
 * A repeat rule the editor cannot express, put into words as the web editor
 * says it: "Every 2 weeks, on Tuesday and Thursday, 10 times". A rule with
 * parts it cannot say answers null, and nothing is shown.
 */
fun ruleSummary(rule: String, resources: Resources, format: Format, locale: Locale): String? {
    val fields = mutableMapOf<String, String>()
    for (part in rule.split(";")) {
        val at = part.indexOf('=')
        if (at <= 0) return null
        fields[part.substring(0, at).trim().uppercase()] = part.substring(at + 1).trim().uppercase()
    }
    if (fields.keys.any { it !in SAID }) return null

    val interval = (fields["INTERVAL"] ?: "1").toIntOrNull()?.takeIf { it >= 1 } ?: return null
    val (single, many) = when (fields["FREQ"]) {
        "DAILY" -> R.string.calendars_rule_day to R.plurals.calendars_rule_days
        "WEEKLY" -> R.string.calendars_rule_week to R.plurals.calendars_rule_weeks
        "MONTHLY" -> R.string.calendars_rule_month to R.plurals.calendars_rule_months
        "YEARLY" -> R.string.calendars_rule_year to R.plurals.calendars_rule_years
        else -> return null
    }
    val parts = mutableListOf(
        if (interval == 1) resources.getString(single) else resources.getQuantityString(many, interval, format.formatNumber(interval)),
    )

    fields["BYDAY"]?.let { value -> parts.add(weekdays(value, resources, format, locale) ?: return null) }
    fields["BYMONTHDAY"]?.let { value -> parts.add(monthdays(value, resources, format) ?: return null) }
    fields["BYMONTH"]?.let { value ->
        val names = value.split(",").map { token ->
            val month = token.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            monthName(Month.of(month), locale)
        }
        parts.add(resources.getString(R.string.calendars_rule_in, format.formatList(names)))
    }
    fields["COUNT"]?.let { value ->
        val times = value.toIntOrNull()?.takeIf { it >= 1 } ?: return null
        parts.add(
            if (times == 1) {
                resources.getString(R.string.calendars_rule_once)
            } else {
                resources.getQuantityString(R.plurals.calendars_rule_times, times, format.formatNumber(times))
            },
        )
    }
    fields["UNTIL"]?.let { value ->
        val found = Regex("^(\\d{4})(\\d{2})(\\d{2})").find(value) ?: return null
        val (year, month, day) = found.destructured
        val date = runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull() ?: return null
        // Noon of the day in UTC is the same day in the zone it is read in.
        val noon = date.atTime(12, 0).toEpochSecond(ZoneOffset.UTC)
        parts.add(resources.getString(R.string.calendars_rule_until, format.formatDate(noon, "UTC")))
    }
    return parts.reduce { first, second -> resources.getString(R.string.calendars_rule_join, first, second) }
}

/** The weekdays a rule falls on: "on Tuesday and Thursday", "on the second Tuesday". */
private fun weekdays(value: String, resources: Resources, format: Format, locale: Locale): String? {
    val plain = mutableListOf<String>()
    val ordinal = mutableListOf<String>()
    for (token in value.split(",")) {
        val found = TOKEN.find(token.trim()) ?: return null
        val index = WEEKDAYS.indexOf(found.groupValues[2])
        // The rule indexes Sunday 0; java.time indexes Monday 1.
        val day = DayOfWeek.of(if (index == 0) 7 else index).getDisplayName(TextStyle.FULL, locale)
        val position = found.groupValues[1]
        if (position.isEmpty()) {
            plain.add(day)
            continue
        }
        val key = when (position.removePrefix("+").toIntOrNull()) {
            1 -> R.string.calendars_rule_first
            2 -> R.string.calendars_rule_second
            3 -> R.string.calendars_rule_third
            4 -> R.string.calendars_rule_fourth
            5 -> R.string.calendars_rule_fifth
            -1 -> R.string.calendars_rule_last
            else -> return null
        }
        ordinal.add(resources.getString(key, day))
    }
    if (plain.isNotEmpty() && ordinal.isNotEmpty()) return null
    if (ordinal.isNotEmpty()) return format.formatList(ordinal)
    return resources.getString(R.string.calendars_rule_on, format.formatList(plain))
}

/** The days of the month a rule falls on: "on days 1 and 15", "on the last day of the month". */
private fun monthdays(value: String, resources: Resources, format: Format): String? {
    val tokens = value.split(",").map { it.trim().toIntOrNull() ?: return null }
    if (tokens == listOf(-1)) return resources.getString(R.string.calendars_rule_end)
    if (tokens.any { it !in 1..31 }) return null
    val days = format.formatList(tokens.map { format.formatNumber(it) })
    return if (tokens.size == 1) {
        resources.getString(R.string.calendars_rule_monthday, days)
    } else {
        resources.getQuantityString(R.plurals.calendars_rule_monthdays, tokens.size, days)
    }
}

/**
 * A month's name as it runs in a sentence, the standalone form the web's
 * month-and-year writing uses; a platform with no standalone names for the
 * language answers a number, and then the plain form is used.
 */
private fun monthName(month: Month, locale: Locale): String {
    val standalone = month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    return if (standalone.any { it.isLetter() }) standalone else month.getDisplayName(TextStyle.FULL, locale)
}
