// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.R

/**
 * A saved board or list view, as the design screens list and edit one.
 *
 * @property id Identifier of the view.
 * @property name Label shown on the row and in the edit dialog.
 * @property viewtype Either `board` or `list`.
 * @property filter Filter expression stored on the view.
 * @property columns Field id driving board columns.
 * @property rows Field id driving board swimlanes.
 * @property fields Field ids the view shows, passed through untouched.
 * @property sort Field id or pseudo-field the view sorts on.
 * @property direction Either `asc` or `desc`.
 * @property classes Class ids the view is restricted to.
 * @property rank Sort position within the list.
 * @property border Field id driving a card's border colour.
 */
data class ViewListItem(
    val id: String,
    val name: String,
    val viewtype: String,
    val filter: String,
    val columns: String,
    val rows: String,
    val fields: String,
    val sort: String,
    val direction: String,
    val classes: List<String>,
    val rank: Int,
    val border: String
)

/**
 * The edits a [ViewListTab] dialog collected, ready to send to a repository.
 * Blank entries arrive as `null` so an unset field clears server-side.
 *
 * @property name Name typed into the dialog.
 * @property viewtype Either `board` or `list`.
 * @property columns Field id driving board columns, or null.
 * @property rows Field id driving board swimlanes, or null.
 * @property filter Filter expression, or null.
 * @property sort Field id or pseudo-field to sort on, or null.
 * @property direction Either `asc` or `desc`.
 * @property classes Comma-joined class ids the view is limited to, or null.
 * @property border Field id driving a card's border colour, or null.
 */
data class ViewDraft(
    val name: String,
    val viewtype: String,
    val columns: String?,
    val rows: String?,
    val filter: String?,
    val sort: String?,
    val direction: String?,
    val classes: String?,
    val border: String?
)

/**
 * A field a view can be organised by, reduced to what the pickers need.
 *
 * @property id Identifier stored on the view.
 * @property name Label shown in the dropdowns.
 * @property fieldtype Server field type, used to offer sortable candidates.
 * @property isSortable Whether the field carries the server's sort flag.
 */
data class ViewFieldOption(
    val id: String,
    val name: String,
    val fieldtype: String,
    val isSortable: Boolean
)

/**
 * Wording for [ViewListTab]. Each feature keeps its own translated strings, so
 * the caller resolves them and passes them in rather than the library owning a
 * second copy of every locale.
 *
 * @property addAction Content description of the add button.
 * @property empty Placeholder shown when there are no views.
 * @property emptySubtitle Second line of the empty placeholder.
 * @property addDialogTitle Title of the create dialog.
 * @property editDialogTitle Title of the edit dialog.
 * @property deleteTitle Title of the delete confirmation.
 * @property deleteMessage Body of the delete confirmation, given the view name.
 * @property byField Row detail naming the grouping field, given the field name.
 * @property sortedBy Row detail naming the sort order, given the direction.
 * @property typeBoard Label of the board view type.
 * @property typeList Label of the list view type.
 * @property moveUp Content description of the move-up button.
 * @property moveDown Content description of the move-down button.
 * @property nameLabel Label of the name field.
 * @property typeLabel Heading above the view type selector.
 * @property columnsField Label of the columns field picker.
 * @property rowsField Label of the swimlane field picker.
 * @property borderField Label of the border colour field picker.
 * @property filter Label of the filter field.
 * @property sortBy Label of the sort field picker.
 * @property direction Heading above the sort direction selector.
 * @property directionAsc Label of the ascending option.
 * @property directionDesc Label of the descending option.
 * @property filterClasses Heading above the class filter chips.
 * @property none Label of a picker's empty choice.
 */
data class ViewListLabels(
    val addAction: String,
    val empty: String,
    val emptySubtitle: String,
    val addDialogTitle: String,
    val editDialogTitle: String,
    val deleteTitle: String,
    val deleteMessage: (viewName: String) -> String,
    val byField: (fieldName: String) -> String,
    val sortedBy: (direction: String) -> String,
    val typeBoard: String,
    val typeList: String,
    val moveUp: String,
    val moveDown: String,
    val nameLabel: String,
    val typeLabel: String,
    val columnsField: String,
    val rowsField: String,
    val borderField: String,
    val filter: String,
    val sortBy: String,
    val direction: String,
    val directionAsc: String,
    val directionDesc: String,
    val filterClasses: String,
    val none: String
)

