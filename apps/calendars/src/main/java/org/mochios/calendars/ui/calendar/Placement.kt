// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import org.mochios.calendars.model.Instance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The shortest a timed block is drawn, in hours, whatever its length. */
const val MINIMUM = 0.25f

/** One end of a timed occurrence by the clock: its day and its minutes since midnight. */
data class Clock(val day: LocalDate, val minutes: Int)

/**
 * Where a timed occurrence's two ends fall by the clock. [backwards] means
 * the finish reads before the start - Auckland Monday 10:00 to Tahiti Sunday
 * 15:00 - which is only possible across zones, and which the views draw on
 * the start day alone.
 */
data class Ends(val from: Clock, val to: Clock, val backwards: Boolean)

/**
 * A timed occurrence's block on one day, in hours from midnight. [backwards]
 * marks a block standing in for an occurrence whose end reads before its
 * start.
 */
data class Cut(val from: Float, val to: Float, val backwards: Boolean = false)

/**
 * The zone a named end reads in when the views show events in their own
 * zones: its own, or [user] when it names none or one the platform does not
 * know.
 */
fun zoneOf(named: String?, user: ZoneId): ZoneId {
    if (named.isNullOrBlank()) return user
    return runCatching { ZoneId.of(named) }.getOrDefault(user)
}

/**
 * The IANA zone an end's clock text reads in, or null for the user's own:
 * the end's own zone only when [zones] is on and it names one.
 */
fun clockZone(named: String?, zones: Boolean): String? = named?.takeIf { zones && it.isNotBlank() }

/**
 * Each end of a timed occurrence at its wall-clock time. With [zones] on,
 * an end reads in the zone it was written in; otherwise both read in the
 * [user]'s. A finish on the stroke of midnight belongs to the day before.
 */
fun ends(instance: Instance, user: ZoneId, zones: Boolean): Ends {
    val startZone = if (zones) zoneOf(instance.zone?.start, user) else user
    val finishZone = if (zones) zoneOf(instance.zone?.finish, user) else user
    val begins = Instant.ofEpochSecond(instance.start).atZone(startZone)
    val finishes = Instant.ofEpochSecond(maxOf(instance.start, instance.finish)).atZone(finishZone)
    val from = Clock(begins.toLocalDate(), begins.hour * 60 + begins.minute)
    var to = Clock(finishes.toLocalDate(), finishes.hour * 60 + finishes.minute)
    if (to.minutes == 0 && to.day.isAfter(from.day)) to = Clock(to.day.minusDays(1), 1440)
    val backwards = to.day.isBefore(from.day) || (to.day == from.day && to.minutes < from.minutes)
    return Ends(from, to, backwards)
}

/**
 * The days a timed occurrence is drawn on, first to last: from the start's
 * day to the finish's, or the start day alone when it ends before it starts.
 */
fun days(instance: Instance, user: ZoneId, zones: Boolean): Pair<LocalDate, LocalDate> {
    val ends = ends(instance, user, zones)
    return ends.from.day to if (ends.backwards) ends.from.day else ends.to.day
}

/**
 * The block a timed occurrence puts on [day], or null when it does not touch
 * the day. An occurrence crossing midnight puts a block on each day it
 * covers, running to the day's end and from the next day's start. One that
 * ends before it starts by the clock sits on its start day as short as a
 * block gets, marked backwards. An occurrence with no length is drawn half
 * an hour tall.
 */
fun cut(instance: Instance, day: LocalDate, user: ZoneId, zones: Boolean): Cut? {
    val ends = ends(instance, user, zones)
    if (day.isBefore(ends.from.day)) return null
    if (ends.backwards) {
        if (day != ends.from.day) return null
        val from = ends.from.minutes / 60f
        return Cut(from, minOf(24f, from + MINIMUM), backwards = true)
    }
    if (day.isAfter(ends.to.day)) return null
    val from = if (day == ends.from.day) ends.from.minutes / 60f else 0f
    val to = when {
        day != ends.to.day -> 24f
        instance.finish > instance.start -> ends.to.minutes / 60f
        else -> minOf(24f, from + 0.5f)
    }
    return Cut(from, maxOf(to, from + MINIMUM))
}
