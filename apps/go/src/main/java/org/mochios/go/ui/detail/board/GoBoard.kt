package org.mochios.go.ui.detail.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.mochios.go.engine.GoGame
import org.mochios.go.engine.Stone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Static star-point (hoshi) coordinates for the three supported board sizes.
 * Mirrors `STAR_POINTS` in `apps/go/web/src/features/go/components/go-board.tsx`.
 * Indices are (row, col) in 0-based grid coordinates.
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

/**
 * Compose `Canvas`-based rendering of a 9/13/19-line Go board.
 *
 * The board is square (`aspectRatio(1f)`) and sizes itself to the container
 * via [BoxWithConstraints], so the same composable adapts to both the
 * desktop side-by-side layout (large fixed-width column) and the phone
 * full-width layout (≤ ~480 dp wide).
 *
 * @param fen          Current position FEN (board + metadata, as serialised
 *                     by [GoGame]). The board parses this on each
 *                     recomposition; cheap for 19x19.
 * @param previousFen  Optional prior-position FEN for ko enforcement when
 *                     local-validating a tap.
 * @param boardSize    9, 13, or 19. Used to look up star-point positions
 *                     when the FEN can't be parsed (defensive fallback).
 * @param myColor      The local player's stone colour. Only used for the
 *                     hover/preview rendering on web; on touch we have no
 *                     hover, but kept on the API for parity with the web
 *                     `GoBoard` and to allow future ghost-stone affordance.
 * @param isMyTurn     True when it's the local player's turn. Combined with
 *                     [gameStatus] to decide whether to honour taps.
 * @param gameStatus   Server-reported game status (`"active"`, `"finished"`,
 *                     `"resigned"`, `"draw"`). Taps are ignored on
 *                     non-active games.
 * @param onPlace      Invoked with the tapped grid coordinates **after**
 *                     local legality has been validated. The caller still
 *                     owns the network round-trip.
 * @param lastMove     Optional (row, col) of the most recent stone — drawn
 *                     with a red dot on top of the stone for quick scan.
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
    val canPlay = isActive && isMyTurn && game != null

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val sidePx = with(androidx.compose.ui.platform.LocalDensity.current) {
            min(maxWidth.toPx(), maxHeight.toPx())
        }
        // Padding sized as a fraction of a cell so coordinate labels and the
        // outer stones don't bump against the edge. Cell pitch is "total
        // pixels minus padding on each side" divided by the number of
        // gaps (size - 1).
        val padding = sidePx * 0.05f
        val boardPx = sidePx - padding * 2
        val cellPx = boardPx / (size - 1).coerceAtLeast(1)

        // Tap handler is registered on the whole canvas; we map the tap
        // offset back to the nearest grid intersection. Anything more than
        // half a cell from any line is ignored (saves a stray edge tap from
        // selecting a corner intersection).
        // canPlay already implies game != null, but the smart-cast below
        // needs the explicit null-check inside the branch.
        val tapModifier = if (canPlay) {
            Modifier.pointerInput(size, cellPx, padding, fen) {
                detectTapGestures(onTap = { tap ->
                    val col = ((tap.x - padding) / cellPx).roundToInt()
                    val row = ((tap.y - padding) / cellPx).roundToInt()
                    if (row !in 0 until size || col !in 0 until size) return@detectTapGestures
                    // Reject taps that landed too far from any intersection
                    // (e.g. on the coordinate margin); helps prevent the
                    // border tap from being interpreted as a 0,0 move.
                    val nearestX = padding + col * cellPx
                    val nearestY = padding + row * cellPx
                    val dx = tap.x - nearestX
                    val dy = tap.y - nearestY
                    val tolerance = cellPx * 0.5f
                    if (dx * dx + dy * dy > tolerance * tolerance) return@detectTapGestures
                    val safeGame = game ?: return@detectTapGestures
                    if (!safeGame.isLegal(row, col)) return@detectTapGestures
                    onPlace(row, col)
                })
            }
        } else {
            Modifier
        }

        Canvas(modifier = Modifier.matchParentSize().then(tapModifier)) {
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

                // Last-move marker: small red dot on top of the most recent
                // stone. Red was chosen for legibility on both black and
                // white stones — the web uses a coloured ring of the
                // opposite stone tone, but the dot reads clearer at the
                // tighter pixel densities phones produce.
                if (lastMove != null) {
                    val (lr, lc) = lastMove
                    if (lr in 0 until size && lc in 0 until size) {
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
        }
    }
}
