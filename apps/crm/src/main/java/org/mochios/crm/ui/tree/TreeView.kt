// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.tree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.model.canWrite
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.rememberDragState
import org.mochios.crm.R
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.Person
import org.mochios.crm.model.CrmView
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.crm.ui.crm.Placement
import org.mochios.crm.util.reparentAllowed

data class TreeNode(
    val obj: CrmObject,
    val depth: Int,
    val hasChildren: Boolean,
    val isExpanded: Boolean
)

@Composable
fun TreeView(
    objects: List<CrmObject>,
    view: CrmView?,
    viewModel: CrmViewModel,
    // Threaded to TreeRow, which must not snapshot the flow itself. allObjects
    // is the UNFILTERED list, distinct from `objects` above: the reparent
    // dialog offers parents the current view filters out.
    crmDetails: CrmDetails?,
    people: List<Person>,
    allObjects: List<CrmObject>,
    onObjectClick: (String) -> Unit
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val dragState = rememberDragState()

    if (objects.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.crm_tree_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Build tree structure from flat list
    val flatNodes by remember(objects) {
        val childMap = objects.groupBy { obj -> obj.parent }
        val ids = objects.mapTo(HashSet()) { obj -> obj.id }
        val roots = objects.filter { obj -> obj.parent.isBlank() || obj.parent !in ids }
        derivedStateOf { flattenTree(roots, 0, childMap, expandedState) }
    }

    // Moving, reordering and deleting rows is writing, as on the web.
    val writable = canWrite(crmDetails?.crm?.access)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(flatNodes, key = { it.obj.id }) { node ->
            TreeRow(
                node = node,
                viewModel = viewModel,
                crmDetails = crmDetails,
                people = people,
                allObjects = allObjects,
                dragState = dragState,
                onToggleExpand = {
                    expandedState[node.obj.id] = !(expandedState[node.obj.id] ?: true)
                },
                onClick = { onObjectClick(node.obj.id) },
                onDelete = if (writable) {
                    { viewModel.deleteObject(node.obj.id) }
                } else {
                    null
                },
                onReparent = if (writable) {
                    { newParentId -> viewModel.reparentObject(node.obj.id, newParentId) }
                } else {
                    null
                },
                onDragDrop = if (writable) {
                    { sourceId, edge ->
                        handleTreeDrop(
                            sourceId = sourceId,
                            targetNode = node,
                            edge = edge,
                            viewModel = viewModel,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Resolves a tree drop into reparent/move calls. Drops onto the source's own
 * descendants are rejected. A drop on a row's middle puts the source under
 * it; one on its top or bottom edge puts it directly above or below that
 * row, among the row's siblings, naming the row it went beside and, when the
 * parent changes, the new parent in the same move. The edges place only
 * under a manual sort, the one that draws the order they place by.
 */
internal fun handleTreeDrop(
    sourceId: String,
    targetNode: TreeNode,
    edge: DragEdge,
    viewModel: CrmViewModel,
) {
    if (sourceId == targetNode.obj.id) return
    val descendants = viewModel.collectDescendants(sourceId)
    if (targetNode.obj.id in descendants) return

    val allObjects = viewModel.uiState.value.objects
    val sourceObj = allObjects.find { it.id == sourceId } ?: return
    // A drop the hierarchy forbids does nothing, rather than being sent for
    // the server to refuse.
    val hierarchy = viewModel.uiState.value.crmDetails?.hierarchy.orEmpty()

    when (edge) {
        DragEdge.On -> {
            // Reparent under the target. The server appends to the end of
            // the new parent's children automatically.
            if (sourceObj.parent != targetNode.obj.id &&
                reparentAllowed(sourceObj, targetNode.obj.id, allObjects, hierarchy)
            ) {
                viewModel.reparentObject(sourceId, targetNode.obj.id)
            }
        }
        DragEdge.Top, DragEdge.Bottom -> {
            // Directly above or below the target, among its parent's children:
            // the new parent, when it changes, goes in the same move. Another
            // sort draws no place to go, so an edge does nothing there.
            if (viewModel.placement() == Placement.None) return
            val newParent = targetNode.obj.parent
            val reparent = sourceObj.parent != newParent
            if (reparent && !reparentAllowed(sourceObj, newParent, allObjects, hierarchy)) return
            viewModel.moveBeside(
                objectId = sourceId,
                target = targetNode.obj.id,
                below = edge == DragEdge.Bottom,
                field = "",
                parent = newParent.takeIf { reparent },
            )
        }
        else -> { /* Start/End not used in vertical tree */ }
    }
}

/**
 * The tree's rows in display order, each expanded node followed by its children.
 *
 * @param items the nodes at this level.
 * @param depth how deep [items] sit.
 * @param childMap each object's children, by parent id.
 * @param expanded which nodes are expanded; a node missing from it is.
 * @return the rows to show.
 */
private fun flattenTree(
    items: List<CrmObject>,
    depth: Int,
    childMap: Map<String, List<CrmObject>>,
    expanded: Map<String, Boolean>,
): List<TreeNode> {
    val result = mutableListOf<TreeNode>()
    for (item in items) {
        val children = childMap[item.id] ?: emptyList()
        val isExpanded = expanded[item.id] ?: true
        result.add(
            TreeNode(
                obj = item,
                depth = depth,
                hasChildren = children.isNotEmpty(),
                isExpanded = isExpanded
            )
        )
        if (isExpanded && children.isNotEmpty()) {
            result.addAll(flattenTree(children, depth + 1, childMap, expanded))
        }
    }
    return result
}
