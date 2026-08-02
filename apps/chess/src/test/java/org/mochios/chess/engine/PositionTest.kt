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
 * The board composable used to guard only the FEN load, then call
 * isKingAttacked, legalMoves and getKingSquare on the result. A kingless
 * position parses without complaint and throws from those, so a peer could
 * write one into the shared row — the server's valid_fen never checks that
 * kings are present — and crash the opponent's client on every open, with the
 * resign and delete actions unreachable behind the crash.
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
     * Control. Proves these FENs really are the crashing kind — without it the
     * nulls above could be passing for some unrelated parse failure, and a
     * regression to a load-only guard would still satisfy every other test.
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
