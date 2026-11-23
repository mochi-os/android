// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import org.mochios.android.ui.components.dnd.rememberDragState
import org.mochios.android.ui.components.FieldChoice
import org.mochios.android.ui.components.FieldValue
import org.mochios.android.ui.components.fieldDisplay
import org.mochios.projects.R
import org.mochios.projects.model.ProjectField
import org.mochios.projects.model.ProjectObject
import org.mochios.projects.ui.project.ProjectViewModel
import org.mochios.projects.util.objectTitle
import org.mochios.android.R as MochiR
import org.mochios.android.i18n.LocalFormat

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
    targetColumnId: String = "",
    /** The lane this card sits in, sent as row_value so a drop changes lane. */
    targetRowId: String = "",
    /** Opens the tapped card's object; a nested card names its own, not its parent's. */
    onOpen: (String) -> Unit
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
            val options = viewModel.getOptionsForObject(obj.objectClass, borderFieldId)
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
    val title = objectTitle(obj, projectDetails?.classes.orEmpty(), prefix)
    val cardFields = viewModel.getCardFields(obj.objectClass)
    // Moving a card is a write; a reader's cards neither drag nor offer a move.
    val writable = viewModel.canWrite()

    // Drag-drop wiring — only top-level cards participate. Nested children stay
    // visual-only to avoid confusing dual-target situations (the parent and
    // the child both being valid drop targets at the same point).
    val isDragSource = writable && cardDragState != null && !isNested && cardIndexInColumn >= 0
    val isBeingDragged by remember(cardDragState, obj.id, isDragSource) {
        derivedStateOf { isDragSource && cardDragState!!.isDragging(obj.id) }
    }
    val dropEdge by remember(cardDragState, obj.id, isDragSource) {
        derivedStateOf {
            val dragged = cardDragState?.draggingItemId
            if (isDragSource && cardDragState!!.targetItemId == obj.id &&
                dragged != null && dragged != obj.id
            ) {
                cardDragState.targetEdge
            } else {
                null
            }
        }
    }

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
                    // Directly below or above this card, as the board shows it.
                    // rowField/rowValue carry the lane. Without them the server
                    // leaves the row value untouched (projects.star only writes
                    // it under `if row_field:` on the decoded `row`), so a cross-lane drag was a
                    // no-op and the refresh put the card back where it started.
                    viewModel.moveBeside(
                        objectId = sourceId,
                        target = obj.id,
                        below = edge == DragEdge.Bottom,
                        field = columnFieldId,
                        value = targetColumnId,
                        rowField = rowFieldId,
                        rowValue = if (rowFieldId != null) targetRowId else null,
                    )
                }
            )
    } else Modifier

    val edgeBorderModifier = if (dropEdge != null) {
        when (dropEdge) {
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

    // The board draws the lifted card under the pointer; this one stands in
    // for it where it came from.
    val visualModifier = if (isBeingDragged) Modifier.alpha(0.4f) else Modifier

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
                2.dp,
                borderColor ?: MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small,
            )
            .clickable { onOpen(obj.id) },
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
                            text = LocalFormat.current.formatNumber(children.size),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Overflow menu — accessible fallback for the
                    // MoveObjectSheet now that long-press is reserved for
                    // drag-start.
                    if (!isNested && writable) {
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
                    // Each by its type, as the web's card: an option by name and
                    // colour, a person by name, a date and a number in the
                    // user's format, a checklist's progress, a ticked box by
                    // its field's name. Only what shows takes a line.
                    val format = LocalFormat.current
                    val choice: (ProjectField) -> (String) -> FieldChoice? = { field ->
                        { id ->
                            viewModel.getOptionsForObject(obj.objectClass, field.id).find { option -> option.id == id }
                                ?.let { option -> FieldChoice(option.name, option.colour) }
                        }
                    }
                    val person: (String) -> String? = { id -> people.find { member -> member.id == id }?.name }
                    val shown = bodyFields.mapNotNull { field ->
                        fieldDisplay(field.fieldtype, obj.stringValue(field.id), format, field.name, choice(field), person)
                    }
                    if (shown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            shown.forEach { display -> FieldValue(display, compact = true) }
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
                                    onOpen = onOpen
                                )
                            }
                        }
                    } else {
                        val deepCount = countDeepChildren(obj.id, childrenByParent)
                        Text(
                            text = nestedCount(deepCount),
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

/** "+N nested": the cards folded under one past the deepest level drawn. */
@Composable
internal fun nestedCount(count: Int): String =
    stringResource(R.string.projects_board_nested_count, LocalFormat.current.formatNumber(count))

private fun countDeepChildren(parentId: String, childrenByParent: Map<String, List<ProjectObject>>): Int {
    val direct = childrenByParent[parentId] ?: return 0
    return direct.size + direct.sumOf { countDeepChildren(it.id, childrenByParent) }
}

/**
 * The drag state the board's cards share. A card takes a drop anywhere on
 * it, above or below by the half the pointer is in, as the web decides by
 * the card's midpoint: a card takes no drop "on" it, so a middle zone for
 * one let a release there fall through to the column, which sent the card
 * to the column's end.
 */
@Composable
internal fun rememberCardDragState(): DragState = rememberDragState(onZoneFraction = 0f)
