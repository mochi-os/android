// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The city a zone reads as beside a time, the same as the web shows it. */
class ZonesTest {

    @Test
    fun `the city is the part after the last slash, with spaces for underscores`() {
        assertEquals("New York", zoneCity("America/New_York"))
        assertEquals("London", zoneCity("Europe/London"))
    }

    @Test
    fun `a nested region keeps only its last part`() {
        assertEquals("Buenos Aires", zoneCity("America/Argentina/Buenos_Aires"))
    }

    @Test
    fun `a sea zone reads as its offset from UTC, the sign the right way round`() {
        assertEquals("UTC+8", zoneCity("Etc/GMT-8"))
        assertEquals("UTC-10", zoneCity("Etc/GMT+10"))
        assertEquals("UTC", zoneCity("Etc/GMT"))
        assertEquals("UTC+1", zoneCity("Etc/GMT-1"))
        assertEquals("UTC", zoneCity("Etc/GMT+0"))
    }

    @Test
    fun `a zone with no slash is its own label`() {
        assertEquals("UTC", zoneCity("UTC"))
    }

    // ---- one zone under one name ----

    /**
     * A platform that resolves names as ICU does, to the old CLDR name, and
     * lists its zones of places under those names.
     */
    private val icu = object : Zones.Registry {
        private val names = mapOf(
            "Asia/Kolkata" to "Asia/Calcutta",
            "Asia/Calcutta" to "Asia/Calcutta",
            "Europe/Kyiv" to "Europe/Kiev",
            "Europe/Kiev" to "Europe/Kiev",
            "US/Eastern" to "America/New_York",
            "America/New_York" to "America/New_York",
            "Europe/London" to "Europe/London",
            "Europe/Busingen" to "Europe/Busingen",
            "Europe/Zurich" to "Europe/Zurich",
        )

        override fun canonical(zone: String): String? = names[zone]

        override fun places(): Collection<String> =
            listOf("Asia/Calcutta", "Europe/Kiev", "America/New_York", "Europe/London", "Europe/Busingen")
    }

    @Test
    fun `an old name reads as the name the map draws the zone by`() {
        assertEquals("Asia/Kolkata", Zones.current("Asia/Calcutta", icu))
        assertEquals("Europe/Kyiv", Zones.current("Europe/Kiev", icu))
        assertEquals("Asia/Kolkata", Zones.current("Asia/Kolkata", icu))
    }

    @Test
    fun `another alias reads as the drawn zone the platform resolves it to`() {
        assertEquals("America/New_York", Zones.current("US/Eastern", icu))
    }

    @Test
    fun `a zone too small to draw reads as the one it is drawn within, and one the map does not draw keeps its name`() {
        assertEquals("Europe/Zurich", Zones.current("Europe/Busingen", icu))
        assertEquals("UTC", Zones.current("UTC", icu))
        assertEquals("Etc/GMT-8", Zones.current("Etc/GMT-8", icu))
    }

    @Test
    fun `a zone the platform does not know still reads as the one the map draws it within`() {
        val older = object : Zones.Registry {
            override fun canonical(zone: String): String? = null
            override fun places(): Collection<String> = emptyList()
        }
        assertEquals("Europe/Zurich", Zones.current("Europe/Busingen", older))
    }

    @Test
    fun `two names of one zone are the same zone, and two zones are not`() {
        assertTrue(Zones.same("Asia/Calcutta", "Asia/Kolkata", icu))
        assertTrue(Zones.same("US/Eastern", "America/New_York", icu))
        assertFalse(Zones.same("Europe/London", "America/New_York", icu))
    }

    @Test
    fun `the list names each zone once by its current name, in order, keeping its other names for a search`() {
        val listed = Zones.listed(icu)
        assertEquals(listOf("America/New_York", "Asia/Kolkata", "Europe/Kyiv", "Europe/London", "Europe/Zurich"), listed.keys.toList())
        assertEquals(listOf("Asia/Calcutta"), listed["Asia/Kolkata"])
        assertEquals(listOf("Europe/Busingen"), listed["Europe/Zurich"])
        assertEquals(emptyList<String>(), listed["Europe/London"])
    }

    @Test
    fun `the sea has one zone per whole hour, named the zone database's way`() {
        val sea = Zones.sea()
        assertEquals(25, sea.size)
        assertEquals("Etc/GMT+12", sea.first())
        assertEquals("Etc/GMT", sea[12])
        assertEquals("Etc/GMT-12", sea.last())
        assertEquals("UTC-12", zoneCity(sea.first()))
    }

    @Test
    fun `an offset reads in hours, with minutes only where there are some`() {
        val winter = Instant.parse("2026-01-15T12:00:00Z")
        assertEquals("UTC+5:30", Zones.offset("Asia/Kolkata", winter))
        assertEquals("UTC+5:45", Zones.offset("Asia/Kathmandu", winter))
        assertEquals("UTC-5", Zones.offset("America/New_York", winter))
        assertEquals("UTC", Zones.offset("Europe/London", winter))
        assertEquals("UTC+1", Zones.offset("Europe/London", Instant.parse("2026-07-15T12:00:00Z")))
        assertEquals("", Zones.offset("Nowhere/Special", winter))
    }

    @Test
    fun `a picker names a sea zone by its offset and any other with spaces`() {
        assertEquals("America/New York", Zones.label("America/New_York"))
        assertEquals("UTC+8", Zones.label("Etc/GMT-8"))
    }
}
