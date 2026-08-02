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
 * Positions where neither side can deliver mate by any sequence of legal moves.
 *
 * Deliberately not `Board.isInsufficientMaterial()`. chesslib counts king and
 * knight against king and knight, and king and knight against king and bishop,
 * as insufficient; chess.js — which the web client uses — does not, and neither
 * is a FIDE dead position, since mate is constructible in both. Because the
 * server accepts whatever terminal state a client reports, the broader rule let
 * Android end a game the web opponent was still playing.
 *
 * Matches chess.js: bare kings; king and one minor piece; and any number of
 * bishops provided every one of them stands on the same square colour.
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
 * Whether the position after a move is drawn, excluding stalemate — the caller
 * tests that first and reports it as its own status.
 *
 * Threefold repetition is absent on purpose: the board is rebuilt from the FEN
 * each move, so its history never holds enough positions for chesslib to see a
 * repetition. The web has the same limitation.
 */
internal fun isDrawnPosition(board: Board): Boolean =
    board.halfMoveCounter >= 100 || isDeadPosition(board)
