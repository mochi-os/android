// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import org.mochios.android.sync.CalendarsMapping
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** How often an event repeats. */
enum class Frequency {
    NEVER,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

/** How a series ends: never, on a day, or after so many occurrences. */
enum class Ending {
    NEVER,
    UNTIL,
    COUNT,
}

/** The weekday tokens an `RRULE` uses, indexed as the preferences index days: Sunday is 0. */
val WEEKDAYS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

/**
 * The editor's Repeat field, as the web editor holds it. [interval] is every
 * how many units, [days] the weekdays a weekly rule picks (Sunday 0 through
 * Saturday 6), and [ending] says whether the series stops on the day [until]
 * or after [count] occurrences. [rule] is the `RRULE` as it was read, written
 * back as it is for as long as the settings are untouched, so a rule the
 * settings cannot express, such as the second Tuesday of every month,
 * survives a title change or a move; [expressible] says whether the settings
 * can say all of it.
 */
data class Recurrence(
    val frequency: Frequency = Frequency.NEVER,
    val interval: Int = 1,
    val days: Set<Int> = emptySet(),
    val ending: Ending = Ending.NEVER,
    val until: LocalDate? = null,
    val count: Int = 10,
    val rule: String? = null,
    val expressible: Boolean = true,
) {

    /** Whether one of the plain choices says it all, with no interval, weekdays or end. */
    val plain: Boolean get() = interval == 1 && days.isEmpty() && ending == Ending.NEVER

    /**
     * The `RRULE` value, or null when the event does not repeat: the rule as
     * read while the settings are untouched, else one built from them, with
     * `INTERVAL` left out when it is 1, which is its default, so a plain
     * weekly rule reads as one. An end on a day takes in the whole of it: the
     * day itself for an [allday] series, whose `UNTIL` is a date as its start
     * is, else the last second of the day in [zone], in UTC. A rule kept as
     * read has its `UNTIL` rewritten in the other form when the series has
     * turned all day or back, so a date never ends a timed series nor a time
     * a whole-day one.
     */
    fun rule(zone: String = CalendarsMapping.UTC, allday: Boolean = false): String? {
        if (!rule.isNullOrBlank()) return kept(rule, zone, allday)
        if (frequency == Frequency.NEVER) return null
        val parts = mutableListOf("FREQ=" + frequency.name)
        if (interval > 1) parts.add("INTERVAL=$interval")
        if (days.isNotEmpty() && frequency == Frequency.WEEKLY) {
            parts.add("BYDAY=" + days.sorted().joinToString(",") { WEEKDAYS[it] })
        }
        when (ending) {
            Ending.UNTIL -> until?.let { parts.add("UNTIL=" + last(it, zone, allday)) }
            Ending.COUNT -> parts.add("COUNT=${count.coerceAtLeast(1)}")
            Ending.NEVER -> Unit
        }
        return parts.joinToString(";")
    }

    /** [rule] with its `UNTIL` in the form the series' start takes. */
    private fun kept(rule: String, zone: String, allday: Boolean): String {
        val day = until ?: return rule
        val parts = rule.split(";")
        val index = parts.indexOfFirst { it.substringBefore('=').trim().equals("UNTIL", ignoreCase = true) }
        if (index < 0) return rule
        val dated = parts[index].substringAfter('=').trim().length == 8
        if (dated == allday) return rule
        return parts.toMutableList().apply { this[index] = "UNTIL=" + last(day, zone, allday) }.joinToString(";")
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        fun last(day: LocalDate, zone: String, allday: Boolean): String {
            if (allday) return DATE.format(day)
            val id = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneOffset.UTC)
            val ends = day.plusDays(1).atStartOfDay(id).minusSeconds(1)
            return STAMP.format(ends.withZoneSameInstant(ZoneOffset.UTC))
        }
    }
}

/**
 * The Repeat field for an `RRULE`, its end day read in [zone], the zone the
 * event starts in. A rule the settings cannot express - one with a
 * `BYMONTHDAY`, a `BYSETPOS`, an ordinal weekday - keeps [Recurrence.rule]
 * and is marked not [Recurrence.expressible], so saving the event again does
 * not quietly simplify it.
 */
fun recurrence(rule: String?, zone: String = CalendarsMapping.UTC): Recurrence {
    if (rule.isNullOrBlank()) return Recurrence()
    val parts = rule.split(";")
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator).trim().uppercase() to part.substring(separator + 1).trim()
        }
        .toMap()
    val named = parts["FREQ"]?.uppercase()
    val frequency = Frequency.entries.firstOrNull { it != Frequency.NEVER && it.name == named } ?: Frequency.NEVER
    val interval = parts["INTERVAL"]?.toIntOrNull()?.takeIf { it > 0 } ?: 1
    val tokens = parts["BYDAY"].orEmpty().split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    val days = tokens.mapNotNull { token -> WEEKDAYS.indexOf(token.takeLast(2)).takeIf { it >= 0 } }.toSet()
    val until = parts["UNTIL"]?.let { day(it, zone) }
    val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
    val ending = when {
        until != null -> Ending.UNTIL
        count != null -> Ending.COUNT
        else -> Ending.NEVER
    }
    // The settings say all of a rule only when every part is one they hold,
    // the weekdays are plain tokens on a weekly rule, and it ends one way.
    val expressible = parts.keys.all { it in PLAIN } &&
        frequency != Frequency.NEVER &&
        (tokens.isEmpty() || (frequency == Frequency.WEEKLY && tokens.all { it in WEEKDAYS })) &&
        !(parts.containsKey("UNTIL") && parts.containsKey("COUNT"))
    return Recurrence(frequency, interval, days, ending, until, count ?: 10, rule.trim(), expressible)
}

/** The parts the editor's settings can hold; anything else makes a rule one they cannot express. */
private val PLAIN = setOf("FREQ", "INTERVAL", "BYDAY", "COUNT", "UNTIL")

/**
 * An `UNTIL` value as the day it falls on in [zone]: a date as written, a
 * UTC or floating time read in that zone. Null when it will not parse.
 */
private fun day(value: String, zone: String): LocalDate? {
    val text = value.trim()
    return try {
        when {
            text.length == 8 -> LocalDate.parse(text, DATE)
            text.endsWith("Z") -> LocalDateTime.parse(text.dropLast(1), PLAIN_STAMP)
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneOffset.UTC))
                .toLocalDate()
            else -> LocalDateTime.parse(text, PLAIN_STAMP).toLocalDate()
        }
    } catch (_: DateTimeParseException) {
        null
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val PLAIN_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

/** The reminders the editor offers, in minutes before the start. */
val REMINDER_LEADS = listOf(0, 5, 15, 30, 60, 1440)

/**
 * The choices for one of an event's reminders: those offered, and the one it
 * has when that is not among them, as a reminder another calendar set can be.
 */
fun reminderLeads(current: Int): List<Int> =
    if (current in REMINDER_LEADS) REMINDER_LEADS else (REMINDER_LEADS + current).sorted()

/** The reminders a new event opens with, from the default reminder preference; -1 is none. */
fun defaultReminders(preference: Int): List<Int> = if (preference >= 0) listOf(preference) else emptyList()

/**
 * The reminder "Add reminder" adds: at the time of the event first, then each
 * one longer, the shortest offered the event lacks. With every one taken, the
 * longest again.
 */
fun nextReminder(reminders: List<Int>): Int =
    REMINDER_LEADS.firstOrNull { it !in reminders } ?: REMINDER_LEADS.last()
