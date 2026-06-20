// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mochios.words.engine.BOARD_SIZE
import org.mochios.words.engine.Board
import org.mochios.words.engine.Placement
import org.mochios.words.engine.PremiumType
import org.mochios.words.engine.getDisplayLetter
import org.mochios.words.engine.getLetterValue
import org.mochios.words.engine.getPremium
import org.mochios.words.engine.isBlankTile
import org.mochios.words.ui.detail.DragSource

/**
 * 15x15 board composable. Renders the bonus squares as a colored background
 * pattern, draws already-played tiles in cream, and pending placements in
 * amber so the user can see at a glance which tiles they're committing.
 *
 * Tap behaviour:
 *  - Empty cell + selected rack tile → places the tile (calls `onCellClick`).
 *    Blank tiles open the BlankTileDialog via the ViewModel.
 *  - Cell with a pending placement → removes it (calls `onRemovePlacement`).
 *
 * Drag-and-drop:
 *  - Pending tiles can be picked up by long-press and dragged. The board
 *    reports its bounds in root coordinates via [onBoundsChanged] so the
 *    screen-level overlay can render the ghost tile under the finger and
 *    map the release position back to a target cell (or rack slot).
 *  - During a drag-in-progress originated from the rack (or another board
 *    cell), the screen passes the live pointer position via [dragPointer];
 *    we use it to highlight the cell under the finger. The actual drop
 *    fires at release time from the screen-level pipeline.
 */
@Composable
fun WordsBoard(
    board: Board,
    pendingPlacements: List<Placement>,
    selectedRackIndex: Int?,
    isMyTurn: Boolean,
    gameStatus: String,
    onCellClick: (row: Int, col: Int) -> Unit,
    onRemovePlacement: (row: Int, col: Int) -> Unit,
    dragSource: DragSource?,
    onBoardDragStart: (row: Int, col: Int, rootPos: Offset) -> Unit,
    onDrag: (rootPos: Offset) -> Unit,
    onDragEndAt: (rootPos: Offset) -> Unit,
    onDragCancel: () -> Unit,
    onBoundsChanged: (Rect, cellSize: Float) -> Unit,
    dragPointer: Offset?,
    modifier: Modifier = Modifier,
) {
    val isActive = gameStatus == "active"
    val canPlace = isActive && isMyTurn && selectedRackIndex != null
    val isDragging = dragSource != null

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.Center),
    ) {
        // Pick the smaller of width/height so the board never overflows its
        // container. When the parent doesn't constrain height (Constraints.Infinity)
        // we fall back to width. Inset a hair so the outer border doesn't get
        // clipped by the cell grid.
        val available = if (maxHeight.value > 0f && maxHeight.value.isFinite()) {
            minOf(maxWidth, maxHeight)
        } else {
            maxWidth
        }
        val side = available

        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

        Box(
            modifier = Modifier
                .size(side)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                .onGloballyPositioned { lc ->
                    coords = lc
                    val bounds = lc.boundsInRoot()
                    val cell = bounds.width / BOARD_SIZE
                    onBoundsChanged(bounds, cell)
                },
        ) {
            BoardGrid(
                board = board,
                pendingPlacements = pendingPlacements,
                isActive = isActive,
                isMyTurn = isMyTurn,
                canPlace = canPlace,
                isDragging = isDragging,
                dragSource = dragSource,
                dragPointer = dragPointer,
                boardCoords = coords,
                onCellClick = onCellClick,
                onRemovePlacement = onRemovePlacement,
                onBoardDragStart = onBoardDragStart,
                onDrag = onDrag,
                onDragEndAt = onDragEndAt,
                onDragCancel = onDragCancel,
            )
        }
    }
}

