// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.editor

import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Another event on the strip's day, as the minutes of the day it covers. */
data class Block(val start: Int, val finish: Int, val summary: String, val colour: String)

/** The strip's event: its day, and its ends as minutes of that day. */
data class Span(val day: LocalDate, val start: Int, val finish: Int)

/**
 * The day strip under the editor's times: one day on a line, the event a
 * block on it that drags, stretches and jumps to a tap. Times are minutes
 * since midnight by the clock of the event's zone; the last is 23:59, since
 * the editor keeps an event that ends at midnight on the next day. The web
 * editor's strip reads the same way.
 */
object Strip {
    /** What a drag or a tap on the strip lands on. */
    const val STEP = 5

    /** The last minute of the day. */
    const val LAST = 24 * 60 - 1

    private fun snapped(minutes: Double): Int = Math.round(minutes / STEP).toInt() * STEP

    private fun zoneOf(name: String): ZoneId? = runCatching { ZoneId.of(name) }.getOrNull()

    private fun minuteOf(instant: Long, zone: ZoneId): Int =
        Instant.ofEpochSecond(instant).atZone(zone).toLocalTime().toSecondOfDay() / 60

    /**
     * The strip's span for a timed event that starts and ends on one day in
     * one zone, or null: a flight across zones or an event over several days
     * is set with the fields alone. Two names of one zone, Asia/Calcutta and
     * Asia/Kolkata, are one zone.
     */
    fun span(start: Long, finish: Long, allday: Boolean, zone: Zone): Span? {
        if (allday) return null
        val begins = zoneOf(zone.start) ?: return null
        val ends = zoneOf(zone.finish) ?: return null
        if (begins.rules != ends.rules) return null
        val day = Instant.ofEpochSecond(start).atZone(begins).toLocalDate()
        if (Instant.ofEpochSecond(finish).atZone(begins).toLocalDate() != day) return null
        return Span(day, minuteOf(start, begins), minuteOf(finish, begins))
    }

    /** The instant a minute of the strip's day is, read in [zone]. */
    fun instant(day: LocalDate, minutes: Int, zone: String): Long =
        day.atStartOfDay().plusMinutes(minutes.toLong())
            .atZone(zoneOf(zone) ?: ZoneId.systemDefault()).toEpochSecond()

    /**
     * The span dragged along by [delta] minutes, its length kept: the start
     * lands on a step and the span stays within the day. Returns the new start.
     */
    fun moved(start: Int, finish: Int, delta: Double): Int =
        snapped(start + delta).coerceIn(0, LAST - (finish - start))

    /**
     * The end dragged by [delta] minutes: on a step, at least a step after
     * the start, and within the day.
     */
    fun resized(start: Int, finish: Int, delta: Double): Int =
        snapped(finish + delta).coerceIn(start + STEP, LAST)

    /**
     * The start for the span moved to begin at the step the minute [at] falls
     * in, its length kept, as a tap on an empty part of the day moves it.
     */
    fun placed(start: Int, finish: Int, at: Double): Int =
        (Math.floor(at / STEP).toInt() * STEP).coerceIn(0, LAST - (finish - start))

    /**
     * The timed occurrences that fall on [day] in [zone], as the minutes of
     * that day they cover, those of the event being edited, [exclude], left
     * out: with [occurrence] non-zero only the one listed at that start.
     */
    fun blocks(instances: List<Instance>, day: LocalDate, zone: String, exclude: String?, occurrence: Long): List<Block> {
        val id = zoneOf(zone) ?: return emptyList()
        val from = day.atStartOfDay(id).toEpochSecond()
        val to = day.plusDays(1).atStartOfDay(id).toEpochSecond()
        return instances.mapNotNull { instance ->
            val edited = exclude != null && instance.event == exclude &&
                (occurrence == 0L || instance.start == occurrence)
            if (instance.allday || edited || instance.finish <= from || instance.start >= to) return@mapNotNull null
            val start = if (instance.start <= from) 0 else minuteOf(instance.start, id)
            val finish = if (instance.finish >= to) 24 * 60 else minuteOf(instance.finish, id)
            Block(start, maxOf(finish, start + STEP), instance.summary, instance.colour)
        }
    }
}
