// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.ui.calendar.step
import org.mochios.calendars.ui.calendar.steps
import org.mochios.calendars.ui.router.CalendarsSection
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The toolbar's previous and next, and the swipe between pages: how far each
 * view moves its anchor, and how many steps one date lies from another.
 */
class StepTest {

    private val ANCHOR: LocalDate = LocalDate.of(2026, 9, 16)

    @Test
    fun `each view steps by its own unit`() {
        assertEquals(LocalDate.of(2026, 9, 17), step(CalendarsSection.DAY, ANCHOR, 1))
        assertEquals(LocalDate.of(2026, 9, 9), step(CalendarsSection.WEEK, ANCHOR, -1))
        // A month's step lands on its 1st, as the web's does.
        assertEquals(LocalDate.of(2026, 10, 1), step(CalendarsSection.MONTH, ANCHOR, 1))
        assertEquals(LocalDate.of(2026, 10, 1), step(CalendarsSection.LIST, ANCHOR, 1))
    }

    @Test
    fun `the multiweek span slides one week at a time, not its length`() {
        assertEquals(LocalDate.of(2026, 9, 23), step(CalendarsSection.MULTIWEEK, ANCHOR, 1))
        assertEquals(LocalDate.of(2026, 9, 9), step(CalendarsSection.MULTIWEEK, ANCHOR, -1))
    }

    private val monday: (LocalDate) -> LocalDate = { date ->
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    @Test
    fun `steps undoes step in every paged view`() {
        val views = listOf(
            CalendarsSection.DAY,
            CalendarsSection.WEEK,
            CalendarsSection.MULTIWEEK,
            CalendarsSection.MONTH,
        )
        for (view in views) {
            for (offset in -14..14) {
                assertEquals(view, offset, steps(view, ANCHOR, step(view, ANCHOR, offset), monday))
            }
        }
    }

    @Test
    fun `two days of the same week or month are no steps apart`() {
        val friday = LocalDate.of(2026, 9, 18)
        assertEquals(0, steps(CalendarsSection.WEEK, ANCHOR, friday, monday))
        assertEquals(0, steps(CalendarsSection.MONTH, ANCHOR, LocalDate.of(2026, 9, 30), monday))
        assertEquals(1, steps(CalendarsSection.WEEK, ANCHOR, LocalDate.of(2026, 9, 21), monday))
    }

    @Test
    fun `a month step from the 31st still counts as one month`() {
        val end = LocalDate.of(2026, 1, 31)
        val next = step(CalendarsSection.MONTH, end, 1)
        assertEquals(1, steps(CalendarsSection.MONTH, end, next, monday))
    }
}
