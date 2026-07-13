// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square

// Web tokens — `--chess-sq-light` / `--chess-sq-dark`. Inlined here because
// the Android theme system doesn't carry app-specific tokens yet; matches the
// values in `apps/chess/web/src/themes/chess.css`.
private val LIGHT_SQUARE = Color(0xFFF0D9B5)
private val DARK_SQUARE = Color(0xFFB58863)
private val LAST_MOVE_HIGHLIGHT = Color(0x55FACC15) // yellow-400 / 33%
private val CHECK_HIGHLIGHT = Color(0xCCEF4444) // red-500 / 80%
private val LEGAL_TARGET_DOT = Color(0x66059669) // emerald-600 / 40%

private val FILES = listOf("a", "b", "c", "d", "e", "f", "g", "h")
private val RANKS = listOf("8", "7", "6", "5", "4", "3", "2", "1")

/**
 * The 8×8 chess board. Mirrors `apps/chess/web/src/features/chess/components/chess-board.tsx`:
 *
 *  - Container-sized square — wrapped in [BoxWithConstraints] so the
 *    parent can pass in any rectangular bounds; the board fills the
 *    smaller of the two with a 1:1 aspect ratio.
 *  - Tap-to-select then tap-to-move input. Selecting your own piece
 *    highlights every legal target square; tapping a target submits
 *    [onMove]. Pawn-to-back-rank targets open a [PromotionDialog] first.
 *  - Orientation flips when [myColor] == `'b'` so Black plays with rank 1
 *    at the top.
 *  - Last-move highlight (faint yellow) on the `from`/`to` squares.
 *  - Check highlight (red) on the side-to-move's king square when in
 *    check — the side-to-move's king is the one actually under attack
 *    (chesslib's `isKingAttacked()` checks the side-to-move).
 *  - File/rank coordinate labels in the squares' corners — file letters
 *    along the bottom rank, rank numbers along the leftmost file. The
 *    labels respect [myColor] orientation.
 *
 * @param fen         Current position in FEN notation. The board is
 *                    re-derived from this on every render; chesslib's
 *                    `Board.loadFromFen` is cheap (~microseconds).
 * @param myColor     'w' or 'b' — which side the local player is. Drives
 *                    orientation, drag-source legality, and the
 *                    promotion-target row check.
 * @param isMyTurn    True iff `chess.turn() == myColor`. Disables square
 *                    taps when false (the player can't move on the
 *                    opponent's turn).
 * @param gameStatus  One of `"active" | "checkmate" | "stalemate" | "draw"
 *                    | "resigned"`. Only `"active"` accepts input.
 * @param onMove      Fired when the user has chosen a complete move
 *                    (selection + target, plus promotion code if it was
 *                    a back-rank pawn move).
 * @param lastMove    Most-recent move's from/to squares. Highlighted in
 *                    faint yellow so the user can see what the opponent
 *                    just played.
 */
