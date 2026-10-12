// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.i18n.Flights
import org.mochios.android.i18n.Maps

/** A location that is a flight number and the tracker page it links to, or a place and its map search. */
class FlightLinksTest {

    @Test
    fun `an airline code and a number is a flight, spaced or not, in any case`() {
        assertEquals("EI59", flightNumber("EI59"))
        assertEquals("BA123", flightNumber("BA 123"))
        assertEquals("U28642", flightNumber("U28642"))
        assertEquals("9W7", flightNumber("9w 7"))
        assertEquals("LH1234A", flightNumber(" lh1234a "))
    }

    @Test
    fun `the airline's name may come before its flight number`() {
        assertEquals("EI59", flightNumber("Aer Lingus EI59"))
        assertEquals("EI59", flightNumber("Aer Lingus EI 59"))
        assertEquals("AS1342", flightNumber("alaska airlines as1342"))
        assertEquals("BA123A", flightNumber("British Airways BA 123A"))
    }

    @Test
    fun `everything else is not`() {
        for (location in listOf("Meeting room", "Studio", "Studio 54", "EI59 gate 12", "Gate B12", "Terminal 2", "Alaska 1342", "A1", "12 34", "EI", "EI12345", "")) {
            assertNull(location, flightNumber(location))
        }
    }

    @Test
    fun `the link follows the tracker preference`() {
        assertEquals("https://www.flightradar24.com/data/flights/ei59", flightLink("EI59", Flights.FLIGHTRADAR24))
        assertEquals("https://www.flightaware.com/live/flight/BA123", flightLink("BA123", Flights.FLIGHTAWARE))
    }

    @Test
    fun `a place is searched on the map service the preference names, as the web searches it`() {
        assertEquals("https://www.openstreetmap.org/search?query=Meeting%20room", mapLink(" Meeting room ", Maps.OPENSTREETMAP))
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=12%20Example%20St%20%26%20Co",
            mapLink("12 Example St & Co", Maps.GOOGLE),
        )
    }
}
