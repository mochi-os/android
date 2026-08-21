// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail.board

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side

/**
 * One piece as a Unicode chess glyph with a contrasting halo. [decorative]
 * drops the content description (the captured strip announces its own);
 * [contentDescription] overrides the default.
 */
@Composable
fun ChessPieceIcon(
    piece: Piece,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    decorative: Boolean = false,
    contentDescription: String? = null,
) {
    if (piece == Piece.NONE) return

    val glyph = pieceGlyph(piece) ?: return
    val isWhite = piece.pieceSide == Side.WHITE

    // Always the filled (black) glyph, coloured per side: the outline white
    // glyphs U+2654-2659 render far lighter than the filled ones on most
    // Android fonts.
    val fillColor = if (isWhite) {
        Color(0xFFF8FAFC) // slate-50
    } else {
        Color(0xFF111827) // gray-900
    }
    val shadowColor = if (isWhite) {
        Color(0xFF111827) // gray-900
    } else {
        Color(0xFFE5E7EB) // gray-200
    }

    val sem: Modifier = if (decorative) {
        Modifier
    } else {
        val cd = contentDescription ?: defaultPieceContentDescription(piece)
        Modifier.semantics { this.contentDescription = cd }
    }

    Box(
        modifier = modifier.then(sem),
        contentAlignment = Alignment.Center,
    ) {
        // Shadow layer — same glyph offset 1px (logical, in sp space) so the
        // figure has a discernible outline regardless of square colour.
        Text(
            text = glyph,
            color = shadowColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(0.35f),
        )
        Text(
            text = glyph,
            color = fillColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Returns the Unicode chess glyph for [piece], or null when [Piece.NONE]. */
private fun pieceGlyph(piece: Piece): String? {
    // We always render the "filled" black-side Unicode glyphs and tint to
    // the right colour; see the colouring note in [ChessPieceIcon]. This
    // gives identical stroke weight for both sides at every font size.
    return when (piece.pieceType) {
        PieceType.KING -> "♚"   // ♚
        PieceType.QUEEN -> "♛"  // ♛
        PieceType.ROOK -> "♜"   // ♜
        PieceType.BISHOP -> "♝" // ♝
        PieceType.KNIGHT -> "♞" // ♞
        PieceType.PAWN -> "♟"   // ♟
        else -> null
    }
}

/**
 * TalkBack label for an occupied square, composed from the translated piece
 * nouns.
 */
@Composable
private fun defaultPieceContentDescription(piece: Piece): String {
    val name = pieceLabel(piece.pieceType)
    val template = if (piece.pieceSide == Side.WHITE) {
        org.mochios.chess.R.string.chess_piece_white
    } else {
        org.mochios.chess.R.string.chess_piece_black
    }
    return androidx.compose.ui.res.stringResource(template, name)
}

@Composable
fun pieceLabel(type: PieceType): String {
    // Inlined string resources for now — keep dependencies on the chess
    // R module minimal. Localisation is provided by the catalog entries
    // declared in apps/chess/src/main/res/values/strings.xml.
    return when (type) {
        PieceType.KING -> androidx.compose.ui.res.stringResource(org.mochios.chess.R.string.chess_piece_king)
        PieceType.QUEEN -> androidx.compose.ui.res.stringResource(org.mochios.chess.R.string.chess_piece_queen)
        PieceType.ROOK -> androidx.compose.ui.res.stringResource(org.mochios.chess.R.string.chess_piece_rook)
        PieceType.BISHOP -> androidx.compose.ui.res.stringResource(org.mochios.chess.R.string.chess_piece_bishop)
        PieceType.KNIGHT -> androidx.compose.ui.res.stringResource(org.mochios.chess.R.string.chess_piece_knight)
        PieceType.PAWN -> androidx.compose.ui.res.stringResource(org.mochios.chess.R.string.chess_piece_pawn)
        else -> ""
    }
}

/** Default glyph font size based on a square edge of [squareDp]. */
fun pieceGlyphSize(squareDp: Float): TextUnit {
    // 85% of the square edge, taken in sp. Compose Text auto-scales with
    // the user's font-size preference; the board already locks its
    // overall size via aspectRatio + BoxWithConstraints so this stays
    // proportional to the visible board.
    return (squareDp * 0.85f).sp
}
