// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiButtonTone
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.projects.R
import org.mochios.projects.model.FieldOption
import org.mochios.projects.model.ProjectField
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

@Composable
private fun fieldTypeLabel(type: String): String = when (type) {
    "text" -> stringResource(R.string.projects_field_type_text)
    "number" -> stringResource(R.string.projects_field_type_number)
    "enumerated" -> stringResource(R.string.projects_field_type_enumerated)
    "user" -> stringResource(R.string.projects_field_type_user)
    "date" -> stringResource(R.string.projects_field_type_date)
    "checklist" -> stringResource(R.string.projects_field_type_checklist)
    else -> type
}

private val POSITION_KEYS = listOf("", "header", "body", "sidebar")

@Composable
private fun positionLabel(value: String): String = when (value) {
    "header" -> stringResource(R.string.projects_field_position_header)
    "body" -> stringResource(R.string.projects_field_position_body)
    "sidebar" -> stringResource(R.string.projects_field_position_sidebar)
    else -> stringResource(R.string.projects_field_position_default)
}

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
    val projectDetails = uiState.projectDetails
    val field = projectDetails?.fields?.get(viewModel.classId)
        ?.find { candidate -> candidate.id == viewModel.fieldId }
    val options = projectDetails?.options?.get(viewModel.classId)?.get(viewModel.fieldId)
        ?: emptyList()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadProject()
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (field != null) {
                            stringResource(R.string.projects_field_label, field.name)
                        } else {
                            stringResource(R.string.projects_design_title)
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
                    ErrorState(error = error, onRetry = { viewModel.loadProject() })
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            var editName by remember(field.id) { mutableStateOf(field.name) }
            var isRequired by remember(field.id) { mutableStateOf(field.isRequired) }
            var isReadonly by remember(field.id) { mutableStateOf(field.isReadonly) }
            var isSortable by remember(field.id) { mutableStateOf(field.isSortable) }
            var isFilterable by remember(field.id) { mutableStateOf(field.isFilterable) }
            var showOnCard by remember(field.id) { mutableStateOf(field.showOnCard) }
            var isMulti by remember(field.id) { mutableStateOf(field.isMulti) }
            var editRows by remember(field.id) { mutableStateOf(if (field.rows > 0) field.rows.toString() else "") }
            var editPosition by remember(field.id) { mutableStateOf(field.position) }
            var editPattern by remember(field.id) { mutableStateOf(field.pattern) }
            var editMinlength by remember(field.id) { mutableStateOf(if (field.minlength > 0) field.minlength.toString() else "") }
            var editMaxlength by remember(field.id) { mutableStateOf(if (field.maxlength > 0) field.maxlength.toString() else "") }
            var showDeleteConfirm by remember { mutableStateOf(false) }
            var deletingOption by remember(field.id) { mutableStateOf<FieldOption?>(null) }

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
                    label = { Text(stringResource(R.string.projects_field_name)) },
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
                    label = { Text(stringResource(R.string.projects_field_type)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Flags
                Text(stringResource(R.string.projects_field_flags), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                FlagRow(stringResource(R.string.projects_field_required), isRequired) { isRequired = it }
                FlagRow(stringResource(R.string.projects_field_readonly), isReadonly) { isReadonly = it }
                FlagRow(stringResource(R.string.projects_field_sortable), isSortable) { isSortable = it }
                FlagRow(stringResource(R.string.projects_field_filterable), isFilterable) { isFilterable = it }
                FlagRow(stringResource(R.string.projects_field_show_on_card), showOnCard) { showOnCard = it }

                if (field.fieldtype == "enumerated") {
                    FlagRow(stringResource(R.string.projects_field_multi), isMulti) { isMulti = it }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Display position
                var posExpanded by remember { mutableStateOf(false) }
                Text(stringResource(R.string.projects_field_position), style = MaterialTheme.typography.labelMedium)
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
                Text(stringResource(R.string.projects_field_validation), style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                if (field.fieldtype == "text") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MochiTextField(
                            value = editMinlength,
                            onValueChange = { editMinlength = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.projects_field_min_length)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        MochiTextField(
                            value = editMaxlength,
                            onValueChange = { editMaxlength = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.projects_field_max_length)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MochiTextField(
                        value = editPattern,
                        onValueChange = { editPattern = it },
                        label = { Text(stringResource(R.string.projects_field_pattern)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                MochiTextField(
                    value = editRows,
                    onValueChange = { editRows = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.projects_field_rows)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Save button
                val flagsString = buildList {
                    if (isRequired) add("required")
                    if (isReadonly) add("readonly")
                    if (isSortable) add("sort")
                    if (isFilterable) add("filter")
                }.joinToString(",").ifEmpty { null }

                val rowsInt = editRows.toIntOrNull()
                val hasChanges = editName != field.name
                        || flagsString != field.flags.ifEmpty { null }
                        || isMulti != field.isMulti
                        || showOnCard != field.showOnCard
                        || (rowsInt ?: 0) != field.rows
                        || editPosition != field.position
                        || editPattern != field.pattern
                        || (editMinlength.toIntOrNull() ?: 0) != field.minlength
                        || (editMaxlength.toIntOrNull() ?: 0) != field.maxlength

                if (hasChanges) {
                    MochiButton(
                        onClick = {
                            viewModel.updateField(
                                name = editName.takeIf { it != field.name },
                                flags = flagsString,
                                multi = isMulti.takeIf { it != field.isMulti },
                                card = showOnCard.takeIf { it != field.showOnCard },
                                position = editPosition.takeIf { it != field.position },
                                rows = rowsInt?.takeIf { it != field.rows },
                                pattern = editPattern.takeIf { it != field.pattern },
                                minlength = (editMinlength.toIntOrNull() ?: 0).takeIf { it != field.minlength },
                                maxlength = (editMaxlength.toIntOrNull() ?: 0).takeIf { it != field.maxlength }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(MochiR.string.common_save))
                    }
                }

                // Options section (only for enumerated fields)
                if (field.fieldtype == "enumerated" || field.fieldtype == "enumerated") {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.projects_field_options), style = MaterialTheme.typography.titleSmall)
                        MochiIconButton(onClick = onAddOption) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.projects_field_add_option))
                        }
                    }

                    options.sortedBy { it.rank }.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditOption(option.id) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
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
                            text = stringResource(R.string.projects_field_no_options),
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
                    Text(stringResource(R.string.projects_field_delete))
                }
            }

            if (showDeleteConfirm) {
                MochiAlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.projects_field_delete_title),
                    text = stringResource(R.string.projects_field_delete_message, field.name),
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
                    title = stringResource(R.string.projects_option_delete_title),
                    text = stringResource(R.string.projects_option_delete_message, option.name),
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