@Composable
fun ChessBoard(
    fen: String,
    myColor: Char,
    isMyTurn: Boolean,
    gameStatus: String,
    onMove: (from: String, to: String, promotion: String?) -> Unit,
    lastMove: Pair<String, String>?,
    modifier: Modifier = Modifier,
) {
    val board = remember(fen) {
        Board().also {
            try {
                it.loadFromFen(fen)
            } catch (_: Exception) {
                // Leave at starting position on malformed FEN. The board
                // will simply look like a fresh game until the next valid
                // update arrives.
            }
        }
    }

    val isActive = gameStatus == "active"
    val inCheck = remember(fen) { board.isKingAttacked }
    val sideToMove = board.sideToMove

    var selected by remember(fen) { mutableStateOf<String?>(null) }
    var legalTargets by remember(fen) { mutableStateOf<Set<String>>(emptySet()) }
    var promotionPending by remember(fen) { mutableStateOf<Pair<String, String>?>(null) }

    // If selection state outlives a position change (e.g. server WS push),
    // reset so the user isn't holding a stale highlight on a moved piece.
    LaunchedEffect(fen) {
        selected = null
        legalTargets = emptySet()
        promotionPending = null
    }

    val ranks = if (myColor == 'w') RANKS else RANKS.asReversed()
    val files = if (myColor == 'w') FILES else FILES.asReversed()

    fun squareKey(file: String, rank: String): String = "$file$rank"

    fun selectSquare(square: String) {
        val piece = board.getPiece(parseSquare(square))
        if (piece == Piece.NONE) {
            selected = null
            legalTargets = emptySet()
            return
        }
        val pieceSide = piece.pieceSide
        val mySide = if (myColor == 'w') Side.WHITE else Side.BLACK
        if (pieceSide != mySide) {
            selected = null
            legalTargets = emptySet()
            return
        }
        selected = square
        legalTargets = board.legalMoves()
            .filter { it.from.value().equals(square, ignoreCase = true) }
            .map { it.to.value().lowercase() }
            .toSet()
    }

    fun attemptMove(from: String, to: String) {
        // Detect pawn-to-back-rank: if the moving piece is a pawn and the
        // destination is rank 1 or 8, ask the user which piece to promote
        // to before sending. chess.js / web do the same shape — see
        // chess-board.tsx handleSquareClick.
        val movingPiece = board.getPiece(parseSquare(from))
        val isPawn = movingPiece.pieceType == PieceType.PAWN
        val backRank = if (myColor == 'w') '8' else '1'
        if (isPawn && to.length == 2 && to[1] == backRank) {
            promotionPending = from to to
        } else {
            onMove(from, to, null)
        }
    }

    fun handleSquareTap(square: String) {
        if (!isActive || !isMyTurn) return
        val current = selected
        if (current != null) {
            if (square in legalTargets) {
                selected = null
                legalTargets = emptySet()
                attemptMove(current, square)
                return
            }
        }
        selectSquare(square)
    }

    val kingInCheckSquare = remember(fen, inCheck, sideToMove) {
        if (inCheck) board.getKingSquare(sideToMove)?.takeIf { it != Square.NONE } else null
    }

    Box(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val sideDp = minOf(maxWidth, maxHeight)
            val squareDp = sideDp / 8f
            val glyphSize = pieceGlyphSize(squareDp.value)
            val labelSize = (squareDp.value * 0.13f).coerceIn(8f, 12f).sp

            Column(
                modifier = Modifier
                    .width(sideDp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                    ),
            ) {
                ranks.forEachIndexed { ri, rank ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        files.forEachIndexed { fi, file ->
                            val square = squareKey(file, rank)
                            val isLight = (ri + fi) % 2 == 0
                            val isSelected = selected == square
                            val isLegal = square in legalTargets
                            val isLastMove = lastMove != null &&
                                (lastMove.first.equals(square, ignoreCase = true) ||
                                    lastMove.second.equals(square, ignoreCase = true))
                            val isCheckSquare = kingInCheckSquare != null &&
                                kingInCheckSquare.value().equals(square, ignoreCase = true)

                            BoardSquare(
                                file = file,
                                rank = rank,
                                showFileLabel = ri == 7, // bottom rank
                                showRankLabel = fi == 0, // leftmost file
                                squareSize = squareDp,
                                glyphSize = glyphSize,
                                labelSize = labelSize,
                                isLight = isLight,
                                isSelected = isSelected,
                                isLegalTarget = isLegal,
                                isLastMoveSquare = isLastMove,
                                isCheckSquare = isCheckSquare,
                                piece = board.getPiece(parseSquare(square)),
                                onTap = { handleSquareTap(square) },
                            )
                        }
                    }
                }
            }
        }
    }

    promotionPending?.let { (from, to) ->
        PromotionDialog(
            myColor = myColor,
            onSelect = { code ->
                promotionPending = null
                onMove(from, to, code)
            },
            onDismiss = {
                promotionPending = null
            },
        )
    }
}

/** Map a 2-char square string ("e2") to a [Square]. Defaults to [Square.NONE]. */
private fun parseSquare(square: String): Square {
    return try {
        Square.valueOf(square.uppercase())
    } catch (_: IllegalArgumentException) {
        Square.NONE
    }
}

@Composable
private fun BoardSquare(
    file: String,
    rank: String,
    showFileLabel: Boolean,
    showRankLabel: Boolean,
    squareSize: androidx.compose.ui.unit.Dp,
    glyphSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    isLight: Boolean,
    isSelected: Boolean,
    isLegalTarget: Boolean,
    isLastMoveSquare: Boolean,
    isCheckSquare: Boolean,
    piece: Piece,
    onTap: () -> Unit,
) {
    val baseColor = if (isLight) LIGHT_SQUARE else DARK_SQUARE
    val labelColor = if (isLight) DARK_SQUARE.copy(alpha = 0.7f) else LIGHT_SQUARE.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .width(squareSize)
            .aspectRatio(1f)
            .background(baseColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Order matters — we paint highlights below the piece.
        if (isLastMoveSquare) {
            Box(modifier = Modifier.fillMaxSize().background(LAST_MOVE_HIGHLIGHT))
        }
        if (isCheckSquare) {
            Box(modifier = Modifier.fillMaxSize().background(CHECK_HIGHLIGHT.copy(alpha = 0.45f)))
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        color = Color(0xCC2563EB), // blue-600 / 80%
                    ),
            )
        }

        if (piece != Piece.NONE) {
            ChessPieceIcon(
                piece = piece,
                fontSize = glyphSize,
            )
        }

        // Legal-move indicator — only show when the user has a selection.
        if (isLegalTarget && piece == Piece.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.33f)
                    .clip(RoundedCornerShape(50))
                    .background(LEGAL_TARGET_DOT),
            )
        }
        if (isLegalTarget && piece != Piece.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 3.dp,
                        color = LEGAL_TARGET_DOT.copy(alpha = 0.65f),
                    ),
            )
        }

        // Coordinate labels — file letters on the bottom rank, rank numbers
        // on the leftmost file. Tiny, low-contrast so they don't fight with
        // the pieces.
        if (showRankLabel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 2.dp, top = 1.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = rank,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                )
            }
        }
        if (showFileLabel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 3.dp, bottom = 1.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = file,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                )
            }
        }
    }
}
