// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components.dnd

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
