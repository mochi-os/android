// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.calendar

import java.time.LocalDate
import java.time.ZoneId

/**
 * How many days one page of the list view covers. A quarter at a time: long
 * enough that a sparse calendar still fills a screen, short enough that the
 * server's own listing limit is never the thing that stops a page.
 */
const val PAGE = 92L

/**
 * Most pages the list view will hold. A calendar whose events never end —
 * a rule without an end date, or the birthdays calendar — would otherwise
 * page forward for as long as the reader kept scrolling.
 */
const val PAGES = 40

/**
 * Where a user's events begin and end, from `-/events/bounds`. [first] and
 * [last] are epoch seconds, 0 when there is nothing to bound. [endless] means
 * something in the visible calendars recurs without an end, so there is
 * always another page forward.
 */
data class Bounds(
    val first: Long = 0,
    val last: Long = 0,
    val endless: Boolean = false,
)

/** A half-open span of days, epoch seconds: `[start, finish)`. */
data class Page(val start: Long, val finish: Long)

/**
 * The page [index] pages from [anchor], which is page 0 and begins on the
 * anchor day itself. A negative index reaches back.
 */
fun page(anchor: LocalDate, index: Int, zone: ZoneId): Page {
    val start = anchor.plusDays(index * PAGE)
    return Page(
        start.atStartOfDay(zone).toEpochSecond(),
        start.plusDays(PAGE).atStartOfDay(zone).toEpochSecond(),
    )
}

/**
 * Whether a page before [earliest] is worth asking for: the earliest page
 * held still begins after the first event there is. A calendar with nothing
 * in it bounds at 0 and reaches no further back.
 */
fun earlier(anchor: LocalDate, earliest: Int, bounds: Bounds, zone: ZoneId, pages: Int): Boolean {
    if (pages >= PAGES) return false
    if (bounds.first <= 0) return false
    return page(anchor, earliest, zone).start > bounds.first
}

/**
 * Whether a page after [latest] is worth asking for: something recurs without
 * an end, or the latest page held stops before the last event there is.
 */
fun later(anchor: LocalDate, latest: Int, bounds: Bounds, zone: ZoneId, pages: Int): Boolean {
    if (pages >= PAGES) return false
    if (bounds.endless) return true
    if (bounds.last <= 0) return false
    return page(anchor, latest, zone).finish <= bounds.last
}
