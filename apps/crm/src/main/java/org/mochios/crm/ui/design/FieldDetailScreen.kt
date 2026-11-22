// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiButtonTone
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.fieldTypeLabel
import org.mochios.android.ui.components.dnd.DragEdge
import org.mochios.android.ui.components.dnd.DropOrientation
import org.mochios.android.ui.components.dnd.draggableItem
import org.mochios.android.ui.components.dnd.dropTarget
import org.mochios.android.ui.components.dnd.isDragging
import org.mochios.android.ui.components.dnd.isTarget
import org.mochios.android.ui.components.dnd.rememberDragState
import org.mochios.android.ui.components.dnd.reorderActions
import org.mochios.android.ui.components.dnd.reorderedAgainst
import org.mochios.crm.R
import org.mochios.crm.api.UpdateFieldRequest
import org.mochios.crm.model.FieldOption
import org.mochios.android.R as MochiR
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.mochios.android.ui.components.ErrorState

private val POSITION_KEYS = listOf("", "header", "body", "sidebar")

@Composable
private fun positionLabel(value: String): String = when (value) {
    "header" -> stringResource(R.string.crm_field_position_header)
    "body" -> stringResource(R.string.crm_field_position_body)
    "sidebar" -> stringResource(R.string.crm_field_position_sidebar)
    else -> stringResource(R.string.crm_field_position_default)
}

/** The flags the form edits, as the server spells them. */
private val EDITED_FLAGS = listOf("required", "readonly", "sort", "filter")

/**
 * The flags to store: [stored] with each of [EDITED_FLAGS] on as [on] says,
 * in that order, and any other flag kept after them. None at all is "".
 */
internal fun fieldFlags(stored: String, on: Set<String>): String {
    val kept = stored.split(",").map { flag -> flag.trim() }
        .filter { flag -> flag.isNotEmpty() && flag !in EDITED_FLAGS }
    return (EDITED_FLAGS.filter { flag -> flag in on } + kept).joinToString(",")
}

/** [flags] as a set, for comparing two spellings of the same flags. */
private fun flagSet(flags: String): Set<String> =
    flags.split(",").map { flag -> flag.trim() }.filter { flag -> flag.isNotEmpty() }.toSet()

/** A field's rows, a blank or zero meaning the single row the web shows. */
private fun rowsOrDefault(rows: Int?): Int = rows?.takeIf { count -> count > 0 } ?: 1

