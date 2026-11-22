// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.tree

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
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.draggableItem
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.ui.components.dnd.isDragging
import org.mochios.android.ui.components.FieldChoice
import org.mochios.android.ui.components.FieldValue
import org.mochios.android.ui.components.fieldDisplay
import org.mochios.projects.R
import org.mochios.projects.model.ProjectField
import org.mochios.projects.ui.`object`.DeleteObjectDialog
import org.mochios.projects.ui.project.ProjectViewModel
import org.mochios.projects.util.HIERARCHY_ROOT
import org.mochios.projects.util.parentAllowed
import org.mochios.projects.util.objectTitle
import org.mochios.projects.util.readableId
import org.mochios.projects.util.parentTargets
import org.mochios.android.R as MochiR

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreeRow(
    node: TreeNode,
    viewModel: ProjectViewModel,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    /** Null, as the move and the drag, for a reader who may not write. */
    onDelete: (() -> Unit)?,
    onReparent: ((newParentId: String) -> Unit)? = null,
    dragState: DragState? = null,
    onDragDrop: ((sourceId: String, edge: DragEdge) -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showReparentDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val indent = (node.depth * 24).dp
    val obj = node.obj
    val uiState by viewModel.uiState.collectAsState()
    val projectDetails = uiState.projectDetails
    val prefix = projectDetails?.project?.prefix ?: ""
    val cardFields = viewModel.getCardFields(obj.objectClass)
    val people = uiState.people

    val isBeingDragged by remember(dragState, obj.id) {
        derivedStateOf { dragState != null && dragState.isDragging(obj.id) }
    }
    val dropEdge by remember(dragState, obj.id) {
        derivedStateOf {
            val dragged = dragState?.draggingItemId
            if (dragState != null && dragState.targetItemId == obj.id &&
                dragged != null && dragged != obj.id
            ) {
                dragState.targetEdge
            } else {
                null
            }
        }
    }

    // Validate the drop target up-front so the modifier can reject self/descendant
    // drops without showing a misleading hover affordance.
    val acceptDrop: (String, DragEdge) -> Boolean = { sourceId, _ ->
        sourceId != obj.id && obj.id !in viewModel.collectDescendants(sourceId)
    }

    val dragHintLabel = if (dragState != null && onDragDrop != null) stringResource(R.string.projects_drag_row) else ""
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

    val edgeBorderModifier = if (dropEdge != null) {
        when (dropEdge) {
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

    // The class's title field is dropped from the card body: the title
    // already shows it, so it would print twice.
    val titleFieldId = viewModel.getClassById(obj.objectClass)?.title.orEmpty()
    val title = objectTitle(obj, projectDetails?.classes.orEmpty(), prefix)
    val bodyFields = cardFields.filter { field -> field.id != titleFieldId }
    val format = LocalFormat.current
    val choice: (ProjectField) -> (String) -> FieldChoice? = { field ->
        { id ->
            viewModel.getOptionsForObject(obj.objectClass, field.id).find { option -> option.id == id }
                ?.let { option -> FieldChoice(option.name, option.colour) }
        }
    }
    val person: (String) -> String? = { id -> people.find { member -> member.id == id }?.name }
    // Prefer a multi-line text field for the description, but a single-line one
    // is the description in most schemas too — only the title is special.
    val descriptionField = bodyFields.firstOrNull { field ->
        field.fieldtype == "text" && field.rows > 1
    } ?: bodyFields.firstOrNull { field -> field.fieldtype == "text" }
    val description = descriptionField
        ?.let { field -> obj.stringValue(field.id).takeIf { value -> value.isNotBlank() } }
    // Ordered by type rather than by the class's field order, so the status and
    // priority always open the meta row directly under the description instead
    // of landing on a wrapped second line behind an owner or a date.
    val metaOrder = listOf("enumerated", "user", "date")
    // Only what shows: an unticked box or an empty checklist adds no gap.
    val metaFields = bodyFields
        .filter { field ->
            field.id != descriptionField?.id &&
                fieldDisplay(field.fieldtype, obj.stringValue(field.id), format, field.name, choice(field), person) != null
        }
        .sortedBy { field ->
            metaOrder.indexOf(field.fieldtype).takeIf { rank -> rank >= 0 } ?: metaOrder.size
        }

    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 3.dp, bottom = 3.dp)
            .then(dragModifier)
            .then(visualModifier)
            .then(edgeBorderModifier)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // The expander pushes the id right on rows with children; the text
            // below shifts by the same amount to line up under it.
            val bodyStart = if (node.hasChildren) 36.dp else 0.dp

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.hasChildren) {
                    MochiIconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (node.isExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (node.isExpanded) {
                                stringResource(MochiR.string.common_collapse)
                            } else {
                                stringResource(MochiR.string.common_expand)
                            },
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = readableId(obj, prefix),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                val hierarchy = projectDetails?.hierarchy.orEmpty()
                val movable = onReparent != null && (
                    parentAllowed(hierarchy, obj.objectClass, HIERARCHY_ROOT) ||
                        parentTargets(obj, uiState.objects, hierarchy).isNotEmpty()
                    )
                if (movable || onDelete != null) Box {
                    MochiIconButton(
                        onClick = { showContextMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(MochiR.string.common_more_options),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MochiDropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        if (movable) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.projects_tree_move)) },
                                onClick = {
                                    showContextMenu = false
                                    showReparentDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.DriveFileMove,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                        if (onDelete != null) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(MochiR.string.common_delete)) },
                                onClick = {
                                    showContextMenu = false
                                    showDeleteConfirm = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                destructive = true,
                            )
                        }
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = bodyStart)
            )

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = bodyStart, top = 4.dp)
                )
            }

            if (metaFields.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = bodyStart, top = 6.dp)
                ) {
                    metaFields.forEach { field ->
                        FieldValue(
                            type = field.fieldtype,
                            value = obj.stringValue(field.id),
                            name = field.name,
                            choice = choice(field),
                            person = person,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        DeleteObjectDialog(
            title = objectTitle(obj, projectDetails?.classes.orEmpty(), prefix),
            descendants = viewModel.collectDescendants(obj.id).size,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showReparentDialog && onReparent != null) {
        // Only what the server takes: a parent of a class the hierarchy
        // allows, never the row itself or anything under it, and the top
        // level only for a class that may sit there.
        val hierarchy = projectDetails?.hierarchy.orEmpty()
        val rootAllowed = parentAllowed(hierarchy, obj.objectClass, HIERARCHY_ROOT)
        val possibleParents = parentTargets(obj, uiState.objects, hierarchy)
        MochiAlertDialog(
            onDismissRequest = { showReparentDialog = false },
            title = stringResource(R.string.projects_tree_move_to_parent),
            content = {
                LazyColumn {
                    if (rootAllowed) {
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
                                Text(stringResource(R.string.projects_tree_root_level), style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider()
                        }
                    }
                    items(possibleParents, key = { parent -> parent.id }) { parent ->
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
                                text = objectTitle(parent, projectDetails?.classes.orEmpty(), prefix),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider()
                    }
                }
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}
