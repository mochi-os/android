// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** How often an event repeats; [CUSTOM] is whatever the plain choices cannot say. */
enum class Frequency {
    NEVER,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM,
}

/** The weekday tokens an `RRULE` uses, indexed as the preferences index days: Sunday is 0. */
val WEEKDAYS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

/**
 * The editor's Repeat field. [interval] is every how many units, [days] the
 * weekdays a weekly rule picks (Sunday 0 through Saturday 6), and the series
 * ends either at [until] (epoch seconds) or after [count] occurrences, never
 * both.
 */
data class Recurrence(
    val frequency: Frequency = Frequency.NEVER,
    val interval: Int = 1,
    val days: Set<Int> = emptySet(),
    val until: Long? = null,
    val count: Int? = null,
) {

    /**
     * The `RRULE` value, or null when the event does not repeat. `INTERVAL` is
     * left out when it is 1, which is its default, so a plain weekly rule
     * reads as one.
     */
    fun rule(): String? {
        val unit = when (frequency) {
            Frequency.NEVER -> return null
            Frequency.DAILY -> "DAILY"
            Frequency.WEEKLY, Frequency.CUSTOM -> "WEEKLY"
            Frequency.MONTHLY -> "MONTHLY"
            Frequency.YEARLY -> "YEARLY"
        }
        val parts = mutableListOf("FREQ=$unit")
        if (interval > 1) parts.add("INTERVAL=$interval")
        if (days.isNotEmpty() && (frequency == Frequency.WEEKLY || frequency == Frequency.CUSTOM)) {
            parts.add("BYDAY=" + days.sorted().joinToString(",") { WEEKDAYS[it] })
        }
        when {
            count != null && count > 0 -> parts.add("COUNT=$count")
            until != null && until > 0 -> parts.add("UNTIL=" + STAMP.format(Instant.ofEpochSecond(until).atZone(ZoneOffset.UTC)))
        }
        return parts.joinToString(";")
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    }
}

/**
 * The Repeat field for an `RRULE`. A rule the editor's plain choices cannot
 * express — one with a `BYMONTHDAY`, a `BYSETPOS`, an interval beside picked
 * weekdays — comes back as [Frequency.CUSTOM] with whatever of it the editor
 * can show, so saving the event again does not quietly simplify it.
 */
fun recurrence(rule: String?): Recurrence {
    if (rule.isNullOrBlank()) return Recurrence()
    val parts = rule.split(";")
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator).uppercase() to part.substring(separator + 1)
        }
        .toMap()
    val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val days = parts["BYDAY"].orEmpty().split(",")
        .mapNotNull { token -> WEEKDAYS.indexOf(token.trim().uppercase().takeLast(2)).takeIf { it >= 0 } }
        .toSet()
    val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
    val until = parts["UNTIL"]?.let { moment(it) }
    val plain = parts.keys.all { it in PLAIN }
    val frequency = when {
        !plain -> Frequency.CUSTOM
        parts["FREQ"]?.uppercase() == "DAILY" -> Frequency.DAILY
        parts["FREQ"]?.uppercase() == "WEEKLY" ->
            if (interval > 1 || days.size > 1) Frequency.CUSTOM else Frequency.WEEKLY
        parts["FREQ"]?.uppercase() == "MONTHLY" -> Frequency.MONTHLY
        parts["FREQ"]?.uppercase() == "YEARLY" -> Frequency.YEARLY
        else -> Frequency.CUSTOM
    }
    return Recurrence(frequency, interval, days, until, count)
}

/** The parts the editor's plain choices can hold; anything else makes a rule custom. */
private val PLAIN = setOf("FREQ", "INTERVAL", "BYDAY", "COUNT", "UNTIL")

/** An `UNTIL` value as epoch seconds, null when it will not parse. */
private fun moment(value: String): Long? {
    val text = value.trim()
    return try {
        when {
            text.length == 8 ->
                java.time.LocalDate.parse(text, DATE).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            text.endsWith("Z") ->
                java.time.LocalDateTime.parse(text.dropLast(1), PLAIN_STAMP).toEpochSecond(ZoneOffset.UTC)
            else ->
                java.time.LocalDateTime.parse(text, PLAIN_STAMP).toEpochSecond(ZoneOffset.UTC)
        }
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val PLAIN_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

/** The reminders the editor offers, in minutes before the start; -1 is none. */
val REMINDERS = listOf(-1, 0, 5, 15, 30, 60, 1440)
