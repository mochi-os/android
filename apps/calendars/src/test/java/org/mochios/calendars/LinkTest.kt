// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.calendars.model.Instance
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.navigation.Reminder
import org.mochios.calendars.ui.calendar.open
import java.time.LocalDate

/** A notification's link, and how the calendar opens what it names. */
class LinkTest {
    @Test
    fun `a reminder names its event, its occurrence and the day it falls on`() {
        assertEquals(
            Reminder("01a0e3fd", 1_790_532_300, LocalDate.of(2026, 9, 27)),
            CalendarsApp.linked("calendars/", "view=day&date=2026-09-27&event=01a0e3fd&occurrence=1790532300"),
        )
    }

    @Test
    fun `an older link names the event in its path and no day`() {
        assertEquals(Reminder("01a0e3fd", 1_790_532_300, null), CalendarsApp.linked("calendars/01a0e3fd/1790532300", ""))
    }

    @Test
    fun `a link that names no event opens nothing`() {
        assertNull(CalendarsApp.linked("calendars/", "view=day&date=2026-09-27"))
        assertNull(CalendarsApp.linked("calendars", ""))
        assertNull(CalendarsApp.linked("calendars/views", ""))
    }

    @Test
    fun `a reminder survives the back-stack entry it is handed through`() {
        val reminder = Reminder("01a0e3fd", 1_790_532_300, LocalDate.of(2026, 9, 27))
        assertEquals(reminder, Reminder.of(reminder.flag()))
        assertEquals(Reminder("e", 5, null), Reminder.of(Reminder("e", 5, null).flag()))
        assertNull(Reminder.of(""))
    }

    @Test
    fun `an occurrence opens in the editor when it can be changed, else in its sheet`() {
        val edited = mutableListOf<Pair<String, Long>>()
        val shown = mutableListOf<Instance>()
        open(Instance(event = "e1", start = 100, recurring = true), { e, o -> edited += e to o }) { shown += it }
        open(Instance(event = "e2", start = 100), { e, o -> edited += e to o }) { shown += it }
        open(Instance(event = "e3", readonly = true), { e, o -> edited += e to o }) { shown += it }
        open(Instance(event = "birthday-c1-2026", start = 100), { e, o -> edited += e to o }) { shown += it }
        assertEquals(listOf("e1" to 100L, "e2" to 0L), edited)
        assertEquals(listOf("e3", "birthday-c1-2026"), shown.map { it.event })
    }
}
