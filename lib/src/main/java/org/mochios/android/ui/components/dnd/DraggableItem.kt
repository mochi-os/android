// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.dnd

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Mark a composable as draggable in a [DragState] scope; long-press starts the
 * drag. [enabled] = false keeps the modifier in the chain but suppresses
 * detection, so drag-to-reorder toggles without rebuilding it.
 *
 * Inside a lazy list the item is pinned for the length of the drag. A lazy
 * list disposes an item scrolled out of view, which cancels the gesture it
 * carries, so a drag that auto-scrolled its own column away used to end with
 * no drop. The item's drawing is kept as the drag's [DragGhost].
 */
fun Modifier.draggableItem(
    state: DragState,
    itemId: String,
    enabled: Boolean = true,
): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val container by rememberUpdatedState(LocalPinnableContainer.current)
    val pin = remember { Pin() }
    DisposableEffect(Unit) { onDispose { pin.release() } }
    val layer = rememberGraphicsLayer()
    this
        .onGloballyPositioned { coords = it }
        // Recorded on every draw but the drag's own, so the ghost shows the
        // item as it was when lifted, not as it looks standing in for it.
        .drawWithContent {
            if (state.draggingItemId == itemId) {
                drawContent()
            } else {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
        }
        .then(
            if (!enabled) Modifier
            else Modifier.pointerInput(state, itemId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localPos: Offset ->
                        pin.hold(container)
                        val rootPos = coords?.localToRoot(localPos) ?: localPos
                        state.startDrag(itemId, rootPos, DragGhost(layer, localPos))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val rootPos = coords?.localToRoot(change.position) ?: change.position
                        state.updateDrag(rootPos)
                    },
                    onDragEnd = {
                        pin.release()
                        state.endDrag()
                    },
                    onDragCancel = {
                        pin.release()
                        state.cancelDrag()
                    },
                )
            }
        )
}

/** The pin a dragged item holds on its lazy list, if it is in one. */
private class Pin {
    private var handle: PinnableContainer.PinnedHandle? = null

    fun hold(container: PinnableContainer?) {
        release()
        handle = container?.pin()
    }

    fun release() {
        handle?.release()
        handle = null
    }
}

/** Whether [itemId] is the active dragging source. */
fun DragState.isDragging(itemId: String): Boolean = draggingItemId == itemId
