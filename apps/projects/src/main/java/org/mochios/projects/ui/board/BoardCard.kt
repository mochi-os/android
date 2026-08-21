// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.sp
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
import org.mochios.projects.R
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.ui.project.ProjectViewModel
import org.mochios.android.R as MochiR

private const val MAX_NESTING_DEPTH = 3

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BoardCard(
    obj: ProjectObject,
    viewModel: ProjectViewModel,
    borderFieldId: String?,
    childrenByParent: Map<String, List<ProjectObject>>,
    columnFieldId: String = "",
    rowFieldId: String? = null,
    depth: Int = 0,
    cardDragState: DragState? = null,
    cardIndexInColumn: Int = -1,
    columnObjectsForDrop: List<ProjectObject> = emptyList(),
    targetColumnId: String = "",
    /** The lane this card sits in, sent as row_value so a drop changes lane. */
    targetRowId: String = "",
    onClick: () -> Unit
) {
    var showMoveSheet by rememberSaveable(obj.id) { mutableStateOf(false) }
    var showOverflow by remember(obj.id) { mutableStateOf(false) }
    var collapsed by rememberSaveable(obj.id) { mutableStateOf(false) }

    val children = childrenByParent[obj.id] ?: emptyList()
    val hasChildren = children.isNotEmpty()
    val isNested = depth > 0

    val borderColor = if (borderFieldId != null) {
        val borderValue = obj.stringValue(borderFieldId)
        if (borderValue.isNotBlank()) {
            val options = viewModel.getAllOptionsForField(borderFieldId)
            val option = options.find { it.id == borderValue }
            if (option != null && option.colour.isNotBlank()) {
                parseColor(option.colour)
            } else null
        } else null
    } else null

    val uiState by viewModel.uiState.collectAsState()
    val projectDetails = uiState.projectDetails
    val people = uiState.people
    val prefix = projectDetails?.project?.prefix ?: ""
    val cls = projectDetails?.classes?.find { it.id == obj.objectClass }
    val titleFieldId = cls?.title?.takeIf { it.isNotBlank() }
    val title = if (titleFieldId != null) {
        obj.stringValue(titleFieldId).ifBlank { "$prefix-${obj.number}" }
    } else {
        obj.readable.ifBlank { "$prefix-${obj.number}" }
    }
    val cardFields = viewModel.getCardFields(obj.objectClass)

    // Drag-drop wiring — only top-level cards participate. Nested children stay
    // visual-only to avoid confusing dual-target situations (the parent and
    // the child both being valid drop targets at the same point).
    val isDragSource = cardDragState != null && !isNested && cardIndexInColumn >= 0
    val isBeingDragged = isDragSource && cardDragState!!.isDragging(obj.id)
    val isDropTarget = isDragSource && cardDragState!!.targetItemId == obj.id &&
        cardDragState.draggingItemId != null && cardDragState.draggingItemId != obj.id
    val targetEdge = cardDragState?.targetEdge

    val dragHintLabel = if (isDragSource) stringResource(R.string.projects_drag_card) else ""
    val dragModifier = if (isDragSource) {
        Modifier
            .semantics { contentDescription = dragHintLabel }
            .draggableItem(state = cardDragState!!, itemId = obj.id)
            .dropTarget(
                state = cardDragState,
                itemId = obj.id,
                orientation = DropOrientation.Vertical,
                acceptedEdges = setOf(DragEdge.Top, DragEdge.Bottom),
                onDrop = { sourceId, edge ->
                    val rank = dropRank(
                        columnObjects = columnObjectsForDrop.map { it.id },
                        targetId = obj.id,
                        sourceId = sourceId,
                        below = edge == DragEdge.Bottom,
                    )
                    // rowField/rowValue carry the lane. Without them the server
                    // leaves the row value untouched (projects.star only writes
                    // it under `if row_field:`), so a cross-lane drag was a
                    // no-op and the refresh put the card back where it started.
                    viewModel.moveObject(
                        objectId = sourceId,
                        field = columnFieldId,
                        value = targetColumnId,
                        rank = rank,
                        rowField = rowFieldId,
                        rowValue = if (rowFieldId != null) targetRowId else null,
                    )
                }
            )
    } else Modifier

    val edgeBorderModifier = if (isDropTarget) {
        when (targetEdge) {
            DragEdge.Top -> Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            )
            DragEdge.Bottom -> Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
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

    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)
            .then(visualModifier)
            .then(edgeBorderModifier)
            // Every card carries a visible outline (matching web, where each
            // Card has a default border); the border-field colour overrides the
            // default subtle outline when one is set.
            .border(
                1.dp,
                borderColor ?: MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (!isNested) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(if (!isNested) 12.dp else 8.dp)) {
                // Header row: [chevron] [title] [child count] [overflow]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (hasChildren) {
                        Icon(
                            imageVector = if (collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                            contentDescription = if (collapsed) stringResource(MochiR.string.common_expand) else stringResource(MochiR.string.common_collapse),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { collapsed = !collapsed }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = title,
                        style = if (!isNested) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasChildren && collapsed) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${children.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Overflow menu — accessible fallback for the
                    // MoveObjectSheet now that long-press is reserved for
                    // drag-start.
                    if (!isNested) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box {
                            MochiIconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = stringResource(MochiR.string.common_more_options),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MochiDropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(R.string.projects_move_to_column)) },
                                    onClick = {
                                        showOverflow = false
                                        showMoveSheet = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                                )
                            }
                        }
                    }
                }

                // Body fields (top-level only, matching web's !isNested check)
                if (!isNested) {
                    val bodyFields = cardFields.filter {
                        it.id != columnFieldId && it.id != rowFieldId && it.id != titleFieldId
                    }
                    val fieldsWithValues = bodyFields.filter { obj.stringValue(it.id).isNotBlank() }
                    if (fieldsWithValues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            fieldsWithValues.forEach { field ->
                                val value = obj.stringValue(field.id)
                                when (field.fieldtype) {
                                    "enumerated" -> {
                                        val options = viewModel.getAllOptionsForField(field.id)
                                        val opt = options.find { it.id == value }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (opt != null && opt.colour.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(
                                                            parseColor(opt.colour),
                                                            MaterialTheme.shapes.extraSmall
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = opt?.name ?: value,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    "user" -> {
                                        // Resolve the entity ID to a member name
                                        // (matching the detail view and web);
                                        // fall back to the raw value if unknown.
                                        val resolved = people.find { it.id == value }
                                            ?.name?.takeIf { it.isNotBlank() } ?: value
                                        Text(
                                            text = resolved,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = value.take(80),
                                            fontSize = 10.sp,
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

                // Nested children
                if (hasChildren && !collapsed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    if (depth < MAX_NESTING_DEPTH) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            children.sortedBy { it.rank }.forEach { child ->
                                BoardCard(
                                    obj = child,
                                    viewModel = viewModel,
                                    borderFieldId = borderFieldId,
                                    childrenByParent = childrenByParent,
                                    columnFieldId = columnFieldId,
                                    rowFieldId = rowFieldId,
                                    depth = depth + 1,
                                    onClick = onClick
                                )
                            }
                        }
                    } else {
                        val deepCount = countDeepChildren(obj.id, childrenByParent)
                        Text(
                            text = stringResource(R.string.projects_board_nested_count, deepCount),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
        }
    }

    if (showMoveSheet) {
        MoveObjectSheet(
            obj = obj,
            viewModel = viewModel,
            onDismiss = { showMoveSheet = false }
        )
    }
}

private fun countDeepChildren(parentId: String, childrenByParent: Map<String, List<ProjectObject>>): Int {
    val direct = childrenByParent[parentId] ?: return 0
    return direct.size + direct.sumOf { countDeepChildren(it.id, childrenByParent) }
}

/**
 * The 1-based position to ask the server to place a dragged card at.
 *
 * [columnObjects] must be the whole COLUMN in rank order, not one swimlane of
 * it: the server scopes the slot to (field, value) — `rank_move_key` in
 * projects.star lists every object sharing the target column value — and has no
 * lane dimension. Passing a lane-local list made position 2 of a lane land as
 * position 2 of the column.
 *
 * A source already in this column is excluded from the count, because the
 * server sees the list with it removed.
 */
internal fun dropRank(
    columnObjects: List<String>,
    targetId: String,
    sourceId: String,
    below: Boolean,
): Int {
    val others = columnObjects.filter { it != sourceId }
    val index = others.indexOf(targetId)
    if (index < 0) return others.size + 1
    return if (below) index + 2 else index + 1
}
