// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Positional superko: a move may not recreate the position before it. The FEN
 * carries the single ko point, but a snapshot from the server arrives with that
 * field cleared, so `previousGrid` is what actually refuses the retake - and it
 * was carried and never read.
 */
class SuperkoTest {

    // The ko shape before Black takes: White at (1,1) has one liberty, (1,2).
    private val before = ".BW../BW.W./.BW../...../....."

    // After Black takes at (1,2): White's stone is gone, Black's is on the
    // board. White retaking at (1,1) captures it back and returns to `before`.
    private val after = ".BW../B.BW./.BW../...../....."

    @Test
    fun `a retake that recreates the previous position is refused`() {
        val game = GoGame("$after w 1 0 - 0", before)
        assertFalse(
            "White retaking at (1,1) repeats the position two plies back",
            game.isLegal(1, 1),
        )
    }

    @Test
    fun `the same move is legal when no previous position is known`() {
        // The positive control: without `previousGrid` the point is an
        // ordinary capture, so the refusal above is superko and not suicide or
        // occupancy.
        val game = GoGame("$after w 1 0 - 0")
        assertTrue(game.isLegal(1, 1))
    }

    @Test
    fun `an unrelated point is unaffected by the previous position`() {
        val game = GoGame("$after w 1 0 - 0", before)
        assertTrue("a move elsewhere stays legal", game.isLegal(4, 4))
    }
}
