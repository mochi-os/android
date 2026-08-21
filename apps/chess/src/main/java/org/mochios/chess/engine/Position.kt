// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece

/**
 * Load [fen], or null when chesslib would throw on it. A kingless position
 * parses but throws from `isKingAttacked` / `legalMoves` / `getKingSquare`, and
 * the server's `valid_fen` does not check for kings. `loadFromFen` clears the
 * board first, so a failed load leaves it empty, not at the start.
 */
fun loadPosition(fen: String): Board? {
    if (fen.isBlank()) return null
    return runCatching {
        val board = Board()
        board.loadFromFen(fen)
        // Both kings, checked explicitly rather than inferred from the probes
        // below: those exercise the side to move, so a board holding only
        // White's king with White to move answers them all quite happily and
        // then throws the moment the position is read from Black's side.
        if (board.getPieceLocation(Piece.WHITE_KING).isEmpty()) return null
        if (board.getPieceLocation(Piece.BLACK_KING).isEmpty()) return null
        // Probe the accessors the board composable reads. Any of them throwing
        // means the position cannot be rendered or played.
        board.isKingAttacked
        board.sideToMove
        board.legalMoves()
        board
    }.getOrNull()
}
