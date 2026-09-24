// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import org.mochios.calendars.model.Instance
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** The grid a dragged block snaps to, in hours: a quarter of an hour. */
const val SNAP = 0.25f

/** [hours] rounded to the nearest quarter of an hour. */
fun snap(hours: Float): Float = (hours / SNAP).roundToInt() * SNAP

/**
 * A lifted block carried [hours] down the day, negative for up: it keeps its
 * length, snaps to the quarter hour, and stops at the day's ends.
 */
fun moved(cut: Cut, hours: Float): Cut {
    val length = cut.to - cut.from
    val from = snap(cut.from + hours).coerceIn(0f, (24f - length).coerceAtLeast(0f))
    return Cut(from, from + length, cut.backwards)
}

/**
 * A block whose end handle was dragged to [hours] from midnight: the end
 * snaps to the quarter hour, comes no nearer the start than one, and goes
 * no later than the day's end.
 */
fun resized(cut: Cut, hours: Float): Cut {
    val least = minOf(cut.from + SNAP, 24f)
    return Cut(cut.from, snap(hours).coerceIn(least, 24f), cut.backwards)
}

/**
 * The instant [hours] from midnight on [day] by the clock in [zone], to the
 * minute; 24 is the next day's midnight. A time a clock change skips reads
 * as the first one after it.
 */
fun at(day: LocalDate, hours: Float, zone: ZoneId): Long {
    val minutes = (hours * 60).roundToInt()
    if (minutes >= 1440) return day.plusDays(1).atStartOfDay(zone).toEpochSecond()
    return day.atTime(LocalTime.ofSecondOfDay(minutes.coerceAtLeast(0) * 60L)).atZone(zone).toEpochSecond()
}

/**
 * The occurrence's ends, epoch seconds, after its block on [day] went from
 * [before] to [after] on [target]. A move carries the whole occurrence by
 * as far as the block moved, so one that crosses midnight and stands on
 * this day for part of itself moves entirely, and the length is kept; a
 * [resize] leaves the start alone and ends the occurrence where the block's
 * bottom now is. The start moves by the clock the block's top is placed
 * by, [startZone], and the bottom by [finishZone].
 */
fun dropped(
    instance: Instance,
    day: LocalDate,
    before: Cut,
    target: LocalDate,
    after: Cut,
    startZone: ZoneId,
    finishZone: ZoneId,
    resize: Boolean,
): Pair<Long, Long> {
    if (resize) return instance.start to at(day, after.to, finishZone)
    val shift = at(target, after.from, startZone) - at(day, before.from, startZone)
    return instance.start + shift to instance.finish + shift
}
