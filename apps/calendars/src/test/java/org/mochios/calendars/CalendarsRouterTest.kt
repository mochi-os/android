// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.model.Instance
import org.mochios.calendars.model.Zone
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.ui.editor.Scope
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
            CalendarsApp.copyEvent("event-1", 1_790_067_600, Scope.ONE),
            CalendarsApp.copyOccurrence(Instance(summary = "Tea")),
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

    @Test
    fun `a copy of a stored event names it, the occurrence and how far the copy reaches`() {
        assertEquals(
            "calendars/events/new?source=event&copy=event-1&occurrence=1790067600&scope=one",
            CalendarsApp.copyEvent("event-1", 1_790_067_600, Scope.ONE),
        )
        assertEquals(
            "calendars/events/new?source=event&copy=event-1&occurrence=0&scope=all",
            CalendarsApp.copyEvent("event-1", 0, Scope.ALL),
        )
    }

    /**
     * An occurrence with no stored event travels in the route itself, so
     * every character its text can hold has to survive the query string.
     */
    @Test
    fun `a copy of an occurrence with no stored event carries the occurrence itself, encoded`() {
        val instance = Instance(
            event = "birthday-1",
            summary = "Tea & cake",
            location = "Café",
            description = "Line one\nline two",
            start = 1,
            finish = 2,
            allday = true,
            date = "2026-09-16",
            zone = Zone("Europe/London", ""),
        )
        assertEquals(
            "calendars/events/new?source=occurrence&start=1&finish=2&allday=1&date=2026-09-16" +
                "&summary=Tea%20%26%20cake&location=Caf%C3%A9&description=Line%20one%0Aline%20two" +
                "&zones=Europe%2FLondon%2C",
            CalendarsApp.copyOccurrence(instance),
        )
        val timed = Instance(summary = "Call", start = 3, finish = 4)
        assertEquals(
            "calendars/events/new?source=occurrence&start=3&finish=4&allday=0&date=" +
                "&summary=Call&location=&description=&zones=%2C",
            CalendarsApp.copyOccurrence(timed),
        )
    }

    /** A parameter the pattern does not name would be dropped on the way to the editor. */
    @Test
    fun `every parameter a copy route carries is one the new-event pattern names`() {
        val routes = listOf(
            CalendarsApp.copyEvent("event-1", 1, Scope.ALL),
            CalendarsApp.copyOccurrence(Instance(summary = "Tea", date = "2026-09-16", zone = Zone("a", "b"))),
        )
        for (route in routes) {
            assertEquals("calendars/events/new", route.substringBefore('?'))
            for (parameter in route.substringAfter('?').split('&').map { it.substringBefore('=') }) {
                assertTrue(
                    parameter,
                    CalendarsApp.EVENT_NEW.contains("?$parameter={$parameter}") ||
                        CalendarsApp.EVENT_NEW.contains("&$parameter={$parameter}"),
                )
            }
        }
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
