// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail.board

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
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
 * One of the twelve chess piece glyphs ('K', 'Q', 'R', 'B', 'N', 'P' × white,
 * black) rendered as a single Unicode chess character.
 *
 * The web implementation hand-codes complex SVG bodies so the pieces stay
 * crisp at every zoom level; on Android the system font already includes the
 * Unicode chess range (U+2654..U+265F) at full vector quality, so a single
 * [Text] suffices. Using one [Text] keeps memory low — even a long game
 * keeps only 64 of these in the composition.
 *
 * For visibility on both light and dark board squares we pair each glyph
 * with a faint contrasting halo behind it. The white glyph (filled white
 * king/queen/etc.) is drawn over a dark halo so it stands out on light
 * squares; the black glyph gets a light halo for dark squares. This is the
 * Material-Symbols-style trick that the web SVGs achieve via stroke outlines.
 *
 * @param piece    The chesslib piece value (color + type). Pass
 *                 [Piece.NONE] only when you want the composable to render
 *                 nothing — it short-circuits.
 * @param fontSize Font size for the glyph. The board sizes this from the
 *                 square's measured dimensions so it always fills ~85% of
 *                 the square.
 * @param decorative When true, omit the accessibility content description.
 *                   Used by the captured-pieces strip where each glyph is
 *                   already announced as part of a parent semantics block.
 * @param contentDescription Explicit accessibility label; takes precedence
 *                           over the auto-generated "White pawn" / "Black
 *                           king" label.
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

    // The Unicode chess glyphs U+2654-2659 are white "outline" pieces and
    // U+265A-265F are black "filled" pieces. Rendering both at the same
    // typeface produces inconsistent weights on most Android fonts (the
    // white pieces look skeletal next to the heavy black ones). We
    // sidestep that by always rendering the *filled* black glyph and
    // colouring it ourselves — white side gets a light fill with a dark
    // border-style shadow, black side gets a dark fill with a light
    // shadow.
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
 * Auto-generated content description for accessibility services. The web
 * delegates to a translated `pieceName(type)` lookup; we keep the Android
 * fallback in English for now since these are announce-only — the
 * full per-piece names are emitted through [pieceLabel] when callers
 * have a string-resource context to resolve from.
 */
private fun defaultPieceContentDescription(piece: Piece): String {
    val side = if (piece.pieceSide == Side.WHITE) "White" else "Black"
    val type = when (piece.pieceType) {
        PieceType.KING -> "king"
        PieceType.QUEEN -> "queen"
        PieceType.ROOK -> "rook"
        PieceType.BISHOP -> "bishop"
        PieceType.KNIGHT -> "knight"
        PieceType.PAWN -> "pawn"
        else -> ""
    }
    return "$side $type"
}

/**
 * Resolve a [PieceType] (color-agnostic) to its display label. Callers in a
 * composable scope should prefer this — it picks up the locale from
 * [androidx.compose.ui.res.stringResource].
 */
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
