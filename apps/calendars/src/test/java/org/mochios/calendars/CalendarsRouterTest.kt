// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.ui.router.CalendarsSection
import org.mochios.calendars.ui.router.calendarsView

/**
 * The token the router stores and reads back, and the routes the module
 * navigates by.
 */
class CalendarsRouterTest {

    @Test
    fun `every view the app offers resolves to itself`() {
        for (view in CalendarsSection.ALL) {
            assertEquals(view, calendarsView(view))
        }
    }

    @Test
    fun `nothing stored opens on the default view`() {
        assertEquals(CalendarsSection.MONTH, CalendarsSection.DEFAULT)
        assertEquals(CalendarsSection.DEFAULT, calendarsView(""))
    }

    /**
     * A token a later release wrote, or one that never was a view, must not
     * open the app on a screen that does not exist.
     */
    @Test
    fun `an unknown token falls back rather than opening on nothing`() {
        assertEquals(CalendarsSection.DEFAULT, calendarsView("agenda"))
        assertEquals(CalendarsSection.DEFAULT, calendarsView("Month"))
        assertEquals(CalendarsSection.DEFAULT, calendarsView("__all__"))
    }

    /** The tokens are the same names the server's `view` preference uses. */
    @Test
    fun `the view tokens are the server's own`() {
        assertEquals(
            listOf("day", "week", "multiweek", "month", "list"),
            CalendarsSection.ALL,
        )
    }

    @Test
    fun `every route is under the module's own prefix, so nothing collides in the host`() {
        val routes = listOf(
            CalendarsApp.HOME,
            CalendarsApp.CREATE,
            CalendarsApp.SUBSCRIBE,
            CalendarsApp.DEVICES,
            CalendarsApp.EVENT_NEW,
            CalendarsApp.EVENT_EDIT,
            CalendarsApp.newEvent(0),
            CalendarsApp.event("event-1", 1_790_067_600),
        )
        for (route in routes) {
            assertTrue(route, route.startsWith("calendars/"))
        }
    }

    @Test
    fun `an event route carries its id and the occurrence it was opened at`() {
        assertEquals("calendars/events/edit/event-1?occurrence=0", CalendarsApp.event("event-1"))
        assertEquals(
            "calendars/events/edit/event-1?occurrence=1790067600",
            CalendarsApp.event("event-1", 1_790_067_600),
        )
        assertEquals("calendars/events/new?start=0", CalendarsApp.newEvent())
    }

    /**
     * The two editor routes must not both match one URL: an event whose id
     * happened to be "new" would otherwise be swallowed by the new-event
     * route, and the nav graph would be left choosing between them.
     */
    @Test
    fun `the new-event route cannot be confused with the edit route`() {
        assertTrue(CalendarsApp.newEvent().startsWith("calendars/events/new"))
        assertTrue(CalendarsApp.event("new").startsWith("calendars/events/edit/"))
        assertEquals(
            "the two patterns differ before their first argument",
            "calendars/events/new",
            CalendarsApp.EVENT_NEW.substringBefore('?'),
        )
        assertEquals(
            "calendars/events/edit/{event}",
            CalendarsApp.EVENT_EDIT.substringBefore('?'),
        )
    }
}
