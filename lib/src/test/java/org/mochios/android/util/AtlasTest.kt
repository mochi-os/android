// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The time zone map the pickers draw: its paths read as rings, a point found
 * inside a zone's land, then its waters, then the sea bands, and the map the
 * app carries putting known places in their zones.
 */
class AtlasTest {

    @Test
    fun `a path reads as its rings, each closed`() {
        val rings = Atlas.rings("M0.0 0.0L10.0 0.0L10.0 10.0L0.0 10.0ZM2.5 2.5L4.0 2.5L4.0 4.0Z")
        assertEquals(2, rings.size)
        assertEquals(listOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f), rings[0].toList())
        assertEquals(listOf(2.5f, 2.5f, 4f, 2.5f, 4f, 4f), rings[1].toList())
    }

    @Test
    fun `a ring inside another is a hole`() {
        val square = Area("A", Atlas.rings("M0 0L10 0L10 10L0 10ZM2 2L4 2L4 4L2 4Z"))
        assertTrue(square.contains(5f, 5f))
        assertFalse(square.contains(3f, 3f))
        assertFalse(square.contains(20f, 5f))
    }

    private val atlas = Atlas.parse(
        """
        {"width": 100, "height": 50, "attribution": "© OpenStreetMap contributors",
         "oceans": {"Etc/GMT": "M0 0L100 0L100 50L0 50Z"},
         "zones": {"Waters/Wide": "M10 10L60 10L60 40L10 40Z"},
         "land": {"Land/First": "M20 20L40 20L40 30L20 30Z", "Land/Second": "M35 20L45 20L45 30L35 30Z"}}
        """.trimIndent(),
    )

    @Test
    fun `a point finds the land first, then a zone's waters, then the sea`() {
        assertEquals("Land/First", atlas.at(25f, 25f))
        assertEquals("Waters/Wide", atlas.at(15f, 15f))
        assertEquals("Etc/GMT", atlas.at(80f, 5f))
        assertNull(atlas.at(150f, 5f))
    }

    @Test
    fun `where two zones cover the same ground, the one drawn later is found`() {
        assertEquals("Land/Second", atlas.at(37f, 25f))
    }

    @Test
    fun `the map reads its frame and its notice`() {
        assertEquals(100f, atlas.width)
        assertEquals(50f, atlas.height)
        assertEquals("© OpenStreetMap contributors", atlas.attribution)
    }

    // ---- the map the app carries ----

    private val carried by lazy { Atlas.parse(File("src/main/res/raw/timezones.json").readText()) }

    /** A place's point on the map: equirectangular, 960 by 400, cropped at 60 degrees south. */
    private fun at(latitude: Double, longitude: Double): String? =
        carried.at(((longitude + 180) / 360 * 960).toFloat(), ((90 - latitude) / 150 * 400).toFloat())

    @Test
    fun `known places fall in their zones on the map the app carries`() {
        assertEquals("Europe/London", at(51.5, -0.12))
        assertEquals("Asia/Kolkata", at(28.6, 77.2))
        assertEquals("America/New_York", at(40.7, -74.0))
        // The open Atlantic, three hours behind UTC.
        assertEquals("Etc/GMT+3", at(30.0, -40.0))
    }

    @Test
    fun `every place the map draws is one the pickers list by that name`() {
        // The sea's zones, such as Etc/UTC, are named by their offsets instead.
        val drawn = (carried.land + carried.zones).map { it.zone }.filterNot { it.startsWith("Etc/") }.toSet()
        assertTrue(drawn.isNotEmpty())
        assertEquals(emptySet<String>(), drawn - ZoneNames.drawn)
    }

    // ---- zooming ----

    @Test
    fun `the whole map fits the box, and a pixel reads as its point on the map`() {
        val view = Viewport.whole(480f, 960f)
        assertEquals(0.5f, view.scale)
        assertEquals(480f to 200f, view.map(240f, 100f))
    }

    @Test
    fun `zooming keeps the point under the fingers where it was`() {
        val view = Viewport.whole(960f, 960f).moved(2f, 480f, 200f, 0f, 0f, 960f, 400f, 960f, 400f)
        assertEquals(2f, view.scale)
        assertEquals(480f to 200f, view.map(480f, 200f))
    }

    @Test
    fun `the map never shrinks below the box, grows past eight times, or leaves an edge showing`() {
        val whole = Viewport.whole(960f, 960f)
        val shrunk = whole.moved(0.5f, 480f, 200f, 0f, 0f, 960f, 400f, 960f, 400f)
        assertEquals(whole, shrunk)
        val grown = whole.moved(20f, 0f, 0f, 0f, 0f, 960f, 400f, 960f, 400f)
        assertEquals(8f, grown.scale)
        val dragged = grown.moved(1f, 0f, 0f, 500f, 500f, 960f, 400f, 960f, 400f)
        assertEquals(0f, dragged.x)
        assertEquals(0f, dragged.y)
        val far = grown.moved(1f, 0f, 0f, -100_000f, -100_000f, 960f, 400f, 960f, 400f)
        assertEquals(960f - 960f * 8, far.x)
        assertEquals(400f - 400f * 8, far.y)
    }
}
