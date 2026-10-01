// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.model.defaultCalendar

/** The calendar a new event opens on: the user's choice, else the built-in default. */
class DefaultCalendarTest {

    private val work = Calendar(id = "work", name = "Work")
    private val home = Calendar(id = "home", name = "Home", default = true)
    private val holidays = Calendar(id = "holidays", name = "Holidays", kind = Calendar.KIND_SUBSCRIPTION, readonly = true)
    private val calendars = listOf(work, home, holidays)

    @Test
    fun `the chosen calendar when it can be written to`() {
        assertEquals("work", defaultCalendar(calendars, "work"))
    }

    @Test
    fun `the built-in default when nothing is chosen, or the choice is gone or read-only`() {
        assertEquals("home", defaultCalendar(calendars, ""))
        assertEquals("home", defaultCalendar(calendars, "deleted"))
        assertEquals("home", defaultCalendar(calendars, "holidays"))
    }

    @Test
    fun `the first writable calendar before the list has a default, and none with nothing writable`() {
        assertEquals("work", defaultCalendar(listOf(holidays, work), ""))
        assertEquals("", defaultCalendar(listOf(holidays), "holidays"))
    }
}
