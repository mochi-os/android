// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mochios.words.engine.getLetterValue

/**
 * Player's 7-tile rack. Tiles always render in 7 slots — empty slots stay
 * as faded dashed outlines so the row is stable in size as tiles flow on
 * and off (drag, place, recall).
 *
 * In normal mode each tile is a button:
 *  - Tap → toggle selection (the screen places the selected tile on the
 *    next board-cell tap, or opens the BlankTileDialog for blanks).
 *  - Long-press + drag → start a continuous drag from this slot. The screen
 *    renders a ghost tile under the finger and resolves the drop target on
 *    release (board cell / rack slot / cancel).
 *
 * In exchange mode each tile shows a tickable checkmark overlay instead;
 * tap toggles inclusion in the exchange-tiles set. Selection rings render
 * in destructive red so the user sees they're committing to discarding.
 */
@Composable
fun TileRack(
    tiles: List<Char>,
    selectedIndex: Int?,
    onSelectTile: (Int) -> Unit,
    disabled: Boolean,
    exchangeMode: Boolean,
    exchangeSelected: Set<Int>,
    onToggleExchange: (Int) -> Unit,
    draggingIndex: Int?,
    onRackDragStart: (index: Int, rootPos: Offset) -> Unit,
    onDrag: (rootPos: Offset) -> Unit,
    onDragEndAt: (rootPos: Offset) -> Unit,
    onDragCancel: () -> Unit,
    onBoundsChanged: (rackBounds: Rect, slotBounds: List<Rect>) -> Unit,
    dragPointer: Offset?,
    modifier: Modifier = Modifier,
) {
    val slotCoords = remember { mutableStateOf<List<LayoutCoordinates?>>(List(7) { null }) }
    var rackCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Compute which slot the live drag pointer is over, if any. Drawn as a
    // hover ring so the user sees where they will drop on the rack.
    val hoverSlot: Int? = remember(dragPointer, slotCoords.value) {
        val ptr = dragPointer ?: return@remember null
        slotCoords.value.indexOfFirst { lc ->
            val b = lc?.boundsInRoot() ?: return@indexOfFirst false
            b.contains(ptr)
        }.takeIf { it >= 0 }
    }

    fun reportBounds() {
        val rack = rackCoords?.boundsInRoot() ?: return
        val slots = slotCoords.value.map { it?.boundsInRoot() ?: Rect.Zero }
        onBoundsChanged(rack, slots)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 576.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .onGloballyPositioned {
                rackCoords = it
                reportBounds()
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 7) {
            val tile: Char? = tiles.getOrNull(i)
            val hasTile = tile != null
            val isSelected = selectedIndex == i && !exchangeMode
            val isExchangeSel = exchangeMode && exchangeSelected.contains(i)
            val isBeingDragged = draggingIndex == i
            val isHover = hoverSlot == i

            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 44.dp)
                    .onGloballyPositioned { lc ->
                        val next = slotCoords.value.toMutableList()
                        next[i] = lc
                        slotCoords.value = next
                        reportBounds()
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            !hasTile -> Color.Transparent
                            isBeingDragged -> TILE_BG_PRESSED
                            else -> TILE_BG
                        }
                    )
                    .border(
                        width = when {
                            isHover -> 2.5.dp
                            isSelected -> 2.5.dp
                            isExchangeSel -> 2.dp
                            else -> 1.5.dp
                        },
                        color = when {
                            isHover -> HOVER_RING
                            !hasTile -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            isSelected -> MaterialTheme.colorScheme.primary
                            isExchangeSel -> MaterialTheme.colorScheme.error
                            else -> TILE_BORDER
                        },
                        shape = RoundedCornerShape(6.dp),
                    )
                    .alpha(
                        when {
                            !hasTile -> 0.4f
                            disabled -> 0.5f
                            isBeingDragged -> 0.0f // ghost rendered at screen level
                            isExchangeSel -> 0.65f
                            else -> 1f
                        }
                    )
                    .then(
                        if (!hasTile || disabled || exchangeMode) Modifier
                        else Modifier.pointerInput(i, hasTile, disabled, exchangeMode) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { localPos ->
                                    val rootPos = slotCoords.value[i]
                                        ?.localToRoot(localPos) ?: localPos
                                    onRackDragStart(i, rootPos)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val rootPos = slotCoords.value[i]
                                        ?.localToRoot(change.position) ?: change.position
                                    onDrag(rootPos)
                                },
                                onDragEnd = {
                                    onDragEndAt(Offset.Unspecified)
                                },
                                onDragCancel = { onDragCancel() },
                            )
                        }
                    )
                    .pointerInput(hasTile, disabled, exchangeMode) {
                        if (!hasTile || disabled) return@pointerInput
                        detectTapGestures(
                            onTap = {
                                if (exchangeMode) onToggleExchange(i)
                                else onSelectTile(i)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (hasTile) {
                    val display = if (tile == '_') "" else tile.toString()
                    val value = getLetterValue(tile!!)
                    Text(
                        text = display,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color(0xFF1F1300),
                    )
                    if (value > 0) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = Color(0xFF555555),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 3.dp, bottom = 2.dp),
                        )
                    }
                    if (tile == '_') {
                        Text(
                            text = "?",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color(0xFF888888),
                        )
                    }
                    if (isExchangeSel) {
                        // Translucent check overlay so the user sees this
                        // tile is queued for exchange. Web uses an inset ring
                        // and an opacity drop; the icon makes it clearer on
                        // a small touchscreen.
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .align(Alignment.TopEnd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val TILE_BG = Color(0xFFEFCB94)
private val TILE_BG_PRESSED = Color(0xFFE0B775)
private val TILE_BORDER = Color(0xFFC79A60)
private val HOVER_RING = Color(0xFF2563EB)
