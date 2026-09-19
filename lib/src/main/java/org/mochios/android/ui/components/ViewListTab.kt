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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DragState
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.draggableItem
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.ui.components.dnd.isDragging
import org.mochios.android.ui.components.dnd.isTarget
import org.mochios.android.ui.components.dnd.rememberDragState
import org.mochios.android.ui.components.dnd.reorderActions
import org.mochios.android.ui.components.dnd.reorderedAgainst

/**
 * A saved board or list view, as the design screens list and edit one.
 *
 * @property id Identifier of the view.
 * @property name Label shown on the row and in the view form.
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
 * The edits a [ViewForm] collected, ready to send to a repository.
 * Blank entries arrive as `null` so an unset field clears server-side.
 *
 * @property name Name typed into the form.
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
 * @property deleteTitle Title of the delete confirmation.
 * @property deleteMessage Body of the delete confirmation, given the view name.
 * @property byField Row detail naming the grouping field, given the field name.
 * @property sortedBy Row detail naming the sort order, given the direction.
 * @property typeBoard Label of the board view type.
 * @property typeList Label of the list view type.
 * @property dragRow Content description of the drag handle.
 * @property moveUp Label of the move-up accessibility action.
 * @property moveDown Label of the move-down accessibility action.
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
    val deleteTitle: String,
    val deleteMessage: (viewName: String) -> String,
    val byField: (fieldName: String) -> String,
    val sortedBy: (direction: String) -> String,
    val typeBoard: String,
    val typeList: String,
    val dragRow: String,
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
 * List of a CRM's or project's saved views with reorder, edit and delete,
 * shared by the two design screens. Rows are sorted
 * by [ViewListItem.rank]; reordering reports the whole new order as a
 * comma-joined id list.
 *
 * @param views Views to list, in any order.
 * @param fields Every field of every class, keyed by class id.
 * @param labels Feature-specific wording.
 * @param sortOptions Pseudo-fields offered alongside the sortable fields, as
 *   id-to-label pairs — the two features offer different sets.
 * @param onAddView Called when the add button is tapped.
 * @param onEditView Called with the id of a view to edit.
 * @param onDeleteView Called with a view's id once deletion is confirmed.
 * @param onReorderViews Called with the new order as comma-joined view ids.
 * @param preview Optional feature-rendered preview of a view, shown above the
 *   list and inside the dialog; receives null when there is nothing to show.
 */
