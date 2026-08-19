// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.board.PagedZoomableBoard
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.draggableItem
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.ui.components.dnd.isDragging
import org.mochios.android.ui.components.dnd.rememberDragState
import org.mochios.crm.R
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmObject
import org.mochios.crm.model.Person
import org.mochios.crm.model.CrmView
import org.mochios.crm.ui.crm.CrmViewModel
import org.mochios.android.R as MochiR

/**
 * Kanban board for the active view.
 *
 * @param objects Every object in the crm — the board needs the complete set so
 *   hierarchy, column grouping and drop ranks stay computed against the real
 *   list even while a filter hides part of it.
 * @param visibleIds Ids the search/filter state allows on screen, or null when
 *   nothing narrows the board. Cards outside the set are hidden, but they still
 *   count towards drop positions, so a drop on a filtered column lands where it
 *   would have without the filter.
 */
@Composable
fun BoardView(
    objects: List<CrmObject>,
    view: CrmView?,
    viewModel: CrmViewModel,
    // Threaded to BoardCard, which must not snapshot the flow itself.
    crmDetails: CrmDetails?,
    people: List<Person>,
    onObjectClick: (String) -> Unit,
    visibleIds: Set<String>? = null,
    /**
     * Starts a create from a column's "+", passing the field values that place
     * the object in that column. The caller opens the same dialog the FAB does
     * rather than creating outright, so a board object gets a title and its
     * fields like any other.
     */
    onStartCreate: ((initialValues: Map<String, String>) -> Unit)? = null,
) {
    if (view == null || view.columns.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.crm_board_no_columns),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // The class a column's "+" would create into: the view's own first, else
    // the CRM's first. Null when the CRM defines no classes at all, which is
    // what hides the button instead of letting it swallow the tap.
    //
    // The view's ids are checked against the CRM's classes rather than trusted.
    // A view naming a deleted class used to pass that id straight to create,
    // and the post failed on a class the server no longer had - a "+" that
    // looked like it did nothing.
    val definedClassIds = crmDetails?.classes.orEmpty().map { cls -> cls.id }.toSet()
    val createClassId = view.classes.firstOrNull { id -> id in definedClassIds }
        ?: crmDetails?.classes?.firstOrNull()?.id?.takeIf { id -> id.isNotBlank() }

    val columnFieldId = view.columns
    // Drawn from every class the view shows, not just the first one that
    // defines the field: each class holds its own options under a shared field
    // id, and a column missing from the board leaves its cards in Unassigned.
    val columnOptions = viewModel.getOptionsForClasses(columnFieldId, view.classes)
    val rowFieldId = view.rows.takeIf { it.isNotBlank() }
    val rowOptions = rowFieldId?.let { fieldId ->
        viewModel.getOptionsForClasses(fieldId, view.classes)
    } ?: emptyList()
    val borderFieldId = view.border.takeIf { it.isNotBlank() }

    // The objects the view admits. Everything below — nesting, top-level
    // cards, drop ranks — is computed against this set rather than the whole
    // crm, the way the tree does it. A deal parented to a company on a
    // deals-only board is a card in its own right here: measuring its parent
    // against the unfiltered list made it neither a top-level card nor a
    // nested one, and emptied the board.
    val classObjects = objects.filter { obj ->
        view.classes.isEmpty() || obj.objectClass in view.classes
    }
    val classObjectIds = classObjects.map { obj -> obj.id }.toSet()

    // Build parent-child map from the objects the view shows
    val childrenByParent = remember(classObjects) {
        val map = mutableMapOf<String, MutableList<CrmObject>>()
        for (obj in classObjects) {
            if (obj.parent.isNotBlank() && obj.parent in classObjectIds) {
                map.getOrPut(obj.parent) { mutableListOf() }.add(obj)
            }
        }
        map
    }

    // Only show top-level objects — those whose parent the view doesn't show,
    // so they have no card to nest inside.
    val filteredObjects = classObjects.filter { obj ->
        obj.parent.isBlank() || obj.parent !in classObjectIds
    }

    if (columnOptions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.crm_board_no_options),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Group objects by column value
    val objectsByColumn = columnOptions.associate { option ->
        option.id to filteredObjects.filter { obj ->
            obj.stringValue(columnFieldId) == option.id ||
                obj.listValue(columnFieldId).contains(option.id)
        }
    }

    // Add unassigned column
    val assignedIds = objectsByColumn.values.flatten().map { it.id }.toSet()
    val unassigned = filteredObjects.filter { it.id !in assignedIds }
    val unassignedLabel = stringResource(R.string.crm_board_unassigned)

    // Card drag-drop state shared across all columns. Re-keyed when the
    // column field changes so a leftover registration doesn't survive a view
    // switch.
    val cardDragState = rememberDragState()

    // Column-reorder state. Long-press a column header and drag horizontally
    // to reorder. Mirrors the web's reorder-mode column rail but driven by
    // long-press instead of an explicit toggle.
    val columnDragState = rememberDragState()
    val columnIdsKey = columnOptions.map { it.id }.joinToString(",")
    val columnOrder = remember(columnIdsKey) {
        mutableStateListOf<String>().apply { addAll(columnOptions.map { it.id }) }
    }
    val columnOptionsById = columnOptions.associateBy { it.id }
    val orderedColumns: List<FieldOption> = columnOrder.mapNotNull { columnOptionsById[it] }

    // Trello-style paging via the shared PagedZoomableBoard in lib: one
    // column at a time with a peek of neighbours; long-press a card to
    // zoom out and see several columns, with edge auto-scroll to reach
    // far-away targets.
    val pages: List<FieldOption> = remember(orderedColumns, unassigned, unassignedLabel) {
        buildList {
            addAll(orderedColumns)
            if (unassigned.isNotEmpty()) {
                add(FieldOption(id = "", name = unassignedLabel, colour = ""))
            }
        }
    }

    PagedZoomableBoard(
        pageCount = pages.size,
        cardDragState = cardDragState,
        columnDragState = columnDragState,
    ) { pageIndex ->
        val columnOption = pages[pageIndex]
        val isUnassigned = columnOption.id.isBlank()
        val columnObjects = if (isUnassigned) unassigned else (objectsByColumn[columnOption.id] ?: emptyList())
        BoardColumn(
            option = columnOption,
            crmDetails = crmDetails,
            people = people,
            objects = columnObjects,
            visibleIds = visibleIds,
            viewModel = viewModel,
            columnFieldId = columnFieldId,
            rowFieldId = rowFieldId,
            rowOptions = rowOptions,
            borderFieldId = borderFieldId,
            childrenByParent = childrenByParent,
            cardDragState = cardDragState,
            columnDragState = columnDragState,
            onColumnDrop = if (isUnassigned) {
                { _, _ -> /* unassigned column is not reorderable */ }
            } else {
                { sourceColumnId, edge ->
                    val sourceIndex = columnOrder.indexOf(sourceColumnId)
                    val targetIndex = columnOrder.indexOf(columnOption.id)
                    if (sourceIndex >= 0 && targetIndex >= 0 && sourceIndex != targetIndex) {
                        val dropIndex = when (edge) {
                            DragEdge.Start -> targetIndex
                            DragEdge.End -> targetIndex + 1
                            else -> targetIndex
                        }.let {
                            if (sourceIndex < it) it - 1 else it
                        }
                        columnOrder.removeAt(sourceIndex)
                        columnOrder.add(dropIndex.coerceIn(0, columnOrder.size), sourceColumnId)
                        viewModel.reorderColumnOptions(columnFieldId, columnOrder.toList())
                    }
                }
            },
            columnReorderEnabled = !isUnassigned,
            onObjectClick = onObjectClick,
            onRename = if (isUnassigned) null else {
                { newName -> viewModel.renameColumnOption(columnFieldId, columnOption.id, newName) }
            },
            onDelete = if (isUnassigned) null else {
                { viewModel.deleteColumnOption(columnFieldId, columnOption.id) }
            },
            // The class is resolved before the button is offered rather than
            // inside its click handler. With no class to create into there is
            // nothing the tap could do, and the handler used to swallow it
            // silently — a "+" that looks live and does nothing.
            onCreateInColumn = if (onStartCreate != null && !isUnassigned &&
                createClassId != null
            ) {
                { onStartCreate(mapOf(columnFieldId to columnOption.id)) }
            } else {
                null
            },
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardColumn(
    option: FieldOption,
    objects: List<CrmObject>,
    visibleIds: Set<String>?,
    viewModel: CrmViewModel,
    crmDetails: CrmDetails?,
    people: List<Person>,
    columnFieldId: String,
    rowFieldId: String?,
    rowOptions: List<FieldOption>,
    borderFieldId: String?,
    childrenByParent: Map<String, List<CrmObject>>,
    cardDragState: DragState,
    columnDragState: DragState,
    onColumnDrop: (sourceColumnId: String, edge: DragEdge) -> Unit,
    columnReorderEnabled: Boolean = true,
    onObjectClick: (String) -> Unit,
    onRename: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onCreateInColumn: (() -> Unit)? = null
) {
    // The whole column is a drop target for "drop on column" — append to end.
    // Cards inside register their own (smaller) drop targets that win when the
    // pointer is over them; this catches drops on empty space or on the
    // header.
    val columnDropModifier = if (option.id.isNotBlank()) {
        Modifier.dropTarget(
            state = cardDragState,
            itemId = "column:${option.id}",
            orientation = DropOrientation.OnOnly,
            onDrop = { sourceId, _ ->
                // Append: rank past the last card of this column.
                val rank = objects.size + 1
                viewModel.moveObject(sourceId, columnFieldId, option.id, rank)
            }
        )
    } else Modifier

    val isColumnDragging = columnDragState.isDragging(option.id)
    val isColumnTarget = columnReorderEnabled &&
        columnDragState.targetItemId == "header:${option.id}" &&
        columnDragState.draggingItemId != null &&
        columnDragState.draggingItemId != option.id
    val columnTargetEdge = columnDragState.targetEdge

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                MaterialTheme.shapes.medium
            )
            .then(columnDropModifier)
            .padding(6.dp)
            .alpha(if (isColumnDragging) 0.6f else 1f)
    ) {
        // Column header — draggable for column reorder when enabled.
        val columnDragHint = if (columnReorderEnabled) stringResource(R.string.crm_drag_column) else ""
        val headerDragModifier = if (columnReorderEnabled) {
            Modifier
                .semantics { contentDescription = columnDragHint }
                .draggableItem(state = columnDragState, itemId = option.id)
                .dropTarget(
                    state = columnDragState,
                    itemId = "header:${option.id}",
                    orientation = DropOrientation.Horizontal,
                    acceptedEdges = setOf(DragEdge.Start, DragEdge.End),
                    onDrop = { sourceId, edge -> onColumnDrop(sourceId, edge) }
                )
        } else Modifier

        val edgeBorderModifier = if (isColumnTarget) {
            when (columnTargetEdge) {
                DragEdge.Start -> Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                )
                DragEdge.End -> Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                )
                else -> Modifier
            }
        } else Modifier

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .then(headerDragModifier)
                .then(edgeBorderModifier)
                .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
        ) {
            if (option.colour.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(parseColor(option.colour))
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = option.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Card-count pill — the standard kanban column count, in a tonal
            // chip. Replaces the old washed-out primary-tinted header bar.
            // While a filter is on, the pill reads "shown / total" so the
            // column still tells you how much it really holds.
            val visibleCount = if (visibleIds == null) {
                objects.size
            } else {
                objects.count { obj -> obj.id in visibleIds }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                // While a filter is on, the pill reads "shown / total" so the
                // column still tells you how much it really holds.
                val visibleCount = if (visibleIds == null) {
                    objects.size
                } else {
                    objects.count { obj -> obj.id in visibleIds }
                }
                Text(
                    text = if (visibleIds == null) {
                        objects.size.toString()
                    } else {
                        "$visibleCount/${objects.size}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onCreateInColumn != null) {
                IconButton(onClick = onCreateInColumn, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.crm_board_new),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onRename != null || onDelete != null) {
                var showMenu by remember { mutableStateOf(false) }
                var showRenameDialog by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = stringResource(MochiR.string.common_more_options),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    MochiDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onRename != null) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.crm_board_rename)) },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            )
                        }
                        if (onDelete != null) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(MochiR.string.common_delete)) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            )
                        }
                    }
                }
                if (showRenameDialog && onRename != null) {
                    var newName by remember { mutableStateOf(option.name) }
                    MochiAlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = stringResource(R.string.crm_board_rename_column),
                        content = {
                            MochiTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmText = stringResource(R.string.crm_board_rename),
                        onConfirm = {
                            onRename(newName)
                            showRenameDialog = false
                        },
                        confirmEnabled = newName.isNotBlank(),
                        dismissText = stringResource(MochiR.string.common_cancel),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Column body
        if (rowFieldId != null && rowOptions.isNotEmpty()) {
            // Swimlane mode.
            // Drop ranks are scoped to the column, not the lane: the server's
            // rank_move_key lists every object sharing the target column value
            // and knows nothing about rows, so a lane-local position would be
            // applied as a column position.
            val columnOrder = viewModel.sortObjects(objects)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowOptions.forEach { rowOption ->
                    val rowObjects = viewModel.sortObjects(objects.filter { obj ->
                        obj.stringValue(rowFieldId) == rowOption.id ||
                            obj.listValue(rowFieldId).contains(rowOption.id)
                    })

                    val shownRowObjects = rowObjects.visible(visibleIds)
                    // A lane that a filter emptied is dropped entirely — a
                    // screen of bare lane headings under one hit is noise.
                    // Unfiltered, empty lanes still show as drop targets.
                    if (visibleIds != null && shownRowObjects.isEmpty()) return@forEach

                    item(key = "header_${rowOption.id}") {
                        // The lane's own drop target. The column-level one below
                        // has no lane to report, so without this a drop anywhere
                        // but on another card could not change lane at all —
                        // including into a lane that is currently empty.
                        Text(
                            text = rowOption.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .dropTarget(
                                    state = cardDragState,
                                    itemId = "lane:${option.id}:${rowOption.id}",
                                    orientation = DropOrientation.OnOnly,
                                    onDrop = { sourceId, _ ->
                                        viewModel.moveObject(
                                            objectId = sourceId,
                                            field = columnFieldId,
                                            value = option.id,
                                            rank = columnOrder.count { candidate ->
                                                candidate.id != sourceId
                                            } + 1,
                                            rowField = rowFieldId,
                                            rowValue = rowOption.id,
                                        )
                                    },
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    itemsIndexed(shownRowObjects, key = { _, o -> o.id }) { _, obj ->
                        BoardCard(
                            obj = obj,
                            viewModel = viewModel,
                            crmDetails = crmDetails,
                            people = people,
                            borderFieldId = borderFieldId,
                            childrenByParent = childrenByParent,
                            columnFieldId = columnFieldId,
                            rowFieldId = rowFieldId,
                            cardDragState = cardDragState,
                            cardIndexInColumn = columnOrder.indexOfFirst { candidate ->
                                candidate.id == obj.id
                            },
                            columnObjectsForDrop = columnOrder,
                            targetColumnId = option.id,
                            targetRowId = rowOption.id,
                            onClick = { onObjectClick(obj.id) }
                        )
                    }
                    item(key = "spacer_${rowOption.id}") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Unassigned row items
                val rowAssignedIds = rowOptions.flatMap { rowOpt ->
                    objects.filter { obj ->
                        obj.stringValue(rowFieldId) == rowOpt.id ||
                            obj.listValue(rowFieldId).contains(rowOpt.id)
                    }.map { it.id }
                }.toSet()
                val unassignedRow = viewModel.sortObjects(objects.filter { it.id !in rowAssignedIds })
                val shownUnassignedRow = unassignedRow.visible(visibleIds)
                if (shownUnassignedRow.isNotEmpty()) {
                    item(key = "header_unassigned_row") {
                        Text(
                            text = stringResource(R.string.crm_board_unassigned),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    itemsIndexed(shownUnassignedRow, key = { _, o -> o.id }) { _, obj ->
                        BoardCard(
                            obj = obj,
                            viewModel = viewModel,
                            crmDetails = crmDetails,
                            people = people,
                            borderFieldId = borderFieldId,
                            childrenByParent = childrenByParent,
                            columnFieldId = columnFieldId,
                            rowFieldId = rowFieldId,
                            cardDragState = cardDragState,
                            cardIndexInColumn = columnOrder.indexOfFirst { candidate ->
                                candidate.id == obj.id
                            },
                            columnObjectsForDrop = columnOrder,
                            targetColumnId = option.id,
                            targetRowId = "",
                            onClick = { onObjectClick(obj.id) }
                        )
                    }
                }
            }
        } else {
            // Simple list mode. The card list fills all remaining column
            // height. Double-tapping the empty space below the last card
            // creates a card (matching the web board's double-click-to-
            // create); the tap zone sits behind the list so it only
            // receives touches where there are no cards.
            val sortedObjects = viewModel.sortObjects(objects)
            val shownObjects = sortedObjects.visible(visibleIds)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (onCreateInColumn != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                                onDoubleClick = onCreateInColumn,
                            )
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(shownObjects, key = { _, o -> o.id }) { _, obj ->
                        BoardCard(
                            obj = obj,
                            viewModel = viewModel,
                            crmDetails = crmDetails,
                            people = people,
                            borderFieldId = borderFieldId,
                            childrenByParent = childrenByParent,
                            cardDragState = cardDragState,
                            cardIndexInColumn = sortedObjects.indexOfFirst { candidate ->
                                candidate.id == obj.id
                            },
                            columnObjectsForDrop = sortedObjects,
                            targetColumnId = option.id,
                            onClick = { onObjectClick(obj.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keeps only the objects the current filter allows on screen. A null [visibleIds]
 * means nothing is filtered, so the list passes through untouched.
 */
private fun List<CrmObject>.visible(visibleIds: Set<String>?): List<CrmObject> =
    if (visibleIds == null) this else filter { obj -> obj.id in visibleIds }

fun parseColor(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        when (cleaned.length) {
            6 -> Color(android.graphics.Color.parseColor("#$cleaned"))
            8 -> Color(android.graphics.Color.parseColor("#$cleaned"))
            3 -> {
                val expanded = cleaned.map { "$it$it" }.joinToString("")
                Color(android.graphics.Color.parseColor("#$expanded"))
            }
            else -> Color.Gray
        }
    } catch (_: Exception) {
        Color.Gray
    }
}
