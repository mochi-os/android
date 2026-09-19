// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.dnd

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

/**
 * This list of ids with [sourceId] lifted out and dropped against [targetId].
 *
 * The shape every drag-to-reorder list wants from a [DragEdge]: the server is
 * told the whole new order, so the caller maps its rows to ids, hands them
 * here, and sends the result.
 *
 * @param sourceId The id being dragged. Returns the list unchanged if absent.
 * @param targetId The id being dropped against. Returns the list with the
 *   source removed if absent.
 * @param edge Which side of the target to land on; [DragEdge.Bottom] inserts
 *   after, anything else before.
 * @return The ids in their new order.
 */
fun List<String>.reorderedAgainst(
    sourceId: String,
    targetId: String,
    edge: DragEdge,
): List<String> {
    val ids = toMutableList()
    if (!ids.remove(sourceId)) return ids
    val targetIndex = ids.indexOf(targetId)
    if (targetIndex < 0) return ids
    ids.add(if (edge == DragEdge.Bottom) targetIndex + 1 else targetIndex, sourceId)
    return ids
}

/**
 * Offers "move up" and "move down" as accessibility actions on a
 * drag-to-reorder row, so TalkBack and switch access users can reorder
 * without dragging. The first row gets no move-up action and the last row no
 * move-down action.
 *
 * @param ids Every row's id, in the order currently shown.
 * @param itemId The id of the row this modifier is on.
 * @param moveUpLabel Label of the move-up action.
 * @param moveDownLabel Label of the move-down action.
 * @param onReorder Receives the full new order of ids.
 * @return This modifier with the actions attached.
 */
fun Modifier.reorderActions(
    ids: List<String>,
    itemId: String,
    moveUpLabel: String,
    moveDownLabel: String,
    onReorder: (List<String>) -> Unit,
): Modifier = semantics {
    val index = ids.indexOf(itemId)
    customActions = buildList {
        if (index > 0) {
            add(
                CustomAccessibilityAction(moveUpLabel) {
                    onReorder(ids.reorderedAgainst(itemId, ids[index - 1], DragEdge.Top))
                    true
                }
            )
        }
        if (index >= 0 && index < ids.lastIndex) {
            add(
                CustomAccessibilityAction(moveDownLabel) {
                    onReorder(ids.reorderedAgainst(itemId, ids[index + 1], DragEdge.Bottom))
                    true
                }
            )
        }
    }
}
