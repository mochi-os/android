// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.detail.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.mochios.go.R
import org.mochios.go.engine.GoGame
import org.mochios.go.engine.Territory
import org.mochios.go.engine.Stone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Star-point (hoshi) coordinates as 0-based (row, col); mirrors web's
 * `STAR_POINTS`.
 */
private val STAR_POINTS: Map<Int, List<Pair<Int, Int>>> = mapOf(
    9 to listOf(2 to 2, 2 to 6, 4 to 4, 6 to 2, 6 to 6),
    13 to listOf(3 to 3, 3 to 9, 6 to 6, 9 to 3, 9 to 9),
    19 to listOf(
        3 to 3, 3 to 9, 3 to 15,
        9 to 3, 9 to 9, 9 to 15,
        15 to 3, 15 to 9, 15 to 15,
    ),
)

/** Woody-tan board background matching the web `--go-board-bg` token. */
private val BoardBackground = Color(0xFFDEB887)
/** Black stone fill colour. */
private val BlackStoneFill = Color(0xFF1A1A1A)
/** Black stone outline colour. */
private val BlackStoneStroke = Color(0xFF000000)
/** White stone fill colour. */
private val WhiteStoneFill = Color(0xFFFAFAFA)
/** White stone outline colour. */
private val WhiteStoneStroke = Color(0xFF666666)
/** Grid line colour — dark brown that reads against the board background. */
private val GridLine = Color(0xFF3E2A18)
/** Last-move marker fill — vivid red for visibility on both stone colours. */
private val LastMoveMarker = Color(0xFFE53935)
/** Territory overlay fills, translucent so the grid stays readable beneath. */
private val BlackTerritory = Color(0x591A1A1A)
private val WhiteTerritory = Color(0x59FAFAFA)

/**
 * Column labels. I is skipped by Go convention, so the 19th column is T.
 * Matches the web board's `ABCDEFGHJKLMNOPQRST`.
 */
private const val COLUMN_LETTERS = "ABCDEFGHJKLMNOPQRST"

/**
 * Canvas rendering of a 9/13/19 board, square and sized to its container. Taps
 * are honoured only when the game is active and it is the local player's turn,
 * and [onPlace] fires only after local legality has been validated; the caller
 * owns the network round trip.
 */