@Composable
fun ViewListTab(
    views: List<ViewListItem>,
    fields: Map<String, List<ViewFieldOption>>,
    labels: ViewListLabels,
    onAddView: () -> Unit,
    onEditView: (String) -> Unit,
    onDeleteView: (String) -> Unit,
    onReorderViews: (String) -> Unit,
    preview: (@Composable (ViewListItem?, Modifier) -> Unit)? = null
) {
    var deletingView by remember { mutableStateOf<ViewListItem?>(null) }
    // No "on" zone: a view row is only ever an insertion point, so the
    // whole row splits at its midpoint into Top and Bottom.
    val dragState = rememberDragState(onZoneFraction = 0f)

    val allFields = remember(fields) {
        fields.values.flatten().distinctBy { field -> field.id }
    }

    Scaffold(
        floatingActionButton = {
            MochiFab(onClick = onAddView) {
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
                val viewIds = sortedViews.map { view -> view.id }
                itemsIndexed(sortedViews, key = { _, view -> view.id }) { _, view ->
                    ViewRow(
                        view = view,
                        allFields = allFields,
                        labels = labels,
                        onEdit = { onEditView(view.id) },
                        onDelete = { deletingView = view },
                        dragState = dragState,
                        viewIds = viewIds,
                        onReorder = { order -> onReorderViews(order.joinToString(",")) }
                    )
                    HorizontalDivider()
                }
            }
        }
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


@Composable
private fun ViewRow(
    view: ViewListItem,
    allFields: List<ViewFieldOption>,
    labels: ViewListLabels,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragState: DragState,
    viewIds: List<String>,
    onReorder: (List<String>) -> Unit
) {
    val isDropTarget = dragState.isTarget(view.id) && dragState.draggingItemId != view.id
    val insertEdge = dragState.targetEdge
    val insertionColour = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dragState.isDragging(view.id)) 0.4f else 1f)
            .dropTarget(
                state = dragState,
                itemId = view.id,
                orientation = DropOrientation.Vertical,
                acceptedEdges = setOf(DragEdge.Top, DragEdge.Bottom),
                onDrop = { sourceId, edge ->
                    onReorder(viewIds.reorderedAgainst(sourceId, view.id, edge))
                }
            )
            .reorderActions(
                ids = viewIds,
                itemId = view.id,
                moveUpLabel = labels.moveUp,
                moveDownLabel = labels.moveDown,
                onReorder = onReorder
            )
            .drawBehind {
                if (!isDropTarget) return@drawBehind
                val y = if (insertEdge == DragEdge.Bottom) size.height else 0f
                drawLine(
                    color = insertionColour,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx()
                )
            }
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = labels.dragRow,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .draggableItem(state = dragState, itemId = view.id)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (view.viewtype == "board") Icons.Default.Dashboard
            else Icons.AutoMirrored.Filled.FormatListBulleted,
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
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Editable values of a view form, seeded from an existing view or the defaults
 * for a new one. Hold it with [rememberViewFormState] so a create screen can
 * read it from its submit button while [ViewForm] edits it.
 *
 * @param initialView View being edited, or null for a new view.
 */
@Stable
class ViewFormState(private val initialView: ViewListItem?) {

    /** Name typed into the form. */
    var name by mutableStateOf(initialView?.name ?: "")

    /** Either `board` or `list`. */
    var viewtype by mutableStateOf(initialView?.viewtype ?: "board")

    /** Field id driving board columns, blank for none. */
    var columns by mutableStateOf(initialView?.columns ?: "")

    /** Field id driving board swimlanes, blank for none. */
    var rows by mutableStateOf(initialView?.rows ?: "")

    /** Field id or pseudo-field to sort on, blank for none. */
    var sort by mutableStateOf(initialView?.sort ?: "")

    /** Either `asc` or `desc`. */
    var direction by mutableStateOf(initialView?.direction ?: "asc")

    /** Field id driving a card's border colour, blank for none. */
    var border by mutableStateOf(initialView?.border ?: "")

    /** Filter expression, blank for none. */
    var filter by mutableStateOf(initialView?.filter ?: "")

    /** Class ids the view is limited to. */
    var classes by mutableStateOf(initialView?.classes?.toSet() ?: emptySet())

    /** Whether the form holds enough to save. */
    val canSave: Boolean
        get() = name.isNotBlank()

    /** The form's values as a [ViewDraft], with blank entries sent as null. */
    fun toDraft() = ViewDraft(
        name = name,
        viewtype = viewtype,
        columns = columns.ifBlank { null },
        rows = rows.ifBlank { null },
        filter = filter.ifBlank { null },
        sort = sort.ifBlank { null },
        direction = direction,
        classes = classes.joinToString(",").ifBlank { null },
        border = border.ifBlank { null }
    )

    /** The view as it would look saved, for the live preview. */
    fun toPreview() = ViewListItem(
        id = initialView?.id ?: "preview",
        name = name.ifBlank { initialView?.name.orEmpty() },
        viewtype = viewtype,
        filter = initialView?.filter.orEmpty(),
        columns = columns,
        rows = rows,
        fields = initialView?.fields.orEmpty(),
        sort = sort,
        direction = direction,
        classes = classes.toList(),
        rank = initialView?.rank ?: 0,
        border = border
    )
}

/**
 * Remembers a [ViewFormState] for [initialView], reseeded when the view changes.
 *
 * @param initialView View being edited, or null for a new view.
 * @return The remembered form state.
 */
@Composable
fun rememberViewFormState(initialView: ViewListItem? = null): ViewFormState =
    remember(initialView) { ViewFormState(initialView) }

/**
 * The fields of a view: name, type, board grouping, filter, sort and class
 * limits, with an optional live preview on top. Does not scroll; the caller's
 * container does.
 *
 * @param state Values the form edits.
 * @param classes Classes offered as the class filter.
 * @param fields Every field of every class, keyed by class id.
 * @param labels Feature-specific wording.
 * @param sortOptions Pseudo-fields offered alongside the sortable fields, as
 *   id-to-label pairs.
 * @param modifier Modifier applied to the form's column.
 * @param preview Optional feature-rendered preview of the view being edited.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ViewForm(
    state: ViewFormState,
    classes: List<ClassListItem>,
    fields: Map<String, List<ViewFieldOption>>,
    labels: ViewListLabels,
    sortOptions: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    preview: (@Composable (ViewListItem?, Modifier) -> Unit)? = null
) {
    val allFields = remember(fields) {
        fields.values.flatten().distinctBy { field -> field.id }
    }
    val enumeratedFields = remember(allFields) {
        allFields.filter { field -> field.fieldtype == "enumerated" }
    }
    var columnsExpanded by remember { mutableStateOf(false) }
    var rowsExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var borderExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (preview != null) {
            preview(state.toPreview(), Modifier)
            Spacer(modifier = Modifier.height(12.dp))
        }
        MochiTextField(
            value = state.name,
            onValueChange = { value -> state.name = value },
            label = { Text(labels.nameLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(labels.typeLabel, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.viewtype == "board",
                onClick = { state.viewtype = "board" },
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
                selected = state.viewtype == "list",
                onClick = { state.viewtype = "list" },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                Text(labels.typeList)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.viewtype == "board") {
            FieldDropdown(
                label = labels.columnsField,
                selectedId = state.columns,
                fields = enumeratedFields,
                noneLabel = labels.none,
                expanded = columnsExpanded,
                onExpandedChange = { expanded -> columnsExpanded = expanded },
                onSelect = { selected -> state.columns = selected }
            )
            Spacer(modifier = Modifier.height(8.dp))

            FieldDropdown(
                label = labels.rowsField,
                selectedId = state.rows,
                fields = enumeratedFields,
                noneLabel = labels.none,
                expanded = rowsExpanded,
                onExpandedChange = { expanded -> rowsExpanded = expanded },
                onSelect = { selected -> state.rows = selected },
                allowNone = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            FieldDropdown(
                label = labels.borderField,
                selectedId = state.border,
                fields = enumeratedFields,
                noneLabel = labels.none,
                expanded = borderExpanded,
                onExpandedChange = { expanded -> borderExpanded = expanded },
                onSelect = { selected -> state.border = selected },
                allowNone = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        MochiTextField(
            value = state.filter,
            onValueChange = { value -> state.filter = value },
            label = { Text(labels.filter) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        FieldDropdown(
            label = labels.sortBy,
            selectedId = state.sort,
            fields = allFields.filter { field ->
                field.isSortable || field.fieldtype in listOf("number", "date", "text")
            },
            noneLabel = labels.none,
            expanded = sortExpanded,
            onExpandedChange = { expanded -> sortExpanded = expanded },
            onSelect = { selected -> state.sort = selected },
            allowNone = true,
            extraOptions = sortOptions
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(labels.direction, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.direction == "asc",
                onClick = { state.direction = "asc" },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(labels.directionAsc)
            }
            SegmentedButton(
                selected = state.direction == "desc",
                onClick = { state.direction = "desc" },
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
                        selected = cls.id in state.classes,
                        onClick = {
                            state.classes = if (cls.id in state.classes) {
                                state.classes - cls.id
                            } else {
                                state.classes + cls.id
                            }
                        },
                        label = { Text(cls.name) }
                    )
                }
            }
        }
    }
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
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
