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
import org.mochios.projects.ui.project.ProjectViewModel

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
    val childMap = objects.groupBy { it.parent }
    val roots = objects.filter { it.parent.isBlank() || objects.none { other -> other.id == it.parent } }

    fun flattenTree(items: List<ProjectObject>, depth: Int): List<TreeNode> {
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
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(flatNodes, key = { it.obj.id }) { node ->
            TreeRow(
                node = node,
                viewModel = viewModel,
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
 * Resolve a tree drag-drop into [ProjectViewModel.moveObject] /
 * [ProjectViewModel.reparentObject] calls. Cycle prevention rejects drops
 * onto the source's own descendants. Sibling reorder ([DragEdge.Top] /
 * [DragEdge.Bottom]) issues a `moveObject` with `scope_parent` set to the
 * target's parent so the server renumbers ranks within that subtree only.
 */
private fun handleTreeDrop(
    sourceId: String,
    targetNode: TreeNode,
    edge: DragEdge,
    viewModel: ProjectViewModel,
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
                // Cross-parent move. Reparent only; the server appends to
                // the new parent's children. We don't immediately follow
                // up with a rank update because the two requests would
                // race — by the time the rank update reaches the server,
                // the reparent may not have applied yet, leading to a
                // sibling-rank update against the wrong parent. Users
                // wanting precise positioning can drag again after the
                // reparent settles.
                viewModel.reparentObject(sourceId, newParent)
                return
            }
            // Same-parent: pure sibling reorder. Server's
            // action_object_move treats `scope_parent` as falsy when empty
            // (root level), so we can only reorder under a non-root
            // parent here. Root-level sibling reorder via drag is a known
            // limitation; the row's overflow menu's "Move" dialog still
            // works, and "Move" → root-level happens to leave rank up to
            // the server (which appends).
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
