// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.ui.calendar.step
import org.mochios.calendars.ui.router.CalendarsSection
import java.time.LocalDate

/** The toolbar's previous and next: how far each view moves its anchor. */
class StepTest {

    private val ANCHOR: LocalDate = LocalDate.of(2026, 9, 16)

    @Test
    fun `each view steps by its own unit`() {
        assertEquals(LocalDate.of(2026, 9, 17), step(CalendarsSection.DAY, ANCHOR, 1))
        assertEquals(LocalDate.of(2026, 9, 9), step(CalendarsSection.WEEK, ANCHOR, -1))
        assertEquals(LocalDate.of(2026, 10, 16), step(CalendarsSection.MONTH, ANCHOR, 1))
        assertEquals(LocalDate.of(2026, 10, 16), step(CalendarsSection.LIST, ANCHOR, 1))
    }

    @Test
    fun `the multiweek span slides one week at a time, not its length`() {
        assertEquals(LocalDate.of(2026, 9, 23), step(CalendarsSection.MULTIWEEK, ANCHOR, 1))
        assertEquals(LocalDate.of(2026, 9, 9), step(CalendarsSection.MULTIWEEK, ANCHOR, -1))
    }
}
