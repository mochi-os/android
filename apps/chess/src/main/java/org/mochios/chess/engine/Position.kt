// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece

/**
 * Load [fen] into a board, or return null when the result is unusable.
 *
 * Loading is not enough on its own. A position with no king parses perfectly
 * happily, and chesslib then throws from `isKingAttacked`, `legalMoves` and
 * `getKingSquare` — so this probes the board before handing it back, and a
 * caller that holds a non-null result can use it without guarding every call.
 *
 * That matters because the server does not stop such a position reaching us:
 * `valid_fen` in chess.star checks the field count, the side to move, eight
 * rows and eight squares per row, but never that the kings are present. A peer
 * can therefore write `8/8/8/8/8/8/8/8 w - - 0 1` into the shared row, and the
 * board used to crash on every open with no way for the user to leave the game.
 *
 * Note also that `loadFromFen` clears the board before parsing, so a failed
 * load leaves it EMPTY rather than at the starting position — which is what the
 * old catch-and-continue here quietly relied on being untrue.
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
