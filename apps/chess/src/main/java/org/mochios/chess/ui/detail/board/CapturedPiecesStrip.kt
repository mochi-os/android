// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import org.mochios.chess.R

/**
 * Display order for captured pieces — least valuable first (pawn → queen).
 * Kings are omitted; they can never be captured. Mirrors
 * `CAPTURED_PIECE_ORDER` in
 * `apps/chess/web/src/features/chess/lib/chess-pieces.ts`.
 */
private val CAPTURED_ORDER = listOf(
    PieceType.PAWN,
    PieceType.KNIGHT,
    PieceType.BISHOP,
    PieceType.ROOK,
    PieceType.QUEEN,
)

/**
 * Per-side starting piece counts. Used to derive "captured = starting -
 * current" from any FEN. Pawn=8, rook/knight/bishop=2 each, queen=1.
 */
private val STARTING_COUNTS: Map<PieceType, Int> = mapOf(
    PieceType.PAWN to 8,
    PieceType.KNIGHT to 2,
    PieceType.BISHOP to 2,
    PieceType.ROOK to 2,
    PieceType.QUEEN to 1,
)

/**
 * A captured-piece entry: piece type + count. The count is always >= 1.
 * When a side has captured zero of a given type, the entry is omitted
 * entirely — the strip renders a `--` placeholder instead.
 */
data class CapturedPiece(val type: PieceType, val count: Int)

/**
 * Derive the per-side captured-piece summary from the current FEN. Returns
 * (capturedByWhite, capturedByBlack) — i.e. what each colour has *taken
 * from the opponent*. Mirrors `getCapturedPiecesSummary` in
 * `apps/chess/web/src/features/chess/lib/captured-pieces.ts`, but operates
 * on the live position rather than the SAN move history (avoids needing
 * to re-derive the move list on every render).
 */
fun capturedPiecesFromFen(fen: String): Pair<List<CapturedPiece>, List<CapturedPiece>> {
    return try {
        val board = Board()
        board.loadFromFen(fen)

        val whiteByType = mutableMapOf<PieceType, Int>()
        val blackByType = mutableMapOf<PieceType, Int>()

        // Count what's left on the board, per side, per type.
        for (piece in board.boardToArray()) {
            if (piece == null || piece == Piece.NONE) continue
            val type = piece.pieceType ?: continue
            if (type !in STARTING_COUNTS) continue
            val bucket = if (piece.pieceSide == Side.WHITE) whiteByType else blackByType
            bucket[type] = (bucket[type] ?: 0) + 1
        }

        // captured by White = starting Black pieces - remaining Black pieces
        // (i.e. things White has taken).
        val capturedByWhite = CAPTURED_ORDER.mapNotNull { type ->
            val start = STARTING_COUNTS[type] ?: return@mapNotNull null
            val remaining = blackByType[type] ?: 0
            val taken = start - remaining
            if (taken > 0) CapturedPiece(type, taken) else null
        }
        val capturedByBlack = CAPTURED_ORDER.mapNotNull { type ->
            val start = STARTING_COUNTS[type] ?: return@mapNotNull null
            val remaining = whiteByType[type] ?: 0
            val taken = start - remaining
            if (taken > 0) CapturedPiece(type, taken) else null
        }
        capturedByWhite to capturedByBlack
    } catch (_: Exception) {
        // Defensive — if the FEN is malformed, render nothing.
        emptyList<CapturedPiece>() to emptyList<CapturedPiece>()
    }
}

/**
 * Strip showing each captured-piece type with its multiplier. Mirrors
 * `CapturedPiecesStrip` in
 * `apps/chess/web/src/features/chess/components/captured-pieces-strip.tsx`:
 * a single horizontal row of piece glyphs with small `×N` suffixes.
 *
 *  - When the side has captured nothing, renders a muted `--` placeholder
 *    so the strip's vertical space stays stable as the game progresses
 *    (avoids layout jitter the first time a piece is taken).
 *  - The piece glyphs are the colour *of the pieces captured* — i.e. the
 *    opponent's colour. The web flips with `capturedPieceColor`; this
 *    composable's `capturedByColor` parameter matches.
 *
 * @param capturedByColor Side that *did* the capturing (`'w'` or `'b'`).
 *                        Inverted internally to colour the piece glyphs.
 * @param pieces          List of captured pieces in display order.
 * @param glyphSize       Font size for each glyph; smaller in the
 *                        strip than on the board. Defaults to 16 sp.
 */
@Composable
fun CapturedPiecesStrip(
    capturedByColor: Char,
    pieces: List<CapturedPiece>,
    modifier: Modifier = Modifier,
) {
    // Border + subtle background mirror the web's bg-gradient styling.
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val capturedPieceSide = if (capturedByColor == 'w') Side.BLACK else Side.WHITE

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (pieces.isEmpty()) {
            Text(
                text = "--",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pieces.forEach { entry ->
                    CapturedPieceCell(side = capturedPieceSide, entry = entry)
                }
            }
        }
    }
}

@Composable
private fun CapturedPieceCell(side: Side, entry: CapturedPiece) {
    val piece = Piece.make(side, entry.type)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            ChessPieceIcon(
                piece = piece,
                fontSize = 20.sp,
                decorative = true,
            )
        }
        if (entry.count > 1) {
            Text(
                text = stringResource(R.string.chess_captured_multiplier, entry.count),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
