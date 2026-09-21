// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.ui.calendar.Bounds
import org.mochios.calendars.ui.calendar.PAGE
import org.mochios.calendars.ui.calendar.PAGES
import org.mochios.calendars.ui.calendar.earlier
import org.mochios.calendars.ui.calendar.later
import org.mochios.calendars.ui.calendar.page
import java.time.LocalDate
import java.time.ZoneId

/**
 * The list view's paging: the range each page covers from its anchor, and
 * whether the calendars' own bounds leave another page in either direction.
 */
class PagingTest {

    private val LONDON: ZoneId = ZoneId.of("Europe/London")
    private val ANCHOR: LocalDate = LocalDate.of(2026, 9, 22)

    /** An epoch second, worked out with `java.time` rather than by the code under test. */
    private fun moment(date: LocalDate) = date.atStartOfDay(LONDON).toEpochSecond()

    // ---- the range a page covers ----

    @Test
    fun `page zero starts on the anchor day and runs a quarter`() {
        val first = page(ANCHOR, 0, LONDON)
        assertEquals(moment(ANCHOR), first.start)
        assertEquals(moment(ANCHOR.plusDays(PAGE)), first.finish)
    }

    @Test
    fun `pages meet without a gap or an overlap`() {
        val first = page(ANCHOR, 0, LONDON)
        val second = page(ANCHOR, 1, LONDON)
        assertEquals(first.finish, second.start)
        assertEquals(moment(ANCHOR.plusDays(2 * PAGE)), second.finish)
    }

    @Test
    fun `a negative index reaches back from the anchor`() {
        val before = page(ANCHOR, -1, LONDON)
        assertEquals(moment(ANCHOR.minusDays(PAGE)), before.start)
        assertEquals(moment(ANCHOR), before.finish)
    }

    /**
     * The clocks go back inside the page after the anchor, so its span is an
     * hour longer than the arithmetic on seconds alone would give. Measuring
     * in days in the user's zone is what keeps a page whole days.
     */
    @Test
    fun `a page is whole days in the user's zone, whatever the clocks do`() {
        val across = page(ANCHOR, 0, LONDON)
        assertEquals(PAGE * 86_400 + 3_600, across.finish - across.start)
        assertEquals(
            LocalDate.of(2026, 12, 23),
            java.time.Instant.ofEpochSecond(across.finish).atZone(LONDON).toLocalDate(),
        )
    }

    // ---- reaching back ----

    @Test
    fun `nothing reaches back past the first event there is`() {
        // The first event is inside page 0, so there is nothing before it.
        val bounds = Bounds(first = moment(ANCHOR.plusDays(3)), last = moment(ANCHOR.plusDays(9)))
        assertFalse(earlier(ANCHOR, 0, bounds, LONDON, pages = 1))
    }

    @Test
    fun `an event before the anchor leaves a page to reach back to`() {
        val bounds = Bounds(first = moment(ANCHOR.minusDays(10)), last = moment(ANCHOR))
        assertTrue(earlier(ANCHOR, 0, bounds, LONDON, pages = 1))
        // One page back already covers it, so that is as far as it goes.
        assertFalse(earlier(ANCHOR, -1, bounds, LONDON, pages = 2))
    }

    @Test
    fun `an empty calendar reaches back nowhere`() {
        assertFalse(earlier(ANCHOR, 0, Bounds(), LONDON, pages = 1))
        assertFalse(earlier(ANCHOR, 0, Bounds(endless = true), LONDON, pages = 1))
    }

    // ---- reaching forward ----

    @Test
    fun `an event past the page leaves a page to reach forward to`() {
        val bounds = Bounds(first = moment(ANCHOR), last = moment(ANCHOR.plusDays(200)))
        assertTrue(later(ANCHOR, 0, bounds, LONDON, pages = 1))
        assertTrue(later(ANCHOR, 1, bounds, LONDON, pages = 2))
        // Page 2 ends past the last event, so it is the last page.
        assertFalse(later(ANCHOR, 2, bounds, LONDON, pages = 3))
    }

    @Test
    fun `nothing reaches forward when the last event is inside the page`() {
        val bounds = Bounds(first = moment(ANCHOR), last = moment(ANCHOR.plusDays(9)))
        assertFalse(later(ANCHOR, 0, bounds, LONDON, pages = 1))
    }

    @Test
    fun `an empty calendar reaches forward nowhere`() {
        assertFalse(later(ANCHOR, 0, Bounds(), LONDON, pages = 1))
    }

    /**
     * A rule without an end, or the birthdays calendar, has no last event to
     * stop at, so the only thing that stops it is the cap.
     */
    @Test
    fun `an endless calendar always has another page, up to the cap`() {
        val bounds = Bounds(first = moment(ANCHOR), last = 0, endless = true)
        assertTrue(later(ANCHOR, 0, bounds, LONDON, pages = 1))
        assertTrue(later(ANCHOR, 100, bounds, LONDON, pages = PAGES - 1))
        assertFalse(later(ANCHOR, 100, bounds, LONDON, pages = PAGES))
    }

    @Test
    fun `the cap stops both directions`() {
        val wide = Bounds(
            first = moment(ANCHOR.minusDays(10_000)),
            last = moment(ANCHOR.plusDays(10_000)),
        )
        assertTrue(earlier(ANCHOR, 0, wide, LONDON, pages = PAGES - 1))
        assertFalse(earlier(ANCHOR, 0, wide, LONDON, pages = PAGES))
        assertTrue(later(ANCHOR, 0, wide, LONDON, pages = PAGES - 1))
        assertFalse(later(ANCHOR, 0, wide, LONDON, pages = PAGES))
    }

    /** The bound the server answers for a calendar of its own, end to end. */
    @Test
    fun `a calendar that both starts and ends inside one page needs no other`() {
        val bounds = Bounds(first = moment(ANCHOR.plusDays(1)), last = moment(ANCHOR.plusDays(2)))
        assertFalse(earlier(ANCHOR, 0, bounds, LONDON, pages = 1))
        assertFalse(later(ANCHOR, 0, bounds, LONDON, pages = 1))
    }
}
