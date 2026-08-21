// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A tie (equal totals, reachable with komi 0) has no winning colour; passTurn
 * resolves the winner colour to a player identity.
 */
class ScoreTest {

    /**
     * An empty board of the given size. The board string is one character per
     * intersection, rows separated by `/` — not run-length encoded.
     */
    private fun empty(size: Int) =
        GoGame((0 until size).joinToString("/") { GoGame.EMPTY.toString().repeat(size) })

    @Test
    fun `an empty board with zero komi is a tie, not a White win`() {
        val score = empty(9).score(komi = 0.0)
        assertEquals(score.black, score.white, 0.0)
        assertNull("a tie has no winning colour", score.winner)
    }

    @Test
    fun `the default komi cannot tie`() {
        val score = empty(9).score()
        assertNotNull(score.winner)
        assertEquals(Stone.WHITE, score.winner)
    }

    @Test
    fun `White wins when komi carries it`() {
        val score = empty(19).score(komi = 6.5)
        assertEquals(Stone.WHITE, score.winner)
    }

    @Test
    fun `the tie case really is an equal score`() {
        val score = empty(13).score(komi = 0.0)
        assertEquals(0.0, score.black, 0.0)
        assertEquals(0.0, score.white, 0.0)
        assertNull(score.winner)
    }
}
