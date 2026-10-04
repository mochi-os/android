// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import org.mochios.calendars.model.Instance
import org.mochios.calendars.ui.editor.EventForm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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

/** How long a drag rests at a grid's edge before the page turns, and again for each further page. */
const val PAGE_HOLD = 500L

/**
 * A page turned by resting a drag at a grid's edge: the first turn comes
 * once the finger has rested there for [PAGE_HOLD], and another each hold
 * for as long as it stays. Leaving the edge, or crossing to the other,
 * starts the count again.
 */
class Dwell {
    private var since: Long? = null
    private var direction = 0

    /** [direction] is 0 away from any edge, -1 at the start and 1 at the end; returns the page to turn now, or 0. */
    fun step(direction: Int, now: Long): Int {
        if (direction != this.direction) {
            this.direction = direction
            since = if (direction == 0) null else now
            return 0
        }
        val began = since ?: return 0
        if (now - began < PAGE_HOLD) return 0
        since = now
        return direction
    }

    fun reset() {
        since = null
        direction = 0
    }
}

/**
 * The page a drag resting at [x] wants, from the columns' outer edges
 * [first] and [last], the sides the first and last days are drawn on: -1
 * within [edge] of the first day's side or beyond it, over the gutter, 1
 * within it of the last day's side or beyond, 0 between. The first day is
 * on the left in a left-to-right layout and on the right in a right-to-left
 * one. The caller asks only while the finger is over the grid.
 */
fun edge(x: Float, first: Float, last: Float, edge: Float): Int {
    val width = kotlin.math.abs(last - first)
    val across = if (last >= first) x - first else first - x
    return when {
        across < edge -> -1
        across > width - edge -> 1
        else -> 0
    }
}

/**
 * A form made all day for a block dropped in the all-day band: from the day
 * [days] after its own first day, as the views read that day in [zone], over
 * [length] days. An all-day form holds the UTC midnights of its first day
 * and of the day after its last.
 */
fun whole(form: EventForm, days: Long, length: Long, zone: ZoneId): EventForm {
    val first = Instant.ofEpochSecond(form.start).atZone(if (form.allday) ZoneOffset.UTC else zone).toLocalDate().plusDays(days)
    return form.copy(
        allday = true,
        start = first.atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
        finish = first.plusDays(maxOf(1L, length)).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
    )
}

/**
 * A form made timed for an all-day bar dropped in the time grid: on the day
 * [days] after its own first day, from [hours] past midnight by the [user]'s
 * clock, lasting [length] seconds.
 */
fun clocked(form: EventForm, days: Long, hours: Float, length: Long, user: ZoneId): EventForm {
    val first = Instant.ofEpochSecond(form.start).atZone(if (form.allday) ZoneOffset.UTC else user).toLocalDate()
    val begins = at(first.plusDays(days), hours, user)
    return form.copy(allday = false, start = begins, finish = begins + length)
}