@Composable
private fun BoardGrid(
    board: Board,
    pendingPlacements: List<Placement>,
    isActive: Boolean,
    isMyTurn: Boolean,
    canPlace: Boolean,
    isDragging: Boolean,
    dragSource: DragSource?,
    dragPointer: Offset?,
    boardCoords: LayoutCoordinates?,
    onCellClick: (row: Int, col: Int) -> Unit,
    onRemovePlacement: (row: Int, col: Int) -> Unit,
    onBoardDragStart: (row: Int, col: Int, rootPos: Offset) -> Unit,
    onDrag: (rootPos: Offset) -> Unit,
    onDragEndAt: (rootPos: Offset) -> Unit,
    onDragCancel: () -> Unit,
) {
    // Index pending placements for O(1) lookup.
    val pendingByCell = remember(pendingPlacements) {
        pendingPlacements.associateBy { it.row * BOARD_SIZE + it.col }
    }

    // Compute which cell the live drag pointer is over, if any. Used to draw
    // a hover ring on the current target cell so the user sees where they
    // will drop.
    val hoverCell: Pair<Int, Int>? = remember(dragPointer, boardCoords) {
        val ptr = dragPointer ?: return@remember null
        val bounds = boardCoords?.boundsInRoot() ?: return@remember null
        if (!bounds.contains(ptr)) return@remember null
        val cellSize = bounds.width / BOARD_SIZE
        if (cellSize <= 0f) return@remember null
        val col = ((ptr.x - bounds.left) / cellSize).toInt().coerceIn(0, BOARD_SIZE - 1)
        val row = ((ptr.y - bounds.top) / cellSize).toInt().coerceIn(0, BOARD_SIZE - 1)
        row to col
    }

    // Layout the 15x15 grid by dividing the box into equal-sized cells. The
    // parent always passes us a square Box (BoxWithConstraints picks the
    // smaller of maxWidth/maxHeight), so each cell gets exactly side/15.
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            for (row in 0 until BOARD_SIZE) {
                for (col in 0 until BOARD_SIZE) {
                    val pending = pendingByCell[row * BOARD_SIZE + col]
                    val isHover = hoverCell?.let { it.first == row && it.second == col } == true
                    BoardCell(
                        row = row,
                        col = col,
                        cellValue = board[row, col],
                        pending = pending,
                        isActive = isActive,
                        isMyTurn = isMyTurn,
                        canPlace = canPlace,
                        isDragging = isDragging,
                        isHover = isHover,
                        dragSource = dragSource,
                        boardCoords = boardCoords,
                        onCellClick = onCellClick,
                        onRemovePlacement = onRemovePlacement,
                        onBoardDragStart = onBoardDragStart,
                        onDrag = onDrag,
                        onDragEndAt = onDragEndAt,
                        onDragCancel = onDragCancel,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val side = minOf(constraints.maxWidth, constraints.maxHeight)
        val cell = (side / BOARD_SIZE).coerceAtLeast(1)
        val total = cell * BOARD_SIZE
        val cellConstraints = androidx.compose.ui.unit.Constraints.fixed(cell, cell)
        val placeables = measurables.map { it.measure(cellConstraints) }
        layout(total, total) {
            placeables.forEachIndexed { index, placeable ->
                val row = index / BOARD_SIZE
                val col = index % BOARD_SIZE
                placeable.place(col * cell, row * cell)
            }
        }
    }
}

@Composable
private fun BoardCell(
    row: Int,
    col: Int,
    cellValue: Char,
    pending: Placement?,
    isActive: Boolean,
    isMyTurn: Boolean,
    canPlace: Boolean,
    isDragging: Boolean,
    isHover: Boolean,
    dragSource: DragSource?,
    boardCoords: LayoutCoordinates?,
    onCellClick: (row: Int, col: Int) -> Unit,
    onRemovePlacement: (row: Int, col: Int) -> Unit,
    onBoardDragStart: (row: Int, col: Int, rootPos: Offset) -> Unit,
    onDrag: (rootPos: Offset) -> Unit,
    onDragEndAt: (rootPos: Offset) -> Unit,
    onDragCancel: () -> Unit,
) {
    val premium = getPremium(row, col)
    val isOccupied = cellValue != '.'
    val isPending = pending != null
    val isEmpty = !isOccupied && !isPending

    val canClickToPlace = canPlace && isEmpty
    val canClickToRemove = isPending && isActive && isMyTurn
    val canDragThis = isActive && isMyTurn && isPending
    val isBeingDragged = (dragSource is DragSource.BoardCell) &&
        dragSource.row == row && dragSource.col == col
    // Only highlight as a drop target if the cell can actually accept the
    // drop: empty (or the source cell of a board-to-board drag, which lifts
    // visually to "empty" while the ghost floats).
    val canAcceptDrop = isEmpty || isBeingDragged
    val showHover = isHover && canAcceptDrop && isDragging

    val premiumBg: Color = when {
        isOccupied || (isPending && !isBeingDragged) -> Color.Transparent
        else -> premiumColor(premium)
    }

    var cellCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { cellCoords = it }
            .background(
                when {
                    isOccupied -> TILE_BG
                    isPending && !isBeingDragged -> PENDING_BG
                    else -> premiumBg.takeIf { it != Color.Transparent } ?: BOARD_BG
                },
            )
            .border(
                width = when {
                    showHover -> 2.dp
                    isPending && !isBeingDragged -> 2.dp
                    else -> 0.5.dp
                },
                color = when {
                    showHover -> HOVER_RING
                    isPending && !isBeingDragged -> PENDING_RING
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
            )
            .then(
                if (canDragThis) {
                    Modifier.pointerInput(row, col, canDragThis) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localPos ->
                                val rootPos = cellCoords?.localToRoot(localPos)
                                    ?: boardCoords?.localToRoot(localPos)
                                    ?: localPos
                                onBoardDragStart(row, col, rootPos)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val rootPos = cellCoords?.localToRoot(change.position)
                                    ?: change.position
                                onDrag(rootPos)
                            },
                            onDragEnd = {
                                // Last known root position lives in screen-
                                // level state via onDrag; we emit a marker to
                                // signal "release happened" and the screen
                                // resolves the drop target.
                                onDragEndAt(Offset.Unspecified)
                            },
                            onDragCancel = { onDragCancel() },
                        )
                    }
                } else Modifier
            )
            .pointerInput(canClickToPlace, canClickToRemove) {
                detectTapGestures(
                    onTap = {
                        if (canClickToRemove) {
                            onRemovePlacement(row, col)
                        } else if (canClickToPlace) {
                            onCellClick(row, col)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isOccupied -> TileFace(
                letter = getDisplayLetter(cellValue),
                value = getLetterValue(cellValue),
                blank = isBlankTile(cellValue),
                pending = false,
            )
            isPending && !isBeingDragged -> TileFace(
                letter = pending!!.letter.uppercaseChar().toString(),
                value = if (pending.rackTile == '_') 0 else getLetterValue(pending.letter),
                blank = pending.rackTile == '_',
                pending = true,
            )
            isPending && isBeingDragged -> Unit // ghost rendered at screen level
            premium == PremiumType.ST -> Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFB0578F),
                modifier = Modifier.fillMaxSize(0.5f),
            )
            premium != PremiumType.NONE -> Text(
                text = premiumLabel(premium),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 7.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = premiumTextColor(premium),
            )
        }
    }
}

