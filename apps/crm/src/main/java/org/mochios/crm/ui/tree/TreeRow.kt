// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.draggableItem
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.ui.components.dnd.isDragging
import org.mochios.crm.R
import org.mochios.crm.ui.board.parseColor
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.android.R as MochiR

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreeRow(
    node: TreeNode,
    viewModel: CrmViewModel,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onReparent: ((newParentId: String) -> Unit)? = null,
    dragState: DragState? = null,
    onDragDrop: ((sourceId: String, edge: DragEdge) -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showReparentDialog by remember { mutableStateOf(false) }
    val indent = (node.depth * 24).dp
    val obj = node.obj
    val crmDetails = viewModel.uiState.value.crmDetails
    val titleFieldId = crmDetails?.classes?.find { it.id == obj.objectClass }
        ?.title?.takeIf { it.isNotBlank() }
    val untitled = stringResource(R.string.crm_untitled)
    val title = titleFieldId?.let { obj.stringValue(it) }.orEmpty().ifBlank { untitled }
    val cardFields = viewModel.getCardFields(obj.objectClass)
    val people = viewModel.uiState.value.people

    val isBeingDragged = dragState != null && dragState.isDragging(obj.id)
    val isDropTarget = dragState != null &&
        dragState.targetItemId == obj.id &&
        dragState.draggingItemId != null &&
        dragState.draggingItemId != obj.id
    val targetEdge = dragState?.targetEdge

    // Validate the drop target up-front so the modifier can reject self/descendant
    // drops without showing a misleading hover affordance.
    val acceptDrop: (String, DragEdge) -> Boolean = { sourceId, _ ->
        sourceId != obj.id && obj.id !in viewModel.collectDescendants(sourceId)
    }

    val dragHintLabel = if (dragState != null && onDragDrop != null) stringResource(R.string.crm_drag_row) else ""
    val dragModifier = if (dragState != null && onDragDrop != null) {
        Modifier
            .semantics { contentDescription = dragHintLabel }
            .draggableItem(state = dragState, itemId = obj.id)
            .dropTarget(
                state = dragState,
                itemId = obj.id,
                orientation = DropOrientation.Vertical,
                acceptedEdges = setOf(DragEdge.Top, DragEdge.Bottom, DragEdge.On),
                accept = acceptDrop,
                onDrop = { sourceId, edge -> onDragDrop(sourceId, edge) }
            )
    } else Modifier

    val edgeBorderModifier = if (isDropTarget) {
        when (targetEdge) {
            DragEdge.Top, DragEdge.Bottom -> Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            )
            DragEdge.On -> Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            ).background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
            else -> Modifier
        }
    } else Modifier

    val visualModifier = if (isBeingDragged) {
        Modifier
            .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.small)
            .scale(1.05f)
            .alpha(0.9f)
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)
            .then(visualModifier)
            .then(edgeBorderModifier)
            .clickable(onClick = onClick)
            .padding(start = indent + 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand/collapse
        if (node.hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (node.isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (node.isExpanded) stringResource(MochiR.string.common_collapse) else stringResource(MochiR.string.common_expand),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(28.dp))
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Title and field chips
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Field values rendered inline (matching web's list columns and the
            // board card) rather than as heavy chips: enumerated shows a colour
            // dot + name, user resolves to the member name, everything else is
            // plain secondary text.
            val fieldsWithValues = cardFields.filter { obj.stringValue(it.id).isNotBlank() }
            if (fieldsWithValues.isNotEmpty()) {
                Spacer(modifier = Modifier.size(3.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    fieldsWithValues.forEach { field ->
                        val value = obj.stringValue(field.id)
                        when (field.fieldtype) {
                            "enumerated" -> {
                                val opt = viewModel.getAllOptionsForField(field.id).find { it.id == value }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (opt != null && opt.colour.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(parseColor(opt.colour), MaterialTheme.shapes.extraSmall)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = opt?.name ?: value,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            "user" -> {
                                val resolved = people.find { it.id == value }?.name?.takeIf { it.isNotBlank() } ?: value
                                Text(
                                    text = resolved,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            else -> {
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overflow menu — accessible fallback for the move/delete actions
        // now that long-press is reserved for drag-start.
        Box {
            IconButton(
                onClick = { showContextMenu = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = stringResource(MochiR.string.common_more_options),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                if (onReparent != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.crm_tree_move)) },
                        onClick = {
                            showContextMenu = false
                            showReparentDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DriveFileMove, contentDescription = null)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(MochiR.string.common_delete)) },
                    onClick = {
                        showContextMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }

    if (showReparentDialog && onReparent != null) {
        val allObjects = viewModel.uiState.value.objects
        val possibleParents = allObjects.filter { it.id != obj.id }
        AlertDialog(
            onDismissRequest = { showReparentDialog = false },
            title = { Text(stringResource(R.string.crm_tree_move_to_parent)) },
            text = {
                LazyColumn {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReparent("")
                                    showReparentDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.crm_tree_root_level), style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                    items(possibleParents) { parent ->
                        val parentTitleField = crmDetails?.classes?.find { it.id == parent.objectClass }
                            ?.title?.takeIf { it.isNotBlank() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReparent(parent.id)
                                    showReparentDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = parentTitleField?.let { parent.stringValue(it) }.orEmpty().ifBlank { untitled },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReparentDialog = false }) { Text(stringResource(MochiR.string.common_cancel)) }
            }
        )
    }
}
