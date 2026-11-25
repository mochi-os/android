// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.tree

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
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.rememberDragState
import org.mochios.projects.R
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.model.ProjectView
import org.mochios.projects.ui.project.Placement
import org.mochios.projects.ui.project.ProjectViewModel
import org.mochios.projects.ui.project.drawnBeside
import org.mochios.projects.util.reparentAllowed

data class TreeNode(
    val obj: ProjectObject,
    val depth: Int,
    val hasChildren: Boolean,
    val isExpanded: Boolean
)

@Composable
fun TreeView(
    objects: List<ProjectObject>,
    view: ProjectView?,
    viewModel: ProjectViewModel,
    onObjectClick: (String) -> Unit
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val dragState = rememberDragState()

    if (objects.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.projects_tree_empty),
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

    // Moving, dragging and deleting rows are writes; a reader only opens them.
    val writable = viewModel.canWrite()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(flatNodes, key = { it.obj.id }) { node ->
            TreeRow(
                node = node,
                viewModel = viewModel,
                dragState = if (writable) dragState else null,
                onToggleExpand = {
                    expandedState[node.obj.id] = !(expandedState[node.obj.id] ?: true)
                },
                onClick = { onObjectClick(node.obj.id) },
                onDelete = if (writable) ({ viewModel.deleteObject(node.obj.id) }) else null,
                onReparent = if (writable) ({ newParentId -> viewModel.reparentObject(node.obj.id, newParentId) }) else null,
                onDragDrop = if (writable) ({ sourceId, edge ->
                    handleTreeDrop(
                        sourceId = sourceId,
                        targetNode = node,
                        edge = edge,
                        viewModel = viewModel,
                        drawn = objects,
                    )
                }) else null
            )
        }
    }
}

/**
 * Turns a tree drop into a move or reparent. A drop on a row's middle puts
 * the source under it; one on its top or bottom edge puts it directly above
 * or below that row, among the rows drawn at its level, naming the row it
 * went beside and the row drawn on the drop's far side. A row whose parent
 * the tree does not draw is drawn at the top level, beside rows of other
 * parents: a drop beside it reorders the rows drawn there and changes no
 * parent, and one that would take a row out of a parent drawn to there is
 * refused, since its place would be among a parent the user cannot see. A
 * drop at another level drawn under a parent, or at the top level beside a
 * row that is truly there, moves the source to that parent in the same move.
 * The move names the parent it reorders within only when every row drawn at
 * that level is that parent's child, so the server takes the neighbours
 * there; a level that mixes parents names none. The edges place only under a
 * manual sort, the one that draws the order they place by. [drawn] is the
 * tree's objects in the order it draws them.
 */
internal fun handleTreeDrop(
    sourceId: String,
    targetNode: TreeNode,
    edge: DragEdge,
    viewModel: ProjectViewModel,
    drawn: List<ProjectObject> = viewModel.getFilteredObjects(),
) {
    if (sourceId == targetNode.obj.id) return
    val descendants = viewModel.collectDescendants(sourceId)
    if (targetNode.obj.id in descendants) return

    val allObjects = viewModel.uiState.value.objects
    val sourceObj = allObjects.find { it.id == sourceId } ?: return
    // A drop the hierarchy forbids does nothing, rather than being sent for
    // the server to refuse.
    val hierarchy = viewModel.uiState.value.projectDetails?.hierarchy.orEmpty()
    // The parent each row is drawn under: its own when the tree draws it,
    // else the top level, "".
    val shown = drawn.mapTo(HashSet()) { obj -> obj.id }
    val home = { obj: ProjectObject -> obj.parent.takeIf { parent -> parent in shown }.orEmpty() }

    when (edge) {
        DragEdge.On -> {
            // Reparent under the target. The update names no place, so the
            // server gives the row a key after the new parent's children,
            // answers it and tells subscribers; reparentObject draws it with
            // that key.
            if (sourceObj.parent != targetNode.obj.id &&
                reparentAllowed(sourceObj, targetNode.obj.id, allObjects, hierarchy)
            ) {
                viewModel.reparentObject(sourceId, targetNode.obj.id)
            }
        }
        DragEdge.Top, DragEdge.Bottom -> {
            // Directly above or below the target, among the rows drawn at its
            // level: the new parent, when it changes, goes in the same move.
            // Another sort draws no place to go, so an edge does nothing there.
            if (viewModel.placement() == Placement.None) return
            val level = home(targetNode.obj)
            // Into another level only where the target's own parent is the
            // one drawn there: beside a row whose parent is hidden, the row
            // would go under that parent, out of sight.
            val reparent = home(sourceObj) != level
            if (reparent && targetNode.obj.parent != level) return
            if (reparent && !reparentAllowed(sourceObj, level, allObjects, hierarchy)) return
            val below = edge == DragEdge.Bottom
            // The rows drawn at the target's level, in the order drawn.
            val rows = drawn.filter { obj -> home(obj) == level }
            val parent = if (reparent) level else sourceObj.parent
            viewModel.moveBeside(
                objectId = sourceId,
                target = targetNode.obj.id,
                below = below,
                field = "",
                parent = parent.takeIf { reparent },
                other = drawnBeside(rows.map { obj -> obj.id }, targetNode.obj.id, below, sourceId),
                // The parent it goes among, which a move that keeps its own
                // names no other way, so the server takes the neighbours only
                // there; none where the level draws rows of other parents.
                scope = parent.takeIf { !reparent && rows.all { obj -> obj.parent == parent } },
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
    items: List<ProjectObject>,
    depth: Int,
    childMap: Map<String, List<ProjectObject>>,
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
