// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.ui.components.CalendarAction

/** The flag a calendar's Settings screen leaves for the calendar screen to act on. */
class CalendarActionFlagTest {
    @Test
    fun `an action and a calendar are read back`() {
        assertEquals(CalendarAction.IMPORT to "c1", CalendarsApp.action("IMPORT:c1"))
        assertEquals(CalendarAction.DELETE to "a:b", CalendarsApp.action("DELETE:a:b"))
    }

    @Test
    fun `no flag, an unknown action or no calendar is nothing`() {
        assertNull(CalendarsApp.action(""))
        assertNull(CalendarsApp.action("SHRED:c1"))
        assertNull(CalendarsApp.action("EXPORT:"))
    }
}
