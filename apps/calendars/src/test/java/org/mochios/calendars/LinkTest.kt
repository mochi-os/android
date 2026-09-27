// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.calendars.navigation.CalendarsApp

class LinkTest {
    @Test
    fun opensTheEventAReminderNames() {
        assertEquals(
            "calendars/events/edit/01a0e3fd?occurrence=1790532300",
            CalendarsApp.linked("calendars/", "view=day&date=2026-09-27&event=01a0e3fd&occurrence=1790532300"),
        )
    }

    @Test
    fun opensAnEventNamedInThePath() {
        assertEquals(
            "calendars/events/edit/01a0e3fd?occurrence=1790532300",
            CalendarsApp.linked("calendars/01a0e3fd/1790532300", ""),
        )
    }

    @Test
    fun opensNothingForALinkThatNamesNoEvent() {
        assertNull(CalendarsApp.linked("calendars/", "view=day&date=2026-09-27"))
        assertNull(CalendarsApp.linked("calendars", ""))
        assertNull(CalendarsApp.linked("calendars/views", ""))
    }
}
