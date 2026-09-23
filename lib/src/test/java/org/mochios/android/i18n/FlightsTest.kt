// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/** The `flights` preference as the server sends it: a known service, or the default. */
class FlightsTest {
    @Test
    fun aKnownServiceIsRead() {
        assertEquals(Flights.FLIGHTAWARE, Flights.fromString("flightaware"))
        assertEquals(Flights.FLIGHTRADAR24, Flights.fromString("flightradar24"))
    }

    @Test
    fun anythingElseIsTheDefault() {
        assertEquals(Flights.FLIGHTRADAR24, Flights.fromString(null))
        assertEquals(Flights.FLIGHTRADAR24, Flights.fromString(""))
        assertEquals(Flights.FLIGHTRADAR24, Flights.fromString("auto"))
        assertEquals(Flights.FLIGHTRADAR24, Flights.fromString("bing"))
    }
}