/**
 * List of a CRM's or project's saved views with reorder, edit and delete, plus
 * the create and edit dialog, shared by the two design screens. Rows are sorted
 * by [ViewListItem.rank]; reordering reports the whole new order as a
 * comma-joined id list.
 *
 * @param views Views to list, in any order.
 * @param classes Classes offered as the dialog's class filter.
 * @param fields Every field of every class, keyed by class id.
 * @param labels Feature-specific wording.
 * @param sortOptions Pseudo-fields offered alongside the sortable fields, as
 *   id-to-label pairs — the two features offer different sets.
 * @param onCreateView Called with the dialog's edits when a view is added.
 * @param onUpdateView Called with a view's id and the dialog's edits on save.
 * @param onDeleteView Called with a view's id once deletion is confirmed.
 * @param onReorderViews Called with the new order as comma-joined view ids.
 * @param preview Optional feature-rendered preview of a view, shown above the
 *   list and inside the dialog; receives null when there is nothing to show.
 */
@Composable
fun ViewListTab(
    views: List<ViewListItem>,
    classes: List<ClassListItem>,
    fields: Map<String, List<ViewFieldOption>>,
    labels: ViewListLabels,
    sortOptions: List<Pair<String, String>>,
    onCreateView: (ViewDraft) -> Unit,
    onUpdateView: (String, ViewDraft) -> Unit,
    onDeleteView: (String) -> Unit,
    onReorderViews: (String) -> Unit,
    preview: (@Composable (ViewListItem?, Modifier) -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingView by remember { mutableStateOf<ViewListItem?>(null) }
    var deletingView by remember { mutableStateOf<ViewListItem?>(null) }

    val allFields = remember(fields) {
        fields.values.flatten().distinctBy { field -> field.id }
    }
    val enumeratedFields = remember(allFields) {
        allFields.filter { field -> field.fieldtype == "enumerated" }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = labels.addAction)
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
                    text = labels.empty,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = labels.emptySubtitle,
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
                if (preview != null) {
                    val topView = views.sortedBy { view -> view.rank }.firstOrNull()
                    item(key = "preview") {
                        preview(
                            topView,
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
                val sortedViews = views.sortedBy { view -> view.rank }
                itemsIndexed(sortedViews, key = { _, view -> view.id }) { index, view ->
                    ViewRow(
                        view = view,
                        allFields = allFields,
                        labels = labels,
                        canMoveUp = index > 0,
                        canMoveDown = index < sortedViews.size - 1,
                        onMoveUp = { onReorderViews(sortedViews.swapped(index, index - 1)) },
                        onMoveDown = { onReorderViews(sortedViews.swapped(index, index + 1)) },
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
            title = labels.addDialogTitle,
            initialView = null,
            classes = classes,
            enumeratedFields = enumeratedFields,
            allFields = allFields,
            labels = labels,
            sortOptions = sortOptions,
            preview = preview,
            onDismiss = { showAddDialog = false },
            onSave = { draft ->
                onCreateView(draft)
                showAddDialog = false
            }
        )
    }

    editingView?.let { view ->
        ViewDialog(
            title = labels.editDialogTitle,
            initialView = view,
            classes = classes,
            enumeratedFields = enumeratedFields,
            allFields = allFields,
            labels = labels,
            sortOptions = sortOptions,
            preview = preview,
            onDismiss = { editingView = null },
            onSave = { draft ->
                onUpdateView(view.id, draft)
                editingView = null
            }
        )
    }

    deletingView?.let { view ->
        MochiAlertDialog(
            onDismissRequest = { deletingView = null },
            title = labels.deleteTitle,
            text = labels.deleteMessage(view.name),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                onDeleteView(view.id)
                deletingView = null
            },
            destructive = true,
            dismissText = stringResource(R.string.common_cancel),
        )
    }
}

/**
 * The list's ids with the entries at [from] and [to] exchanged, comma-joined
 * the way the reorder endpoint expects.
 */
private fun List<ViewListItem>.swapped(from: Int, to: Int): String {
    val reordered = toMutableList()
    val moved = reordered[to]
    reordered[to] = reordered[from]
    reordered[from] = moved
    return reordered.joinToString(",") { view -> view.id }
}

@Composable
private fun ViewRow(
    view: ViewListItem,
    allFields: List<ViewFieldOption>,
    labels: ViewListLabels,
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
            val details = buildList {
                add(if (view.viewtype == "board") labels.typeBoard else labels.typeList)
                if (view.columns.isNotBlank()) {
                    val field = allFields.find { candidate -> candidate.id == view.columns }
                    if (field != null) add(labels.byField(field.name))
                }
                if (view.sort.isNotBlank()) {
                    add(labels.sortedBy(view.direction))
                }
            }.joinToString(" · ")
            Text(
                text = details,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MochiIconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = labels.moveUp,
                modifier = Modifier.size(18.dp),
            )
        }
        MochiIconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = labels.moveDown,
                modifier = Modifier.size(18.dp),
            )
        }
        MochiIconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.common_edit),
                modifier = Modifier.size(18.dp)
            )
        }
        MochiIconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.common_delete),
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
    initialView: ViewListItem?,
    classes: List<ClassListItem>,
    enumeratedFields: List<ViewFieldOption>,
    allFields: List<ViewFieldOption>,
    labels: ViewListLabels,
    sortOptions: List<Pair<String, String>>,
    preview: (@Composable (ViewListItem?, Modifier) -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (ViewDraft) -> Unit
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

    val previewView = ViewListItem(
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

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (preview != null) {
                    preview(previewView, Modifier)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                MochiTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(labels.nameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(labels.typeLabel, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = viewtype == "board",
                        onClick = { viewtype = "board" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                Icons.Default.Dashboard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text(labels.typeBoard)
                    }
                    SegmentedButton(
                        selected = viewtype == "list",
                        onClick = { viewtype = "list" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                Icons.Default.FormatListBulleted,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ) {
                        Text(labels.typeList)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (viewtype == "board") {
                    FieldDropdown(
                        label = labels.columnsField,
                        selectedId = columnsField,
                        fields = enumeratedFields,
                        noneLabel = labels.none,
                        expanded = columnsExpanded,
                        onExpandedChange = { expanded -> columnsExpanded = expanded },
                        onSelect = { selected -> columnsField = selected }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FieldDropdown(
                        label = labels.rowsField,
                        selectedId = rowsField,
                        fields = enumeratedFields,
                        noneLabel = labels.none,
                        expanded = rowsExpanded,
                        onExpandedChange = { expanded -> rowsExpanded = expanded },
                        onSelect = { selected -> rowsField = selected },
                        allowNone = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FieldDropdown(
                        label = labels.borderField,
                        selectedId = borderField,
                        fields = enumeratedFields,
                        noneLabel = labels.none,
                        expanded = borderExpanded,
                        onExpandedChange = { expanded -> borderExpanded = expanded },
                        onSelect = { selected -> borderField = selected },
                        allowNone = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                MochiTextField(
                    value = filterField,
                    onValueChange = { value -> filterField = value },
                    label = { Text(labels.filter) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                FieldDropdown(
                    label = labels.sortBy,
                    selectedId = sortField,
                    fields = allFields.filter { field ->
                        field.isSortable || field.fieldtype in listOf("number", "date", "text")
                    },
                    noneLabel = labels.none,
                    expanded = sortExpanded,
                    onExpandedChange = { expanded -> sortExpanded = expanded },
                    onSelect = { selected -> sortField = selected },
                    allowNone = true,
                    extraOptions = sortOptions
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(labels.direction, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = direction == "asc",
                        onClick = { direction = "asc" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(labels.directionAsc)
                    }
                    SegmentedButton(
                        selected = direction == "desc",
                        onClick = { direction = "desc" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(labels.directionDesc)
                    }
                }

                if (classes.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(labels.filterClasses, style = MaterialTheme.typography.labelMedium)
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
        confirmText = stringResource(R.string.common_save),
        onConfirm = {
            onSave(
                ViewDraft(
                    name = name,
                    viewtype = viewtype,
                    columns = columnsField.ifBlank { null },
                    rows = rowsField.ifBlank { null },
                    filter = filterField.ifBlank { null },
                    sort = sortField.ifBlank { null },
                    direction = direction,
                    classes = selectedClasses.joinToString(",").ifBlank { null },
                    border = borderField.ifBlank { null }
                )
            )
        },
        confirmEnabled = name.isNotBlank(),
        dismissText = stringResource(R.string.common_cancel),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldDropdown(
    label: String,
    selectedId: String,
    fields: List<ViewFieldOption>,
    noneLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    allowNone: Boolean = false,
    extraOptions: List<Pair<String, String>> = emptyList()
) {
    val selectedName = fields.find { field -> field.id == selectedId }?.name
        ?: extraOptions.find { option -> option.first == selectedId }?.second
        ?: if (selectedId.isBlank() && allowNone) noneLabel else selectedId

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        MochiTextField(
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
                MochiDropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = {
                        onSelect("")
                        onExpandedChange(false)
                    },
                )
            }
            extraOptions.forEach { (value, displayLabel) ->
                MochiDropdownMenuItem(
                    text = { Text(displayLabel) },
                    onClick = {
                        onSelect(value)
                        onExpandedChange(false)
                    },
                )
            }
            fields.forEach { field ->
                MochiDropdownMenuItem(
                    text = { Text(field.name) },
                    onClick = {
                        onSelect(field.id)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}
