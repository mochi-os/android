// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mochios.android.ui.components.dnd.DragState
import kotlin.math.roundToInt

/**
 * Kanban board for phones: pages one column at a time, then scales down during
 * a card drag so several columns are droppable, with edge auto-scroll. Scale
 * goes through `Modifier.layout`, not `graphicsLayer`, to keep dnd hit-testing
 * aligned.
 */
@Composable
fun PagedZoomableBoard(
    pageCount: Int,
    cardDragState: DragState,
    modifier: Modifier = Modifier,
    columnDragState: DragState? = null,
    state: LazyListState = rememberLazyListState(),
    pagedPeekPadding: Dp = 24.dp,
    pageSpacing: Dp = 8.dp,
    zoomScale: Float = 0.55f,
    edgeScrollThreshold: Dp = 56.dp,
    edgeScrollSpeedPerFrameDp: Dp = 14.dp,
    page: @Composable (index: Int) -> Unit,
) {
    val isDragging = cardDragState.draggingItemId != null ||
        (columnDragState?.draggingItemId != null)

    val scale by animateFloatAsState(
        targetValue = if (isDragging) zoomScale else 1f,
        animationSpec = tween(durationMillis = 240),
        label = "boardScale",
    )

    val density = LocalDensity.current
    var viewportLeft by remember { mutableStateOf(0f) }
    var viewportRight by remember { mutableStateOf(0f) }

    // Edge auto-scroll loop. Runs only while a drag is active. Reads the
    // pointer position from whichever DragState owns the current drag,
    // compares against the cached viewport bounds, and dispatches a
    // synchronous scroll delta per frame.
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        val thresholdPx = with(density) { edgeScrollThreshold.toPx() }
        val maxSpeedPx = with(density) { edgeScrollSpeedPerFrameDp.toPx() }
        while (true) {
            val pointerX = when {
                cardDragState.draggingItemId != null -> cardDragState.dragOffset.x
                columnDragState?.draggingItemId != null -> columnDragState.dragOffset.x
                else -> null
            }
            if (pointerX != null && viewportRight > viewportLeft) {
                val leftEdge = pointerX - viewportLeft
                val rightEdge = viewportRight - pointerX
                val delta = when {
                    leftEdge in 0f..thresholdPx ->
                        -((thresholdPx - leftEdge) / thresholdPx) * maxSpeedPx
                    rightEdge in 0f..thresholdPx ->
                        ((thresholdPx - rightEdge) / thresholdPx) * maxSpeedPx
                    else -> 0f
                }
                if (delta != 0f) state.scrollBy(delta)
            }
            delay(16) // ~60Hz
        }
    }

    // Snap-to-page in paged mode, free-scroll in zoom mode. Swapping the
    // fling behavior mid-drag would feel jarring; we only swap when the
    // scale animation has effectively settled to 1.0.
    val snapFling = rememberSnapFlingBehavior(lazyListState = state)
    val freeFling = ScrollableDefaults.flingBehavior()

    LazyRow(
        state = state,
        flingBehavior = if (scale > 0.999f) snapFling else freeFling,
        contentPadding = PaddingValues(horizontal = pagedPeekPadding),
        horizontalArrangement = Arrangement.spacedBy(pageSpacing),
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .onGloballyPositioned { coords ->
                val r = coords.boundsInWindow()
                viewportLeft = r.left
                viewportRight = r.right
            },
    ) {
        items(pageCount, key = { it }) { index ->
            // The `.layout {}` measures at full width but reports a scaled size
            // and places with a scaled layer, so `boundsInWindow` inside the
            // page still matches what the user sees and dnd hit-testing lands.
            Box(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val w = (placeable.width * scale).roundToInt().coerceAtLeast(0)
                        val h = (placeable.height * scale).roundToInt().coerceAtLeast(0)
                        layout(w, h) {
                            placeable.placeWithLayer(0, 0) {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                    }
            ) {
                page(index)
            }
        }
    }
}