private fun parseColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        Color(android.graphics.Color.parseColor("#$clean"))
    } catch (_: Exception) {
        Color.Gray
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldDetailScreen(
    onBack: () -> Unit,
    onAddOption: () -> Unit,
    onEditOption: (String) -> Unit,
    viewModel: FieldDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val crmDetails = uiState.crmDetails
    val field = crmDetails?.fields?.get(viewModel.classId)
        ?.find { candidate -> candidate.id == viewModel.fieldId }
    val options = crmDetails?.options?.get(viewModel.classId)?.get(viewModel.fieldId)
        ?: emptyList()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadCrm()
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            onBack()
        }
    }

    // A change the server refused, over the loaded form. A failed load is the
    // error state's below, which clearing here would take away.
    val snackbarHostState = remember { SnackbarHostState() }
    val loaded = field != null
    LaunchedEffect(uiState.error, loaded) {
        val error = uiState.error
        if (error != null && loaded) {
            snackbarHostState.showSnackbar(error.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (field != null) {
                            stringResource(R.string.crm_field_label, field.name)
                        } else {
                            stringResource(R.string.crm_design_title)
                        }
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (field == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val error = uiState.error
                if (error != null) {
                    ErrorState(error = error, onRetry = { viewModel.loadCrm() })
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Seeded from the field as the server has it, and again whenever
            // that changes, so a save's reload shows what was stored.
            var editName by remember(field) { mutableStateOf(field.name) }
            var isRequired by remember(field) { mutableStateOf(field.isRequired) }
            var isReadonly by remember(field) { mutableStateOf(field.isReadonly) }
            var isSortable by remember(field) { mutableStateOf(field.isSortable) }
            var isFilterable by remember(field) { mutableStateOf(field.isFilterable) }
            var showOnCard by remember(field) { mutableStateOf(field.showOnCard) }
            var editRows by remember(field) { mutableStateOf(if (field.rows > 0) field.rows.toString() else "") }
            var editPosition by remember(field) { mutableStateOf(field.position) }
            var editPattern by remember(field) { mutableStateOf(field.pattern) }
            var editMinlength by remember(field) { mutableStateOf(if (field.minlength > 0) field.minlength.toString() else "") }
            var editMaxlength by remember(field) { mutableStateOf(if (field.maxlength > 0) field.maxlength.toString() else "") }
            var showDeleteConfirm by remember { mutableStateOf(false) }
            var deletingOption by remember(field.id) { mutableStateOf<FieldOption?>(null) }
            // No "on" zone: an option row is only ever an insertion point, so the
            // whole row splits at its midpoint into Top and Bottom.
            val dragState = rememberDragState(onZoneFraction = 0f)

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Name
                MochiTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.crm_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Type: fixed once the field exists. The server never rereads it on
                // update, so an editable picker here could only pretend.
                MochiTextField(
                    value = fieldTypeLabel(field.fieldtype),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.crm_field_type)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Flags
                Text(stringResource(R.string.crm_field_flags), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                FlagRow(stringResource(R.string.crm_field_required), isRequired) { isRequired = it }
                FlagRow(stringResource(R.string.crm_field_readonly), isReadonly) { isReadonly = it }
                FlagRow(stringResource(R.string.crm_field_sortable), isSortable) { isSortable = it }
                FlagRow(stringResource(R.string.crm_field_filterable), isFilterable) { isFilterable = it }
                FlagRow(stringResource(R.string.crm_field_show_on_card), showOnCard) { showOnCard = it }

                Spacer(modifier = Modifier.height(12.dp))

                // Display position
                var posExpanded by remember { mutableStateOf(false) }
                Text(stringResource(R.string.crm_field_position), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = posExpanded,
                    onExpandedChange = { posExpanded = it }
                ) {
                    MochiTextField(
                        value = positionLabel(editPosition),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = posExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = posExpanded,
                        onDismissRequest = { posExpanded = false }
                    ) {
                        POSITION_KEYS.forEach { value ->
                            MochiDropdownMenuItem(
                                text = { Text(positionLabel(value)) },
                                onClick = {
                                    editPosition = value
                                    posExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Validation
                Text(stringResource(R.string.crm_field_validation), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                if (field.fieldtype == "text") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MochiTextField(
                            value = editMinlength,
                            onValueChange = { editMinlength = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.crm_field_min_length)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        MochiTextField(
                            value = editMaxlength,
                            onValueChange = { editMaxlength = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.crm_field_max_length)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MochiTextField(
                        value = editPattern,
                        onValueChange = { editPattern = it },
                        label = { Text(stringResource(R.string.crm_field_pattern)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                MochiTextField(
                    value = editRows,
                    onValueChange = { editRows = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.crm_field_rows)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Save button. Flags always go, "" when none is on, so switching
                // the last one off reaches the server.
                val flagsString = fieldFlags(
                    field.flags,
                    buildSet {
                        if (isRequired) add("required")
                        if (isReadonly) add("readonly")
                        if (isSortable) add("sort")
                        if (isFilterable) add("filter")
                    }
                )
                val rowsValue = rowsOrDefault(editRows.toIntOrNull())
                val hasChanges = editName != field.name
                        || flagSet(flagsString) != flagSet(field.flags)
                        || showOnCard != field.showOnCard
                        || editPosition != field.position
                        || rowsValue != rowsOrDefault(field.rows)
                        || editPattern != field.pattern
                        || (editMinlength.toIntOrNull() ?: 0) != field.minlength
                        || (editMaxlength.toIntOrNull() ?: 0) != field.maxlength

                if (hasChanges) {
                    MochiButton(
                        onClick = {
                            viewModel.updateField(
                                UpdateFieldRequest(
                                    name = editName.takeIf { it != field.name },
                                    flags = flagsString,
                                    card =showOnCard.takeIf { it != field.showOnCard },
                                    position = editPosition.takeIf { it != field.position },
                                    rows = rowsValue.takeIf { it != rowsOrDefault(field.rows) },
                                    pattern = editPattern.takeIf { it != field.pattern },
                                    minlength = (editMinlength.toIntOrNull() ?: 0).takeIf { it != field.minlength },
                                    maxlength = (editMaxlength.toIntOrNull() ?: 0).takeIf { it != field.maxlength }
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(MochiR.string.common_save))
                    }
                }

                // Options section (only for enumerated fields)
                if (field.fieldtype == "enumerated") {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.crm_field_options), style = MaterialTheme.typography.titleSmall)
                        MochiIconButton(onClick = onAddOption) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.crm_field_add_option))
                        }
                    }

                    val sortedOptions = remember(options) {
                        options.sortedBy { option -> option.rank }
                    }
                    val optionIds = remember(sortedOptions) {
                        sortedOptions.map { entry -> entry.id }
                    }
                    val moveUpLabel = stringResource(R.string.crm_class_move_up)
                    val moveDownLabel = stringResource(R.string.crm_class_move_down)
                    val dragHint = stringResource(R.string.crm_drag_row)
                    val insertionColour = MaterialTheme.colorScheme.primary
                    sortedOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = if (dragState.isDragging(option.id)) 0.4f else 1f
                                }
                                .dropTarget(
                                    state = dragState,
                                    itemId = option.id,
                                    orientation = DropOrientation.Vertical,
                                    acceptedEdges = setOf(DragEdge.Top, DragEdge.Bottom),
                                    onDrop = { sourceId, edge ->
                                        viewModel.reorderOptions(
                                            optionIds.reorderedAgainst(sourceId, option.id, edge)
                                        )
                                    }
                                )
                                .reorderActions(
                                    ids = optionIds,
                                    itemId = option.id,
                                    moveUpLabel = moveUpLabel,
                                    moveDownLabel = moveDownLabel,
                                    onReorder = { order -> viewModel.reorderOptions(order) }
                                )
                                .drawBehind {
                                    val dropping = dragState.isTarget(option.id) &&
                                        dragState.draggingItemId != option.id
                                    if (!dropping) return@drawBehind
                                    val bottom = dragState.targetEdge == DragEdge.Bottom
                                    val y = if (bottom) size.height else 0f
                                    drawLine(
                                        color = insertionColour,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                                .clickable { onEditOption(option.id) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = dragHint,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .draggableItem(state = dragState, itemId = option.id)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (option.colour.isNotBlank()) {
                                Icon(
                                    Icons.Default.Circle,
                                    contentDescription = null,
                                    tint = parseColor(option.colour),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            MochiIconButton(
                                onClick = { onEditOption(option.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(MochiR.string.common_edit),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            MochiIconButton(
                                onClick = { deletingOption = option },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(MochiR.string.common_delete),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    if (options.isEmpty()) {
                        Text(
                            text = stringResource(R.string.crm_field_no_options),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Delete field
                MochiOutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    tone = MochiButtonTone.Neutral,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.crm_field_delete))
                }
            }

            if (showDeleteConfirm) {
                MochiAlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.crm_field_delete_title),
                    text = stringResource(R.string.crm_field_delete_message, field.name),
                    confirmText = stringResource(MochiR.string.common_delete),
                    onConfirm = {
                        showDeleteConfirm = false
                        viewModel.deleteField()
                    },
                    destructive = true,
                    dismissText = stringResource(MochiR.string.common_cancel),
                )
            }

            deletingOption?.let { option ->
                MochiAlertDialog(
                    onDismissRequest = { deletingOption = null },
                    title = stringResource(R.string.crm_option_delete_title),
                    text = stringResource(R.string.crm_option_delete_message, option.name),
                    confirmText = stringResource(MochiR.string.common_delete),
                    onConfirm = {
                        deletingOption = null
                        viewModel.deleteOption(option.id)
                    },
                    destructive = true,
                    dismissText = stringResource(MochiR.string.common_cancel),
                )
            }
        }
    }
}

@Composable
private fun FlagRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

