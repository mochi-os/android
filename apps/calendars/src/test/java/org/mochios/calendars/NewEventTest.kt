// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.model.Hours
import org.mochios.calendars.ui.editor.defaultStart
import java.time.LocalDate
import java.time.LocalTime

/** Where a new event with no time of its own starts. */
class NewEventTest {

    private val TODAY = LocalDate.of(2026, 9, 28)
    private val HOURS = Hours(start = 8, finish = 17)

    @Test
    fun `today it starts at the next whole hour`() {
        assertEquals(TODAY.atTime(15, 0), defaultStart(TODAY, TODAY, LocalTime.of(14, 37), HOURS))
        assertEquals(TODAY.atTime(23, 0), defaultStart(TODAY, TODAY, LocalTime.of(22, 59), HOURS))
    }

    @Test
    fun `on another day it starts at the start of the working hours`() {
        val friday = LocalDate.of(2026, 10, 2)
        assertEquals(friday.atTime(8, 0), defaultStart(friday, TODAY, LocalTime.of(14, 37), HOURS))
        assertEquals(friday.atTime(7, 0), defaultStart(friday, TODAY, LocalTime.MIDNIGHT, Hours(start = 7, finish = 15)))
    }

    @Test
    fun `once today has no whole hour left it starts at tomorrow's working hours`() {
        assertEquals(TODAY.plusDays(1).atTime(8, 0), defaultStart(TODAY, TODAY, LocalTime.of(23, 10), HOURS))
    }
}
