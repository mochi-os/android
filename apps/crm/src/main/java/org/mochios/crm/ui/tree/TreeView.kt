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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.rememberDragState
import org.mochios.crm.R
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.Person
import org.mochios.crm.model.CrmView
import org.mochios.crm.ui.crm.CrmViewModel

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
    val childMap = objects.groupBy { it.parent }
    val roots = objects.filter { it.parent.isBlank() || objects.none { other -> other.id == it.parent } }

    fun flattenTree(items: List<CrmObject>, depth: Int): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        for (item in items) {
            val children = childMap[item.id] ?: emptyList()
            val isExpanded = expandedState[item.id] ?: true
            result.add(
                TreeNode(
                    obj = item,
                    depth = depth,
                    hasChildren = children.isNotEmpty(),
                    isExpanded = isExpanded
                )
            )
            if (isExpanded && children.isNotEmpty()) {
                result.addAll(flattenTree(children, depth + 1))
            }
        }
        return result
    }

    val flatNodes = flattenTree(roots, 0)

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
                onDelete = { viewModel.deleteObject(node.obj.id) },
                onReparent = { newParentId -> viewModel.reparentObject(node.obj.id, newParentId) },
                onDragDrop = { sourceId, edge ->
                    handleTreeDrop(
                        sourceId = sourceId,
                        targetNode = node,
                        edge = edge,
                        viewModel = viewModel,
                    )
                }
            )
        }
    }
}

/**
 * Resolves a tree drop into reparent/move calls. Drops onto the source's own
 * descendants are rejected; sibling reorders pass scope_parent so the server
 * renumbers only that subtree.
 */
private fun handleTreeDrop(
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

    when (edge) {
        DragEdge.On -> {
            // Reparent under the target. The server appends to the end of
            // the new parent's children automatically.
            if (sourceObj.parent != targetNode.obj.id) {
                viewModel.reparentObject(sourceId, targetNode.obj.id)
            }
        }
        DragEdge.Top, DragEdge.Bottom -> {
            // Insert as sibling of the target under target's parent.
            val newParent = targetNode.obj.parent
            if (sourceObj.parent != newParent) {
                // Cross-parent: reparent only, the server appends. A follow-up
                // rank update would race the reparent and land against the
                // wrong parent.
                viewModel.reparentObject(sourceId, newParent)
                return
            }
            // Same-parent reorder. The server reads an empty scope_parent as
            // unset, so root-level siblings cannot be reordered by drag; the
            // Move dialog still works.
            if (newParent.isBlank()) return
            val siblings = allObjects
                .filter { it.parent == newParent && it.id != sourceId }
                .sortedBy { it.rank }
            val targetIndex = siblings.indexOfFirst { it.id == targetNode.obj.id }
            if (targetIndex < 0) return
            val rank = if (edge == DragEdge.Top) targetIndex + 1 else targetIndex + 2
            viewModel.moveObject(
                objectId = sourceId,
                field = "",
                value = null,
                rank = rank,
                scopeParent = newParent,
            )
        }
        else -> { /* Start/End not used in vertical tree */ }
    }
}
