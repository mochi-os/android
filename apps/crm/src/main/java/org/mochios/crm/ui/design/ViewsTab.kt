// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.crm.R
import org.mochios.crm.model.CrmClass
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField
import org.mochios.crm.model.CrmView
import org.mochios.crm.ui.`object`.ConfirmDeleteDialog
import org.mochios.android.R as MochiR

@Composable
fun ViewsTab(
    views: List<CrmView>,
    classes: List<CrmClass>,
    fields: Map<String, List<CrmField>>,
    viewModel: DesignViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingView by remember { mutableStateOf<CrmView?>(null) }
    var deletingView by remember { mutableStateOf<CrmView?>(null) }

    // Flatten all fields across classes for column/row selection
    val allFields = remember(fields) {
        fields.values.flatten().distinctBy { it.id }
    }
    val enumeratedFields = remember(allFields) {
        allFields.filter { it.fieldtype == "enumerated" }
    }

    val uiState by viewModel.uiState.collectAsState()
    val crmDetails = uiState.crmDetails

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.crm_views_add))
            }
        }
    ) { padding ->
        if (views.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.crm_views_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.crm_views_empty_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (crmDetails != null) {
                    val previewView = views.sortedBy { it.rank }.firstOrNull()
                    item(key = "preview") {
                        DesignPreview(
                            crm = crmDetails,
                            view = previewView,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
                val sortedViews = views.sortedBy { it.rank }
                itemsIndexed(sortedViews, key = { _, v -> v.id }) { index, view ->
                    ViewRow(
                        view = view,
                        allFields = allFields,
                        canMoveUp = index > 0,
                        canMoveDown = index < sortedViews.size - 1,
                        onMoveUp = {
                            val newOrder = sortedViews.toMutableList().also {
                                val tmp = it[index - 1]; it[index - 1] = it[index]; it[index] = tmp
                            }.joinToString(",") { it.id }
                            viewModel.reorderViews(newOrder)
                        },
                        onMoveDown = {
                            val newOrder = sortedViews.toMutableList().also {
                                val tmp = it[index + 1]; it[index + 1] = it[index]; it[index] = tmp
                            }.joinToString(",") { it.id }
                            viewModel.reorderViews(newOrder)
                        },
                        onEdit = { editingView = view },
                        onDelete = { deletingView = view }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        ViewDialog(
            title = stringResource(R.string.crm_views_add_dialog_title),
            initialView = null,
            classes = classes,
            enumeratedFields = enumeratedFields,
            allFields = allFields,
            crmDetails = crmDetails,
            onDismiss = { showAddDialog = false },
            onSave = { name, viewtype, columns, rows, sort, direction, selectedClasses, border, filter ->
                viewModel.createView(
                    name = name,
                    viewtype = viewtype,
                    columns = columns,
                    rows = rows,
                    filter = filter,
                    sort = sort,
                    direction = direction,
                    classes = selectedClasses,
                    border = border
                )
                showAddDialog = false
            }
        )
    }

    editingView?.let { view ->
        ViewDialog(
            title = stringResource(R.string.crm_views_edit_dialog_title),
            initialView = view,
            classes = classes,
            enumeratedFields = enumeratedFields,
            allFields = allFields,
            crmDetails = crmDetails,
            onDismiss = { editingView = null },
            onSave = { name, viewtype, columns, rows, sort, direction, selectedClasses, border, filter ->
                viewModel.updateView(
                    viewId = view.id,
                    name = name,
                    viewtype = viewtype,
                    columns = columns,
                    rows = rows,
                    filter = filter,
                    sort = sort,
                    direction = direction,
                    classes = selectedClasses,
                    border = border
                )
                editingView = null
            }
        )
    }

    deletingView?.let { view ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.crm_views_delete_title),
            message = stringResource(R.string.crm_views_delete_message, view.name),
            onConfirm = {
                viewModel.deleteView(view.id)
                deletingView = null
            },
            onDismiss = { deletingView = null }
        )
    }
}

@Composable
private fun ViewRow(
    view: CrmView,
    allFields: List<CrmField>,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (view.viewtype == "board") Icons.Default.Dashboard
            else Icons.Default.FormatListBulleted,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = view.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            val viewBoardLabel = stringResource(R.string.crm_views_type_board)
            val viewListLabel = stringResource(R.string.crm_views_type_list)
            val details = buildList {
                add(if (view.viewtype == "board") viewBoardLabel else viewListLabel)
                if (view.columns.isNotBlank()) {
                    val field = allFields.find { it.id == view.columns }
                    if (field != null) add(stringResource(R.string.crm_views_by, field.name))
                }
                if (view.sort.isNotBlank()) {
                    add(stringResource(R.string.crm_views_sorted, view.direction))
                }
            }.joinToString(" · ")
            Text(
                text = details,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.crm_views_move_up),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.crm_views_move_down),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(MochiR.string.common_edit), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(MochiR.string.common_delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ViewDialog(
    title: String,
    initialView: CrmView?,
    classes: List<CrmClass>,
    enumeratedFields: List<CrmField>,
    allFields: List<CrmField>,
    crmDetails: CrmDetails?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        viewtype: String,
        columns: String?,
        rows: String?,
        sort: String?,
        direction: String?,
        classes: String?,
        border: String?,
        filter: String?,
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialView?.name ?: "") }
    var viewtype by remember { mutableStateOf(initialView?.viewtype ?: "board") }
    var columnsField by remember { mutableStateOf(initialView?.columns ?: "") }
    var rowsField by remember { mutableStateOf(initialView?.rows ?: "") }
    var sortField by remember { mutableStateOf(initialView?.sort ?: "") }
    var direction by remember { mutableStateOf(initialView?.direction ?: "asc") }
    var borderField by remember { mutableStateOf(initialView?.border ?: "") }
    var filterField by remember { mutableStateOf(initialView?.filter ?: "") }
    var selectedClasses by remember {
        mutableStateOf(initialView?.classes?.toSet() ?: emptySet())
    }

    var columnsExpanded by remember { mutableStateOf(false) }
    var rowsExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var borderExpanded by remember { mutableStateOf(false) }

    // Build a synthesized CrmView reflecting the dialog's current edit
    // state, so the inline preview tracks the user's changes in real-time.
    val previewView = CrmView(
        id = initialView?.id ?: "preview",
        name = name.ifBlank { initialView?.name.orEmpty() },
        viewtype = viewtype,
        filter = initialView?.filter.orEmpty(),
        columns = columnsField,
        rows = rowsField,
        fields = initialView?.fields.orEmpty(),
        sort = sortField,
        direction = direction,
        classes = selectedClasses.toList(),
        rank = initialView?.rank ?: 0,
        border = borderField
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (crmDetails != null) {
                    DesignPreview(
                        crm = crmDetails,
                        view = previewView
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.crm_class_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // View type
                Text(stringResource(R.string.crm_views_type), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = viewtype == "board",
                        onClick = { viewtype = "board" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text(stringResource(R.string.crm_views_type_board))
                    }
                    SegmentedButton(
                        selected = viewtype == "list",
                        onClick = { viewtype = "list" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text(stringResource(R.string.crm_views_type_list))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Columns field (for board view - which enumerated field drives columns)
                if (viewtype == "board") {
                    FieldDropdown(
                        label = stringResource(R.string.crm_views_columns_field),
                        selectedId = columnsField,
                        fields = enumeratedFields,
                        expanded = columnsExpanded,
                        onExpandedChange = { columnsExpanded = it },
                        onSelect = { columnsField = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Rows field (swimlanes)
                    FieldDropdown(
                        label = stringResource(R.string.crm_views_rows_field),
                        selectedId = rowsField,
                        fields = enumeratedFields,
                        expanded = rowsExpanded,
                        onExpandedChange = { rowsExpanded = it },
                        onSelect = { rowsField = it },
                        allowNone = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Border field
                    FieldDropdown(
                        label = stringResource(R.string.crm_views_border_field),
                        selectedId = borderField,
                        fields = enumeratedFields,
                        expanded = borderExpanded,
                        onExpandedChange = { borderExpanded = it },
                        onSelect = { borderField = it },
                        allowNone = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter (stored on the view; not yet applied to listings).
                OutlinedTextField(
                    value = filterField,
                    onValueChange = { filterField = it },
                    label = { Text(stringResource(R.string.crm_views_filter)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Sort
                FieldDropdown(
                    label = stringResource(R.string.crm_views_sort_by),
                    selectedId = sortField,
                    fields = allFields.filter { it.isSortable || it.fieldtype in listOf("number", "date", "text") },
                    expanded = sortExpanded,
                    onExpandedChange = { sortExpanded = it },
                    onSelect = { sortField = it },
                    allowNone = true,
                    extraOptions = listOf(
                        "number" to stringResource(R.string.crm_views_sort_number),
                        "created" to stringResource(R.string.crm_views_sort_created),
                        "updated" to stringResource(R.string.crm_views_sort_updated),
                        "rank" to stringResource(R.string.crm_views_sort_rank)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Direction
                Text(stringResource(R.string.crm_views_direction), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = direction == "asc",
                        onClick = { direction = "asc" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.crm_views_direction_asc))
                    }
                    SegmentedButton(
                        selected = direction == "desc",
                        onClick = { direction = "desc" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.crm_views_direction_desc))
                    }
                }

                // Class filter
                if (classes.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.crm_views_filter_classes), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        classes.forEach { cls ->
                            FilterChip(
                                selected = cls.id in selectedClasses,
                                onClick = {
                                    selectedClasses = if (cls.id in selectedClasses) {
                                        selectedClasses - cls.id
                                    } else {
                                        selectedClasses + cls.id
                                    }
                                },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name,
                        viewtype,
                        columnsField.ifBlank { null },
                        rowsField.ifBlank { null },
                        sortField.ifBlank { null },
                        direction,
                        selectedClasses.joinToString(",").ifBlank { null },
                        borderField.ifBlank { null },
                        filterField.ifBlank { null },
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(MochiR.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldDropdown(
    label: String,
    selectedId: String,
    fields: List<CrmField>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    allowNone: Boolean = false,
    extraOptions: List<Pair<String, String>> = emptyList()
) {
    val noneLabel = stringResource(R.string.crm_create_template_none)
    val selectedName = fields.find { it.id == selectedId }?.name
        ?: extraOptions.find { it.first == selectedId }?.second
        ?: if (selectedId.isBlank() && allowNone) noneLabel else selectedId

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            if (allowNone) {
                DropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = {
                        onSelect("")
                        onExpandedChange(false)
                    }
                )
            }
            extraOptions.forEach { (value, displayLabel) ->
                DropdownMenuItem(
                    text = { Text(displayLabel) },
                    onClick = {
                        onSelect(value)
                        onExpandedChange(false)
                    }
                )
            }
            fields.forEach { field ->
                DropdownMenuItem(
                    text = { Text(field.name) },
                    onClick = {
                        onSelect(field.id)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}
