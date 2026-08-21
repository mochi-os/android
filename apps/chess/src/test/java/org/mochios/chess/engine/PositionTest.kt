// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.engine

import com.github.bhlangonijr.chesslib.Board
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [loadPosition] must reject positions chesslib parses but then throws on, such
 * as a kingless board the server's `valid_fen` accepts.
 */
class PositionTest {

    private val startingPosition = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    @Test
    fun `a normal position loads`() {
        assertNotNull(loadPosition(startingPosition))
        assertNotNull(loadPosition("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"))
    }

    /** The attack: structurally valid to the server, fatal to the client. */
    @Test
    fun `an empty board is rejected`() {
        assertNull(loadPosition("8/8/8/8/8/8/8/8 w - - 0 1"))
    }

    @Test
    fun `a position with only one king is rejected`() {
        assertNull(loadPosition("4k3/8/8/8/8/8/8/8 w - - 0 1"))
        assertNull(loadPosition("8/8/8/8/8/8/8/4K3 w - - 0 1"))
    }

    @Test
    fun `malformed input is rejected rather than thrown`() {
        assertNull(loadPosition(""))
        assertNull(loadPosition("   "))
        assertNull(loadPosition("not a fen"))
        // Board-only FEN, missing the five trailing fields.
        assertNull(loadPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"))
    }

    /**
     * Control: these FENs parse but throw on access, so a load-only guard would
     * accept them.
     */
    @Test
    fun `the rejected positions are exactly the ones chesslib throws on`() {
        val kingless = Board().apply { loadFromFen("8/8/8/8/8/8/8/8 w - - 0 1") }
        assertThrows(Exception::class.java) { kingless.isKingAttacked }

        val oneKing = Board().apply { loadFromFen("4k3/8/8/8/8/8/8/8 w - - 0 1") }
        assertThrows(Exception::class.java) { oneKing.isKingAttacked }

        // And that a load-only guard would have accepted them: both parse.
        assertTrue(runCatching { Board().apply { loadFromFen("8/8/8/8/8/8/8/8 w - - 0 1") } }.isSuccess)
    }

    /** A returned board is usable without guarding each accessor. */
    @Test
    fun `a returned board answers the calls the composable makes`() {
        val board = loadPosition(startingPosition)
        assertNotNull(board)
        board!!
        board.isKingAttacked
        board.sideToMove
        assertTrue(board.legalMoves().isNotEmpty())
    }
}
