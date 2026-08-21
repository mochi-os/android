// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square

/**
 * Dead positions per chess.js, which the web client uses: bare kings, king and
 * one minor piece, or bishops all on one square colour. Not
 * `Board.isInsufficientMaterial()`: chesslib also counts K+N v K+N and K+N v
 * K+B, and the server accepts whatever terminal state a client reports.
 */
internal fun isDeadPosition(board: Board): Boolean {
    val minorSquares = mutableListOf<Square>()
    for (piece in Piece.values()) {
        if (piece == Piece.NONE || piece.pieceType == PieceType.KING) continue
        val squares = board.getPieceLocation(piece)
        if (squares.isEmpty()) continue
        when (piece.pieceType) {
            // A pawn, rook or queen anywhere means mate is still constructible.
            PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
            PieceType.KNIGHT -> {
                // One lone knight cannot mate; a knight alongside any other
                // minor piece can, so bail as soon as it is not alone.
                if (squares.size > 1 || minorSquares.isNotEmpty()) return false
                minorSquares += squares
            }
            PieceType.BISHOP -> minorSquares += squares
            else -> return false
        }
    }
    if (minorSquares.size <= 1) return true
    // Bishops only, and only when they share a square colour — otherwise the
    // pair covers both colours and can mate. A knight can never reach here,
    // because it returns false above the moment a second minor piece exists.
    if (board.getPieceLocation(Piece.WHITE_KNIGHT).isNotEmpty()) return false
    if (board.getPieceLocation(Piece.BLACK_KNIGHT).isNotEmpty()) return false
    val light = minorSquares.first().isLightSquare
    return minorSquares.all { it.isLightSquare == light }
}

/**
 * Drawn after a move, excluding stalemate (the caller reports that separately).
 * No threefold repetition: the board is rebuilt from the FEN each move, so it
 * has no history to repeat.
 */
internal fun isDrawnPosition(board: Board): Boolean =
    board.halfMoveCounter >= 100 || isDeadPosition(board)
