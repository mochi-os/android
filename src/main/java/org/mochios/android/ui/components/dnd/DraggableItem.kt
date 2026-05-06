package org.mochi.android.ui.components.dnd

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Mark a composable as draggable in a [DragState] scope. Long-press starts
 * the drag; subsequent pointer movement updates [DragState.dragOffset];
 * release fires the active drop target's onDrop. Cancellation (e.g. parent
 * scroll claiming the gesture) clears state cleanly.
 *
 * Pass [enabled] = false to keep the modifier in place but suppress drag
 * detection — useful for screens that want to toggle drag-to-reorder on and
 * off without rebuilding the modifier chain.
 */
fun Modifier.draggableItem(
    state: DragState,
    itemId: String,
    enabled: Boolean = true,
): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coords = it }
        .then(
            if (!enabled) Modifier
            else Modifier.pointerInput(state, itemId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localPos: Offset ->
                        val rootPos = coords?.localToRoot(localPos) ?: localPos
                        state.startDrag(itemId, rootPos)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val rootPos = coords?.localToRoot(change.position) ?: change.position
                        state.updateDrag(rootPos)
                    },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.cancelDrag() },
                )
            }
        )
}

/** Whether [itemId] is the active dragging source. */
fun DragState.isDragging(itemId: String): Boolean = draggingItemId == itemId
