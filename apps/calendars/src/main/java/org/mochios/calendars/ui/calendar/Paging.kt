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
 * How many pages forward the list view follows a calendar whose events never
 * end, a rule without an end date or the birthdays calendar, before it stops
 * adding pages on its own. A calendar that ends pages on to its last event.
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
 * anchor day itself. A negative index reaches back. The server lists nothing
 * before 1970, so a page stops there.
 */
fun page(anchor: LocalDate, index: Int, zone: ZoneId): Page {
    val start = anchor.plusDays(index * PAGE)
    return Page(
        maxOf(0L, start.atStartOfDay(zone).toEpochSecond()),
        maxOf(0L, start.plusDays(PAGE).atStartOfDay(zone).toEpochSecond()),
    )
}

/**
 * Whether a page before [earliest] is worth asking for: the earliest page
 * held still begins after the first event there is, and after 1970, where
 * listing stops. A first event before 1970, such as a contact's birthday,
 * bounds below zero; zero alone means there is nothing.
 */
fun earlier(anchor: LocalDate, earliest: Int, bounds: Bounds, zone: ZoneId): Boolean {
    if (bounds.first == 0L) return false
    val start = page(anchor, earliest, zone).start
    return start > 0 && start > bounds.first
}

/**
 * Whether a page after [latest] is worth asking for: the latest page held
 * stops before the last event there is, or something recurs without an end
 * and fewer than [PAGES] pages from the anchor are held.
 */
fun later(anchor: LocalDate, latest: Int, bounds: Bounds, zone: ZoneId): Boolean {
    if (bounds.endless) return latest + 1 < PAGES
    if (bounds.last <= 0) return false
    return page(anchor, latest, zone).finish <= bounds.last
}
