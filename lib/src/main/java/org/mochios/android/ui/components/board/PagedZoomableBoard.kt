// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.flow.first
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
 *
 * A page [collapsed] says is a narrow strip of [collapsedWidth]. Beside a
 * column only a sliver of it would show, so each run of strips is a stop of
 * its own: the board comes to rest with the run centred and part of each
 * neighbouring column in view.
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
    collapsed: (index: Int) -> Boolean = { false },
    collapsedWidth: Dp = 40.dp,
    page: @Composable (index: Int) -> Unit,
) {
    val isDragging = dragOwner(cardDragState, columnDragState) != null
    val isCollapsed by rememberUpdatedState(collapsed)

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
                if (!isCollapsed(index)) {
                    state.animateScrollToItem(index)
                    return@collectLatest
                }
                // A strip: centre its run, measured once the board is back at
                // full size, since the zoom changes every page's width.
                var first = index
                while (first > 0 && isCollapsed(first - 1)) first--
                snapshotFlow { zoom }.first { scale -> scale > 0.999f }
                if (state.layoutInfo.visibleItemsInfo.none { item -> item.index == first }) {
                    state.scrollToItem(first)
                }
                val info = state.layoutInfo
                val stop = boardStops(info.visibleItemsInfo.map { item ->
                    BoardItem(item.index, item.offset, item.size)
                }, isCollapsed).firstOrNull { candidate -> candidate.first == first }
                    ?: return@collectLatest
                state.animateScrollBy(stop.centre - viewportCentre(info))
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
    val minimumFling = with(density) { MinimumFlingVelocity.toPx() }
    val snapFling = rememberSnapFlingBehavior(
        remember(state) {
            StopSnapLayoutInfoProvider(
                state,
                SnapLayoutInfoProvider(state, SnapPosition.Center),
                { index -> isCollapsed(index) },
                { minimumFling },
            )
        }
    )
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
                            val width = (if (isCollapsed(index)) collapsedWidth else pageWidth).roundToPx()
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

/** Below this a fling settles on the nearest stop rather than the next one; Compose's own snapping threshold. */
private val MinimumFlingVelocity = 400.dp

/** A laid-out page, in the list's offsets: [offset] from the start of its content, [size] across. */
internal data class BoardItem(val index: Int, val offset: Int, val size: Int)

/** Where the board may rest: one column, or a run of strips from [first] to [last], centred on [centre]. */
internal data class BoardStop(val first: Int, val last: Int, val centre: Float)

/** The stops among [items], in order: each column alone, and each run of collapsed pages together. */
internal fun boardStops(items: List<BoardItem>, collapsed: (Int) -> Boolean): List<BoardStop> {
    val stops = mutableListOf<BoardStop>()
    var run: MutableList<BoardItem>? = null
    fun close() {
        val strips = run ?: return
        val start = strips.first()
        val end = strips.last()
        stops.add(BoardStop(start.index, end.index, (start.offset + end.offset + end.size) / 2f))
        run = null
    }
    for (item in items.sortedBy { it.index }) {
        if (!collapsed(item.index)) {
            close()
            stops.add(BoardStop(item.index, item.index, item.offset + item.size / 2f))
            continue
        }
        val strips = run
        if (strips != null && strips.last().index == item.index - 1) strips.add(item)
        else {
            close()
            run = mutableListOf(item)
        }
    }
    close()
    return stops
}

/** The middle of the list's viewport, in the offsets [BoardItem] uses. */
internal fun viewportCentre(info: LazyListLayoutInfo): Float =
    (info.viewportStartOffset + info.viewportEndOffset) / 2f

/**
 * How far to scroll to rest on a stop, given each stop's distance from the
 * centre: the nearest for a slow release, else the next one in the fling's
 * direction.
 */
internal fun snapDistance(distances: List<Float>, velocity: Float, minimum: Float): Float {
    val behind = distances.filter { it <= 0f }.maxOrNull()
    val ahead = distances.filter { it >= 0f }.minOrNull()
    return when {
        behind == null -> ahead ?: 0f
        ahead == null -> behind
        abs(velocity) < minimum -> if (abs(behind) <= abs(ahead)) behind else ahead
        velocity > 0f -> ahead
        else -> behind
    }
}

/**
 * Snapping that rests on [boardStops] rather than on every page, so a run of
 * strips is one stop, centred, and never leaves a column off-centre. The fling's
 * approach, the long travel before the final snap, is Compose's own.
 */
private class StopSnapLayoutInfoProvider(
    private val state: LazyListState,
    private val base: SnapLayoutInfoProvider,
    private val collapsed: (Int) -> Boolean,
    private val minimum: () -> Float,
) : SnapLayoutInfoProvider {
    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float =
        base.calculateApproachOffset(velocity, decayOffset)

    override fun calculateSnapOffset(velocity: Float): Float {
        val info = state.layoutInfo
        val centre = viewportCentre(info)
        val stops = boardStops(info.visibleItemsInfo.map { BoardItem(it.index, it.offset, it.size) }, collapsed)
        return snapDistance(stops.map { it.centre - centre }, velocity, minimum())
    }
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
