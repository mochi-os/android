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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import org.mochios.chess.R
import org.mochios.chess.engine.Record
import org.mochios.chess.engine.loadPosition
import org.mochios.android.i18n.LocalFormat

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

private val STRIP_TEXT = Color(0xFF111827)

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
 * (captured by White, captured by Black), read from the game's [record]: the
 * moves that took something, on top of whatever its starting position already
 * lacked. Counting the position alone credits a promoted pawn as a capture. A
 * record lost to an older client starts at the position, which then gives its
 * count alone.
 */
fun capturedPieces(record: Record): Pair<List<CapturedPiece>, List<CapturedPiece>> {
    val (white, black) = missing(record.start)
    for (move in record.moves) {
        val type = move.captured ?: continue
        // White taking a piece leaves Black without it, and the other way round.
        val loser = if (move.side == Side.WHITE) black else white
        loser[type] = (loser[type] ?: 0) + 1
    }
    return pieces(black) to pieces(white)
}

/**
 * What White and Black lack of their starting sets in [fen]. A piece above its
 * starting count is a promoted pawn, so it is counted back as that pawn rather
 * than as a pawn taken.
 */
private fun missing(fen: String): Pair<MutableMap<PieceType, Int>, MutableMap<PieceType, Int>> {
    val board = loadPosition(fen) ?: return mutableMapOf<PieceType, Int>() to mutableMapOf()
    fun side(side: Side): MutableMap<PieceType, Int> {
        val have = CAPTURED_ORDER.associateWith { type -> board.getPieceLocation(Piece.make(side, type)).size }
        val gone = mutableMapOf<PieceType, Int>()
        var promoted = 0
        for (type in CAPTURED_ORDER) {
            if (type == PieceType.PAWN) continue
            val start = STARTING_COUNTS.getValue(type)
            promoted += (have.getValue(type) - start).coerceAtLeast(0)
            gone[type] = (start - have.getValue(type)).coerceAtLeast(0)
        }
        gone[PieceType.PAWN] = (STARTING_COUNTS.getValue(PieceType.PAWN) - have.getValue(PieceType.PAWN) - promoted)
            .coerceAtLeast(0)
        return gone
    }
    return side(Side.WHITE) to side(Side.BLACK)
}

private fun pieces(counts: Map<PieceType, Int>): List<CapturedPiece> =
    CAPTURED_ORDER.mapNotNull { type ->
        val count = counts[type] ?: 0
        if (count > 0) CapturedPiece(type, count) else null
    }

/**
 * Row of captured-piece glyphs with `×N` suffixes, in the colour of the pieces
 * taken (the opponent of [capturedByColor]). Renders a `--` placeholder when
 * empty so the strip's height never jumps. The pieces sit on the board square
 * colour they contrast with - white on the dark square, black on the light -
 * since the glyphs carry no outline and a theme surface can match either.
 */
@Composable
fun CapturedPiecesStrip(
    capturedByColor: Char,
    pieces: List<CapturedPiece>,
    modifier: Modifier = Modifier,
) {
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val capturedPieceSide = if (capturedByColor == 'w') Side.BLACK else Side.WHITE
    val background = if (capturedPieceSide == Side.WHITE) DARK_SQUARE else LIGHT_SQUARE

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (pieces.isEmpty()) {
            Text(
                text = "--",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = STRIP_TEXT.copy(alpha = 0.5f),
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
                text = stringResource(R.string.chess_captured_multiplier, LocalFormat.current.formatNumber(entry.count)),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = STRIP_TEXT,
            )
        }
    }
}
