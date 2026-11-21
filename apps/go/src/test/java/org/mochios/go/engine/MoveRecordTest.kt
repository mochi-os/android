// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The last-move marker comes from the record, so it marks the opponent's
 * reply as well as the player's own stone, as the web's `lastStone` does.
 */
class MoveRecordTest {

    // Black at (2,3), White at (4,4).
    private val game = GoGame(
        "........./........./...B...../........./....W..../........./........./........./......... b 0 0 - 0",
    )

    @Test
    fun `the opponent's reply is the last stone`() {
        assertEquals(4 to 4, lastStone("B[2,3];W[4,4]", game))
    }

    @Test
    fun `a record of one stone`() {
        assertEquals(2 to 3, lastStone("B[2,3]", game))
    }

    @Test
    fun `nothing is marked after a pass or on an empty record`() {
        assertNull(lastStone("B[2,3];W[pass]", game))
        assertNull(lastStone("", game))
    }

    @Test
    fun `a point that does not hold the recorded stone is not marked`() {
        // The colour differs, the point is empty, the point is off the board.
        assertNull(lastStone("B[2,3];B[4,4]", game))
        assertNull(lastStone("B[2,3];W[0,0]", game))
        assertNull(lastStone("B[2,3];W[9,0]", game))
    }

    @Test
    fun `an entry it does not recognise marks nothing`() {
        assertNull(lastStone("B[2,3];W[4,4]x", game))
        assertNull(lastStone("B[2,3];W[99999999999,4]", game))
    }
}
