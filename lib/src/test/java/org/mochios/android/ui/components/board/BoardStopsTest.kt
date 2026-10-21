// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.board

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a paged board may come to rest. Pages are 300 wide, strips 40, with 8
 * between, laid out from offset 0.
 */
class BoardStopsTest {

    private fun layout(vararg collapsed: Boolean): List<BoardItem> {
        var offset = 0
        return collapsed.mapIndexed { index, strip ->
            val size = if (strip) 40 else 300
            BoardItem(index, offset, size).also { offset += size + 8 }
        }
    }

    @Test
    fun `each column is a stop of its own, centred on its middle`() {
        val stops = boardStops(layout(false, false, false)) { false }
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), stops.map { it.first to it.last })
        assertEquals(listOf(150f, 458f, 766f), stops.map { it.centre })
    }

    @Test
    fun `a run of strips is one stop, centred on the run`() {
        val collapsed = setOf(1, 2)
        val stops = boardStops(layout(false, true, true, false)) { it in collapsed }
        assertEquals(listOf(0 to 0, 1 to 2, 3 to 3), stops.map { it.first to it.last })
        // The strips sit at 308 and 356, so the run spans 308 to 396.
        assertEquals(352f, stops[1].centre)
    }

    @Test
    fun `strips parted by a column are separate stops`() {
        val collapsed = setOf(0, 2)
        val stops = boardStops(layout(true, false, true)) { it in collapsed }
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), stops.map { it.first to it.last })
    }

    @Test
    fun `stops come in board order whatever order the layout reports`() {
        val stops = boardStops(layout(false, true, false).reversed()) { it == 1 }
        assertEquals(listOf(0, 1, 2), stops.map { it.first })
    }

    @Test
    fun `a slow release rests on the nearest stop`() {
        assertEquals(-40f, snapDistance(listOf(-40f, 200f), velocity = 100f, minimum = 400f))
        assertEquals(30f, snapDistance(listOf(-90f, 30f), velocity = -100f, minimum = 400f))
    }

    @Test
    fun `a fling goes on to the next stop in its direction`() {
        assertEquals(200f, snapDistance(listOf(-40f, 200f), velocity = 900f, minimum = 400f))
        assertEquals(-90f, snapDistance(listOf(-90f, 30f), velocity = -900f, minimum = 400f))
    }

    @Test
    fun `with stops on one side only, that side wins`() {
        assertEquals(120f, snapDistance(listOf(120f, 400f), velocity = -900f, minimum = 400f))
        assertEquals(-60f, snapDistance(listOf(-300f, -60f), velocity = 900f, minimum = 400f))
        assertEquals(0f, snapDistance(emptyList(), velocity = 900f, minimum = 400f))
    }
}
