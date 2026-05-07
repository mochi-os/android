package org.mochios.android.ui.components.dnd

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Register a composable as a drop target in a [DragState] scope. The target's
 * bounds in root coordinates are reported to the state on every layout pass;
 * the state matches them against the live drag pointer to pick the active
 * target and edge.
 *
 * @param itemId stable id used to identify this target. Must not collide with
 *   any draggable item id — the state automatically excludes a target whose
 *   id matches the source being dragged so a card cannot drop on itself.
 * @param orientation [DropOrientation.Vertical] for column-style lists,
 *   [DropOrientation.Horizontal] for row-style rails, [DropOrientation.OnOnly]
 *   for whole-area "on" drops (e.g. a kanban column accepting any card).
 * @param acceptedEdges which [DragEdge] values this target accepts; pointer
 *   positions resolving to other edges are ignored. Defaults to all edges
 *   matching [orientation].
 * @param accept optional predicate run on drop; return false to cancel the
 *   drop (e.g. cycle prevention in tree reparent).
 * @param onDrop fired when the user releases over this target with an
 *   accepted edge.
 */
fun Modifier.dropTarget(
    state: DragState,
    itemId: String,
    orientation: DropOrientation = DropOrientation.Vertical,
    acceptedEdges: Set<DragEdge> = defaultEdgesFor(orientation),
    accept: (sourceId: String, edge: DragEdge) -> Boolean = { _, _ -> true },
    onDrop: (sourceId: String, edge: DragEdge) -> Unit,
): Modifier = composed {
    // Hold the latest accept/onDrop in refs so the registered DropTargetEntry
    // always sees the current closures even though we don't re-register on
    // every recomposition (the lambdas usually capture local state and would
    // change identity each frame).
    val acceptRef = remember { mutableStateOf(accept) }.also { it.value = accept }
    val onDropRef = remember { mutableStateOf(onDrop) }.also { it.value = onDrop }
    DisposableEffect(state, itemId, orientation, acceptedEdges) {
        state.register(
            itemId,
            DropTargetEntry(
                bounds = null,
                orientation = orientation,
                acceptedEdges = acceptedEdges,
                accept = { src, edge -> acceptRef.value(src, edge) },
                onDrop = { src, edge -> onDropRef.value(src, edge) },
            )
        )
        onDispose { state.unregister(itemId) }
    }
    this.onGloballyPositioned { coords ->
        state.updateBounds(itemId, coords.boundsInRoot())
    }
}

private fun defaultEdgesFor(orientation: DropOrientation): Set<DragEdge> = when (orientation) {
    DropOrientation.Vertical -> setOf(DragEdge.Top, DragEdge.Bottom, DragEdge.On)
    DropOrientation.Horizontal -> setOf(DragEdge.Start, DragEdge.End, DragEdge.On)
    DropOrientation.OnOnly -> setOf(DragEdge.On)
}

/** Whether [itemId] is the active drop target. */
fun DragState.isTarget(itemId: String): Boolean = targetItemId == itemId