@Composable
private fun TileFace(letter: String, value: Int, blank: Boolean, pending: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (blank) Color(0xFF666666)
            else if (pending) Color(0xFF3B2A06)
            else Color(0xFF1F1300),
        )
        if (value > 0) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp),
                color = Color(0xFF555555),
                modifier = Modifier
                    .align(Alignment.BottomEnd),
            )
        }
    }
}

private fun premiumColor(t: PremiumType): Color = when (t) {
    PremiumType.TW -> Color(0xFFFF6B6B)
    PremiumType.DW -> Color(0xFFFFB3B3)
    PremiumType.TL -> Color(0xFF3F7BB6)
    PremiumType.DL -> Color(0xFFA5C8E0)
    PremiumType.ST -> Color(0xFFFFB3B3)
    PremiumType.NONE -> Color.Transparent
}

private fun premiumLabel(t: PremiumType): String = when (t) {
    PremiumType.TW -> "TW"
    PremiumType.DW -> "DW"
    PremiumType.TL -> "TL"
    PremiumType.DL -> "DL"
    PremiumType.ST -> ""
    PremiumType.NONE -> ""
}

private fun premiumTextColor(t: PremiumType): Color = when (t) {
    PremiumType.TW -> Color(0xFFFFFFFF)
    PremiumType.DW -> Color(0xFFAA4A4A)
    PremiumType.TL -> Color(0xFFFFFFFF)
    PremiumType.DL -> Color(0xFF254A6B)
    else -> Color(0xFF888888)
}

private val BOARD_BG = Color(0xFFF7F2E7)
private val TILE_BG = Color(0xFFEFCB94)
private val PENDING_BG = Color(0xFFFBBF24)
private val PENDING_RING = Color(0xFFD97706)
private val HOVER_RING = Color(0xFF2563EB)
