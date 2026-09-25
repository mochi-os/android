// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.calendars.model.Zone
import org.mochios.calendars.navigation.CalendarsApp
import org.mochios.calendars.storage.Memory
import org.mochios.calendars.ui.editor.remembered
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * What a blank new form starts with from the device's memory of the last
 * new event: its zones always, its all-day setting only when nothing else
 * has chosen, and the route that carries a tap's choice.
 */
class EventMemoryTest {

    private val LONDON = "Europe/London"
    private val SYDNEY = "Australia/Sydney"
    private val TOKYO = "Asia/Tokyo"

    /** A tap on the 10:00 cell of the 24th, in London. */
    private val TAP = ZonedDateTime.of(2026, 9, 24, 10, 0, 0, 0, ZoneId.of(LONDON)).toEpochSecond()

    @Test
    fun blankFormWithNoMemoryIsTimedInTheUsersZone() {
        assertEquals(Memory(false, Zone(LONDON, LONDON)), remembered(null, LONDON, 0, null))
    }

    @Test
    fun newEventActionTakesTheMemoryAllDay() {
        assertTrue(remembered(Memory(true, Zone(SYDNEY, TOKYO)), LONDON, 0, null).allday)
    }

    @Test
    fun newEventActionTakesTheMemoryZones() {
        assertEquals(Zone(SYDNEY, TOKYO), remembered(Memory(true, Zone(SYDNEY, TOKYO)), LONDON, 0, null).zone)
    }

    @Test
    fun cellTapStaysTimedInTheRememberedZones() {
        val form = remembered(Memory(true, Zone(SYDNEY, SYDNEY)), LONDON, TAP, false)
        assertFalse(form.allday)
        assertEquals(Zone(SYDNEY, SYDNEY), form.zone)
    }

    @Test
    fun routeSayingAllDayWinsOverATimedMemory() {
        assertTrue(remembered(Memory(false, Zone(LONDON, LONDON)), LONDON, TAP, true).allday)
    }

    @Test
    fun blankRememberedEndReadsInTheUsersZone() {
        assertEquals(Zone(LONDON, LONDON), remembered(Memory(false, Zone(LONDON, "")), LONDON, 0, null).zone)
    }

    @Test
    fun startWithoutASayIsADayCellAndTakesTheMemoryAllDay() {
        assertTrue(remembered(Memory(true, Zone(LONDON, LONDON)), LONDON, TAP, null).allday)
        assertFalse(remembered(Memory(false, Zone(LONDON, LONDON)), LONDON, TAP, null).allday)
    }

    @Test
    fun cellTapRouteSaysTimed() {
        assertEquals("calendars/events/new?start=$TAP&allday=0", CalendarsApp.newEvent(TAP, false))
    }

    @Test
    fun bandTapRouteSaysAllDay() {
        assertEquals("calendars/events/new?start=$TAP&allday=1", CalendarsApp.newEvent(TAP, true))
    }

    @Test
    fun newEventActionRouteSaysNothing() {
        assertEquals("calendars/events/new?start=0", CalendarsApp.newEvent(0, null))
    }
}
