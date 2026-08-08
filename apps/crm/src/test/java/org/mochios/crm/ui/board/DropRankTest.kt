// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The server places a dragged card by 1-based position within the target
 * COLUMN — rank_move_key lists every object sharing the column value and has
 * no row dimension. The board renders swimlanes, so the position the client
 * computes has to be column-wide even when the card was dropped inside a lane.
 */
class DropRankTest {

    private val column = listOf("a", "b", "c", "d")

    @Test
    fun `dropping above a card takes its position`() {
        assertEquals(1, dropRank(column, targetId = "a", sourceId = "x", below = false))
        assertEquals(3, dropRank(column, targetId = "c", sourceId = "x", below = false))
    }

    @Test
    fun `dropping below a card takes the position after it`() {
        assertEquals(2, dropRank(column, targetId = "a", sourceId = "x", below = true))
        assertEquals(5, dropRank(column, targetId = "d", sourceId = "x", below = true))
    }

    /**
     * A card already in this column is excluded, because the server computes
     * the slot from the list with the moved object removed.
     */
    @Test
    fun `a source already in the column does not count towards the position`() {
        // Without "a", the list is b, c, d — so c is second.
        assertEquals(2, dropRank(column, targetId = "c", sourceId = "a", below = false))
        assertEquals(3, dropRank(column, targetId = "c", sourceId = "a", below = true))
    }

    @Test
    fun `moving a card down one place lands after its neighbour`() {
        assertEquals(2, dropRank(column, targetId = "b", sourceId = "a", below = true))
    }

    /**
     * The defect this replaces: the client passed one swimlane's objects, so a
     * card second in its lane asked for position 2 of the whole column. With
     * the column-wide list the same drop resolves to its real position.
     */
    @Test
    fun `a lane-local list would report the wrong position`() {
        val laneOnly = listOf("b", "d")
        assertEquals(2, dropRank(laneOnly, targetId = "d", sourceId = "x", below = false))
        assertEquals(4, dropRank(column, targetId = "d", sourceId = "x", below = false))
    }

    @Test
    fun `an unknown target appends to the end`() {
        assertEquals(5, dropRank(column, targetId = "missing", sourceId = "x", below = false))
        assertEquals(4, dropRank(column, targetId = "missing", sourceId = "a", below = false))
    }

    @Test
    fun `an empty column takes the first slot`() {
        assertEquals(1, dropRank(emptyList(), targetId = "a", sourceId = "x", below = false))
    }
}