@Composable
fun GoBoard(
    fen: String,
    previousFen: String?,
    boardSize: Int,
    myColor: Stone,
    isMyTurn: Boolean,
    gameStatus: String,
    onPlace: (row: Int, col: Int) -> Unit,
    lastMove: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    // Parsing is cheap and produces an immutable value, but cache against
    // the FEN so unrelated recompositions don't re-allocate the grid.
    val game = remember(fen, previousFen) {
        runCatching { GoGame(fen, previousFen) }.getOrNull()
    }
    val size = game?.size ?: boardSize
    val starPoints = STAR_POINTS[size] ?: emptyList()
    val isActive = gameStatus == "active"
    // A resignation ends the game before anything is counted, so the board had
    // no territory to show — it drew a marker on every empty point instead.
    val isScored = gameStatus == "finished" || gameStatus == "draw"
    val canPlay = isActive && isMyTurn && game != null

    // The canvas carried no semantics at all, so the board was invisible to a
    // screen reader. Web pairs role="application" with the same description.
    val description = when {
        !canPlay -> stringResource(R.string.go_board_a11y, size)
        myColor == Stone.WHITE -> stringResource(R.string.go_board_a11y_turn_white, size)
        else -> stringResource(R.string.go_board_a11y_turn_black, size)
    }
    val textMeasurer = rememberTextMeasurer()
    // Territory is only meaningful once the game is scored, and only web showed
    // it — the engine has computed it all along with no reader.
    val territory = remember(fen, isScored) {
        if (!isScored || game == null) null else runCatching { game.territory() }.getOrNull()
    }

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val sidePx = with(androidx.compose.ui.platform.LocalDensity.current) {
            min(maxWidth.toPx(), maxHeight.toPx())
        }
        // Padding sized as a fraction of a cell so coordinate labels and the
        // outer stones don't bump against the edge. Cell pitch is "total
        // pixels minus padding on each side" divided by the number of
        // gaps (size - 1).
        val padding = sidePx * 0.07f
        val boardPx = sidePx - padding * 2
        val cellPx = boardPx / (size - 1).coerceAtLeast(1)

        // Map the tap to the nearest intersection; more than half a cell from a
        // line is ignored. canPlay implies game != null, but the smart cast
        // needs the explicit check inside the lambda.
        val tapModifier = if (canPlay) {
            Modifier.pointerInput(size, cellPx, padding, fen) {
                detectTapGestures(onTap = { tap ->
                    val col = ((tap.x - padding) / cellPx).roundToInt()
                    val row = ((tap.y - padding) / cellPx).roundToInt()
                    if (row !in 0 until size || col !in 0 until size) return@detectTapGestures
                    // Reject taps that landed too far from any intersection
                    // (e.g. on the coordinate margin); helps prevent the
                    // border tap from being interpreted as a 0,0 move.
                    val dx = tap.x - (padding + col * cellPx)
                    val dy = tap.y - (padding + row * cellPx)
                    val tolerance = cellPx * 0.5f
                    // Per-axis, not radial: roundToInt already picked the
                    // nearest intersection, and a radial test would leave the
                    // corners of every cell dead.
                    if (abs(dx) > tolerance || abs(dy) > tolerance) return@detectTapGestures
                    val safeGame = game ?: return@detectTapGestures
                    if (!safeGame.isLegal(row, col)) return@detectTapGestures
                    onPlace(row, col)
                })
            }
        } else {
            Modifier
        }

        val shape = RoundedCornerShape(BOARD_CORNER)
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = shape,
                )
                .semantics { contentDescription = description }
                .then(tapModifier)
        ) {
            // Board background fills the whole composable rect — wood-tan
            // matches the web `--go-board-bg` token.
            drawRect(color = BoardBackground)

            // Grid lines. The outermost lines get a thicker stroke than the
            // interior lines so the playing area reads as a framed box,
            // matching the web `strokeWidth={i === 0 || i === size - 1 ? 1.5 : 0.8}`.
            val outerStroke = max(1.5f, cellPx * 0.04f)
            val innerStroke = max(0.8f, cellPx * 0.02f)
            for (i in 0 until size) {
                val y = padding + i * cellPx
                val x = padding + i * cellPx
                val isOuter = i == 0 || i == size - 1
                val w = if (isOuter) outerStroke else innerStroke
                // Horizontal
                drawLine(
                    color = GridLine,
                    start = Offset(padding, y),
                    end = Offset(padding + boardPx, y),
                    strokeWidth = w,
                )
                // Vertical
                drawLine(
                    color = GridLine,
                    start = Offset(x, padding),
                    end = Offset(x, padding + boardPx),
                    strokeWidth = w,
                )
            }

            // Hoshi (star points). 5dp on web → ~12% of a cell scales nicely
            // across both small and large boards.
            val starRadius = max(2.dp.toPx(), cellPx * 0.12f)
            for ((r, c) in starPoints) {
                drawCircle(
                    color = GridLine,
                    radius = starRadius,
                    center = Offset(padding + c * cellPx, padding + r * cellPx),
                )
            }

            // Stones. We pull the grid from the parsed engine instance;
            // when the FEN fails to parse we silently skip stones so the
            // board still renders rather than crashing the screen.
            if (game != null) {
                val stoneRadius = cellPx * 0.45f
                val stoneStrokeWidth = max(0.5f, cellPx * 0.02f)
                for (r in 0 until size) {
                    for (c in 0 until size) {
                        val stone = game.getStone(r, c)
                        if (stone == GoGame.EMPTY) continue
                        val cx = padding + c * cellPx
                        val cy = padding + r * cellPx
                        val (fill, ring) = if (stone == GoGame.BLACK) {
                            BlackStoneFill to BlackStoneStroke
                        } else {
                            WhiteStoneFill to WhiteStoneStroke
                        }
                        drawCircle(
                            color = fill,
                            radius = stoneRadius,
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = ring,
                            radius = stoneRadius,
                            center = Offset(cx, cy),
                            style = Stroke(width = stoneStrokeWidth),
                        )
                    }
                }

                // Territory overlay on a finished game: a translucent square at
                // each point the scoring counts, so the result is legible on the
                // board rather than only in the score line.
                if (territory != null) {
                    val half = cellPx * 0.16f
                    for (r in 0 until size) {
                        for (c in 0 until size) {
                            val owner = territory[r][c]
                            val fill = when (owner) {
                                Territory.BLACK -> BlackTerritory
                                Territory.WHITE -> WhiteTerritory
                                else -> null
                            } ?: continue
                            val cx = padding + c * cellPx
                            val cy = padding + r * cellPx
                            drawRect(
                                color = fill,
                                topLeft = Offset(cx - half, cy - half),
                                size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                            )
                        }
                    }
                }

                // Last-move marker: a red dot reads on both stone colours (web
                // uses a ring).
                if (lastMove != null) {
                    val (lr, lc) = lastMove
                    // The stone check matters: lastMove is never cleared, so
                    // once a reply captures that stone the marker would sit on
                    // a bare intersection. Web guards the same way.
                    val stillThere = lr in 0 until size && lc in 0 until size &&
                        game.getStone(lr, lc) != GoGame.EMPTY
                    if (stillThere) {
                        val cx = padding + lc * cellPx
                        val cy = padding + lr * cellPx
                        drawCircle(
                            color = LastMoveMarker,
                            radius = max(2.dp.toPx(), cellPx * 0.18f),
                            center = Offset(cx, cy),
                        )
                    }
                }
            }

            // Coordinates sit centred in the margin the padding leaves, the
            // way the web board spaces them: letters above and below, numbers
            // to either side, none of them touching the grid or the edge.
            val labelPx = min(padding * 0.62f, cellPx * 0.5f)
            val labelStyle = TextStyle(color = GridLine, fontSize = labelPx.toSp())
            val topBand = padding / 2f
            val bottomBand = padding + boardPx + padding / 2f
            for (i in 0 until size) {
                val letter = COLUMN_LETTERS.getOrNull(i)?.toString() ?: continue
                val number = (size - i).toString()
                val x = padding + i * cellPx
                val y = padding + i * cellPx
                val column = textMeasurer.measure(letter, labelStyle)
                val row = textMeasurer.measure(number, labelStyle)
                drawText(
                    column,
                    topLeft = Offset(
                        x - column.size.width / 2f,
                        topBand - column.size.height / 2f,
                    ),
                )
                drawText(
                    column,
                    topLeft = Offset(
                        x - column.size.width / 2f,
                        bottomBand - column.size.height / 2f,
                    ),
                )
                drawText(
                    row,
                    topLeft = Offset(
                        topBand - row.size.width / 2f,
                        y - row.size.height / 2f,
                    ),
                )
                drawText(
                    row,
                    topLeft = Offset(
                        bottomBand - row.size.width / 2f,
                        y - row.size.height / 2f,
                    ),
                )
            }
        }
    }
}

/** Board corner radius, matching the chess and words boards. */
private val BOARD_CORNER = 6.dp
