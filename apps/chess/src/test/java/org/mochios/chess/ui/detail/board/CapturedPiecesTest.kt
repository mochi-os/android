// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail.board

import com.github.bhlangonijr.chesslib.PieceType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.chess.engine.Record
import org.mochios.chess.engine.START

/**
 * The strips are read from the record, so a promoted pawn is not counted as
 * taken; a record lost to an older client counts from the position.
 */
class CapturedPiecesTest {

    private fun record(vararg moves: Pair<String, String>, start: String = START): Record {
        val record = Record.resume(start, "")!!
        for ((from, to) in moves) {
            val promotion = if (to.endsWith("8") && from[1] == '7') "q" else null
            requireNotNull(record.play(from, to, promotion)) { "$from$to" }
        }
        return record
    }

    @Test
    fun `each side's captures are counted`() {
        val (white, black) = capturedPieces(record("e2" to "e4", "d7" to "d5", "e4" to "d5", "d8" to "d5"))
        assertEquals(listOf(CapturedPiece(PieceType.PAWN, 1)), white)
        assertEquals(listOf(CapturedPiece(PieceType.PAWN, 1)), black)
    }

    @Test
    fun `a promoted pawn is not counted as taken`() {
        // White takes three pawns and a rook; its e-pawn becomes a queen.
        val game = record(
            "e2" to "e4", "d7" to "d5", "e4" to "d5", "c7" to "c6", "d5" to "c6",
            "g8" to "f6", "c6" to "b7", "b8" to "d7", "b7" to "a8",
        )
        val (white, black) = capturedPieces(game)
        assertEquals(listOf(CapturedPiece(PieceType.PAWN, 3), CapturedPiece(PieceType.ROOK, 1)), white)
        assertEquals("THE POINT: Black took nothing", emptyList<CapturedPiece>(), black)
    }

    @Test
    fun `a capture en passant is counted`() {
        val (white, _) = capturedPieces(record("e2" to "e4", "a7" to "a6", "e4" to "e5", "d7" to "d5", "e5" to "d6"))
        assertEquals(listOf(CapturedPiece(PieceType.PAWN, 1)), white)
    }

    @Test
    fun `a lost record counts from the position, a surplus queen as a promoted pawn`() {
        val lost = Record.resume("4k3/8/8/8/8/8/8/QQ2K3 w - - 0 1", "1. d4 d5")!!
        val (_, black) = capturedPieces(lost)
        assertEquals(
            listOf(
                CapturedPiece(PieceType.PAWN, 7),
                CapturedPiece(PieceType.KNIGHT, 2),
                CapturedPiece(PieceType.BISHOP, 2),
                CapturedPiece(PieceType.ROOK, 2),
            ),
            black,
        )
    }
}
