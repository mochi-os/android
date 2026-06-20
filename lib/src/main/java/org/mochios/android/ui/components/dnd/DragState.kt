// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.dnd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Drag-and-drop state holder shared by [Modifier.draggableItem] and
 * [Modifier.dropTarget]. One instance is created per drag scope (e.g. a board
 * or a tree) via [rememberDragState]. Drop targets register themselves under a
 * stable item id; the draggable side updates the live cursor position and
 * picks the active target on each pointer event.
 */
class DragState(
    /**
     * Which drop modes a target accepts. Vertical lists usually allow
     * [DragEdge.Top] / [DragEdge.Bottom] / [DragEdge.On]; horizontal rails
     * remap to [DragEdge.Start] / [DragEdge.End]. [DragEdge.On] is computed by
     * pointer position; the other edges by which half of the bounds the
     * pointer is in. The fraction of the bounds claimed for the "on" zone (the
     * remainder splits between the two edges) defaults to a third.
     */
    val onZoneFraction: Float = 1f / 3f,
) {
    /** Id of the item currently being dragged, or null if no drag is active. */
    var draggingItemId: String? by mutableStateOf(null)
        private set

    /**
     * Last known pointer position in root coordinates while dragging. Tracked
     * eagerly so reordering visuals can respond to the live cursor.
     */
    var dragOffset: Offset by mutableStateOf(Offset.Zero)
        private set

    /**
     * Item id of the drop target the pointer is currently over, or null when
     * the pointer is outside every registered target.
     */
    var targetItemId: String? by mutableStateOf(null)
        private set

    /** Which edge of the active target the pointer is closest to. */
    var targetEdge: DragEdge by mutableStateOf(DragEdge.On)
        private set

    /** Registered drop targets, keyed by stable item id. */
    private val targets = mutableStateMapOf<String, DropTargetEntry>()

    internal fun register(itemId: String, entry: DropTargetEntry) {
        targets[itemId] = entry
    }

    internal fun updateBounds(itemId: String, bounds: Rect) {
        val entry = targets[itemId] ?: return
        if (entry.bounds == bounds) return
        targets[itemId] = entry.copy(bounds = bounds)
    }

    internal fun unregister(itemId: String) {
        targets.remove(itemId)
    }

    /** Begin a drag; the caller usually fires this from a long-press. */
    fun startDrag(itemId: String, position: Offset) {
        draggingItemId = itemId
        dragOffset = position
        recomputeTarget()
    }

    /** Pointer moved while dragging. Updates [dragOffset] and the active target. */
    fun updateDrag(position: Offset) {
        if (draggingItemId == null) return
        dragOffset = position
        recomputeTarget()
    }

    /**
     * Pointer released. Dispatches to the active target's onDrop and clears
     * drag state. Returns true if a drop fired, false if the drag was
     * cancelled or hit no valid target.
     */
    fun endDrag(): Boolean {
        val source = draggingItemId
        val target = targetItemId
        val edge = targetEdge
        val entry = target?.let { targets[it] }
        clear()
        if (source == null || target == null || entry == null) return false
        if (source == target) return false
        // Defer the user's accept check; if they reject, treat as no-op.
        if (!entry.accept(source, edge)) return false
        entry.onDrop(source, edge)
        return true
    }

    /** Cancel the drag without firing onDrop. */
    fun cancelDrag() {
        clear()
    }

    private fun clear() {
        draggingItemId = null
        targetItemId = null
        targetEdge = DragEdge.On
    }

    private fun recomputeTarget() {
        val pos = dragOffset
        val source = draggingItemId
        // First-match wins. Drop targets layered on top of one another (e.g. a
        // card sitting inside a column) should register the more specific one
        // last so it shadows the looser one — but in practice we look up edges
        // first and only fall back to "on"-the-column when no card matches.
        var bestId: String? = null
        var bestEdge: DragEdge = DragEdge.On
        for ((id, entry) in targets) {
            if (id == source) continue
            val rect = entry.bounds ?: continue
            if (!rect.contains(pos)) continue
            val edge = entry.computeEdge(pos, onZoneFraction)
            if (edge !in entry.acceptedEdges) continue
            // Prefer the deepest hit. Approximated by smallest area.
            val area = rect.width * rect.height
            val bestArea = bestId?.let { targets[it]?.bounds }?.let { it.width * it.height } ?: Float.MAX_VALUE
            if (bestId == null || area < bestArea) {
                bestId = id
                bestEdge = edge
            }
        }
        targetItemId = bestId
        targetEdge = bestEdge
    }
}

/** Position of the pointer relative to a drop target's bounds. */
enum class DragEdge {
    /** Above (vertical) — insert as previous sibling. */
    Top,
    /** Below (vertical) — insert as next sibling. */
    Bottom,
    /** Left of centre (horizontal) — insert before. */
    Start,
    /** Right of centre (horizontal) — insert after. */
    End,
    /** Inside the central zone — drop "on" the target (e.g. reparent). */
    On,
}

/** Internal record of a registered drop target. */
internal data class DropTargetEntry(
    val bounds: Rect?,
    /** Which orientation/mode this target accepts. */
    val orientation: DropOrientation,
    /** Which edges this target accepts. */
    val acceptedEdges: Set<DragEdge>,
    /** Optional source-validity check — return false to reject the drop. */
    val accept: (sourceId: String, edge: DragEdge) -> Boolean,
    /** Called when the drag is released over this target. */
    val onDrop: (sourceId: String, edge: DragEdge) -> Unit,
) {
    fun computeEdge(pointer: Offset, onZoneFraction: Float): DragEdge {
        val rect = bounds ?: return DragEdge.On
        return when (orientation) {
            DropOrientation.Vertical -> {
                val rel = (pointer.y - rect.top) / rect.height
                val on = onZoneFraction.coerceIn(0f, 1f)
                val onLow = (1f - on) / 2f
                val onHigh = onLow + on
                when {
                    rel < onLow -> DragEdge.Top
                    rel > onHigh -> DragEdge.Bottom
                    else -> DragEdge.On
                }
            }
            DropOrientation.Horizontal -> {
                val rel = (pointer.x - rect.left) / rect.width
                val on = onZoneFraction.coerceIn(0f, 1f)
                val onLow = (1f - on) / 2f
                val onHigh = onLow + on
                when {
                    rel < onLow -> DragEdge.Start
                    rel > onHigh -> DragEdge.End
                    else -> DragEdge.On
                }
            }
            DropOrientation.OnOnly -> DragEdge.On
        }
    }
}

/** Whether a drop target slots into a vertical list, horizontal rail, or only accepts "on" drops. */
enum class DropOrientation {
    Vertical,
    Horizontal,
    OnOnly,
}

@Composable
fun rememberDragState(onZoneFraction: Float = 1f / 3f): DragState =
    remember { DragState(onZoneFraction) }
