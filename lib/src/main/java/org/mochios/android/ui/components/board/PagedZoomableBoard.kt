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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.mochios.android.ui.components.dnd.DragState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kanban board for phones: pages one column at a time, then scales down during
 * a drag with the column the drag began in centred, so part of each neighbour
 * shows, and scrolls while the pointer is near an edge. The dragged item is
 * drawn under the pointer from its ghost, and when the drag ends the board
 * settles on the column the pointer was over. Scale goes through
 * `Modifier.layout`, not `graphicsLayer`, to keep dnd hit-testing aligned.
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
    val isDragging = dragOwner(cardDragState, columnDragState) != null

    val zoom by animateFloatAsState(
        targetValue = if (isDragging) zoomScale else 1f,
        animationSpec = tween(durationMillis = 240),
        label = "boardScale",
    )

    val density = LocalDensity.current
    // The board's bounds in root coordinates, the space drag positions use.
    var viewport by remember { mutableStateOf(Rect.Zero) }

    // While a drag is active: scroll when the pointer is near an edge, and
    // re-pick the target under it each frame, since the targets move beneath a
    // pointer that holds still. When it ends: settle on the page under the
    // pointer, so the board is not left between two.
    LaunchedEffect(cardDragState, columnDragState) {
        var previous: DragState? = null
        snapshotFlow { dragOwner(cardDragState, columnDragState) }.collectLatest { owner ->
            if (owner == null) {
                val ended = previous ?: return@collectLatest
                previous = null
                val index = pageAt(state, ended.dragOffset.x - viewport.left) ?: return@collectLatest
                state.animateScrollToItem(index)
                return@collectLatest
            }
            previous = owner
            val thresholdPx = with(density) { edgeScrollThreshold.toPx() }
            val maxSpeedPx = with(density) { edgeScrollSpeedPerFrameDp.toPx() }
            while (true) {
                val pointerX = owner.dragOffset.x
                if (viewport.right > viewport.left) {
                    val leftEdge = pointerX - viewport.left
                    val rightEdge = viewport.right - pointerX
                    val delta = when {
                        leftEdge in 0f..thresholdPx ->
                            -((thresholdPx - leftEdge) / thresholdPx) * maxSpeedPx
                        rightEdge in 0f..thresholdPx ->
                            ((thresholdPx - rightEdge) / thresholdPx) * maxSpeedPx
                        else -> 0f
                    }
                    if (delta != 0f) state.scrollBy(delta)
                }
                owner.refresh()
                delay(16) // ~60Hz
            }
        }
    }

    // Snap-to-page in paged mode, free-scroll in zoom mode. Swapping the
    // fling behavior mid-drag would feel jarring; we only swap when the
    // scale animation has effectively settled to 1.0.
    val snapFling = rememberSnapFlingBehavior(lazyListState = state)
    val freeFling = ScrollableDefaults.flingBehavior()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .onGloballyPositioned { coords -> viewport = coords.boundsInRoot() },
    ) {
        // Pages keep one width at every scale, so their content does not
        // reflow as the board zooms. The first visible page sits at the start
        // padding, so padding that centres a scaled page centres that page.
        val pageWidth = maxWidth - pagedPeekPadding * 2
        val side = centredPadding(maxWidth, pageWidth, zoom)

        LazyRow(
            state = state,
            flingBehavior = if (zoom > 0.999f) snapFling else freeFling,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(pageSpacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pageCount, key = { it }) { index ->
                // The `.layout {}` measures at full width but reports a scaled size
                // and places with a scaled layer, so `boundsInWindow` inside the
                // page still matches what the user sees and dnd hit-testing lands.
                // The width is fixed inside it: a size modifier outside would
                // hold the page's slot at full width and centre the scaled page
                // in it, so zooming out never brought a neighbour into view.
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val width = pageWidth.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width)
                            )
                            val w = (placeable.width * zoom).roundToInt().coerceAtLeast(0)
                            val h = (placeable.height * zoom).roundToInt().coerceAtLeast(0)
                            layout(w, h) {
                                placeable.placeWithLayer(0, 0) {
                                    scaleX = zoom
                                    scaleY = zoom
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                            }
                        }
                ) {
                    page(index)
                }
            }
        }

        // The dragged item, drawn at the board's scale with the point it was
        // lifted by under the pointer.
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val owner = dragOwner(cardDragState, columnDragState) ?: return@drawBehind
                    val ghost = owner.ghost ?: return@drawBehind
                    val pointer = owner.dragOffset - viewport.topLeft
                    translate(pointer.x - ghost.grab.x * zoom, pointer.y - ghost.grab.y * zoom) {
                        scale(zoom, zoom, pivot = Offset.Zero) {
                            drawLayer(ghost.layer)
                        }
                    }
                }
        )
    }
}

/** Whichever of the board's drag states is dragging, or null. */
private fun dragOwner(cardDragState: DragState, columnDragState: DragState?): DragState? = when {
    cardDragState.draggingItemId != null -> cardDragState
    columnDragState?.draggingItemId != null -> columnDragState
    else -> null
}

/** Side padding that centres a page of [page] width drawn at [scale] in a [viewport]. */
internal fun centredPadding(viewport: Dp, page: Dp, scale: Float): Dp = (viewport - page * scale) / 2

/**
 * The visible page nearest [x], in pixels from the board's start edge, or null
 * when no page is laid out.
 */
private fun pageAt(state: LazyListState, x: Float): Int? {
    val info = state.layoutInfo
    return info.visibleItemsInfo.minByOrNull { item ->
        val start = item.offset - info.viewportStartOffset
        abs(start + item.size / 2f - x)
    }?.index
}
