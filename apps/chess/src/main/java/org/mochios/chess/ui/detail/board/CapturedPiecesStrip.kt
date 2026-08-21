// Copyright © 2026 Mochisoft OÜ
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
 * Least valuable first; mirrors the web's `CAPTURED_PIECE_ORDER`.
 */
private val CAPTURED_ORDER = listOf(
    PieceType.PAWN,
    PieceType.KNIGHT,
    PieceType.BISHOP,
    PieceType.ROOK,
    PieceType.QUEEN,
)

private val STARTING_COUNTS: Map<PieceType, Int> = mapOf(
    PieceType.PAWN to 8,
    PieceType.KNIGHT to 2,
    PieceType.BISHOP to 2,
    PieceType.ROOK to 2,
    PieceType.QUEEN to 1,
)

/**
 * One captured type; [count] is at least 1, zero-count types are omitted.
 */
data class CapturedPiece(val type: PieceType, val count: Int)

/**
 * (captured by White, captured by Black), derived from the position as starting
 * counts minus what remains, not from the move history.
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
 * Row of captured-piece glyphs with `×N` suffixes, in the colour of the pieces
 * taken (the opponent of [capturedByColor]). Renders a `--` placeholder when
 * empty so the strip's height never jumps.
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
