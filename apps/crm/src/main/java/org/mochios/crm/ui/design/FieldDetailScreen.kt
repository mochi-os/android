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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.mochios.crm.R
import org.mochios.crm.model.FieldOption
import org.mochios.crm.model.CrmField
import org.mochios.crm.ui.`object`.ConfirmDeleteDialog
import org.mochios.android.R as MochiR

private val FIELD_TYPE_KEYS = listOf("text", "number", "enumerated", "user", "date", "checklist")

@Composable
private fun fieldTypeLabel(type: String): String = when (type) {
    "text" -> stringResource(R.string.crm_field_type_text)
    "number" -> stringResource(R.string.crm_field_type_number)
    "enumerated" -> stringResource(R.string.crm_field_type_enumerated)
    "user" -> stringResource(R.string.crm_field_type_user)
    "date" -> stringResource(R.string.crm_field_type_date)
    "checklist" -> stringResource(R.string.crm_field_type_checklist)
    else -> type
}

private val POSITION_KEYS = listOf("", "header", "body", "sidebar")

@Composable
private fun positionLabel(value: String): String = when (value) {
    "header" -> stringResource(R.string.crm_field_position_header)
    "body" -> stringResource(R.string.crm_field_position_body)
    "sidebar" -> stringResource(R.string.crm_field_position_sidebar)
    else -> stringResource(R.string.crm_field_position_default)
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
    classId: String,
    field: CrmField,
    options: List<FieldOption>,
    viewModel: DesignViewModel,
    onBack: () -> Unit
) {
    var editName by remember(field.id) { mutableStateOf(field.name) }
    var editFieldtype by remember(field.id) { mutableStateOf(field.fieldtype) }
    var typeExpanded by remember { mutableStateOf(false) }
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
    var showAddOptionDialog by remember { mutableStateOf(false) }
    var editingOption by remember { mutableStateOf<FieldOption?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
            }
            Text(
                text = stringResource(R.string.crm_field_label, field.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name
        OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text(stringResource(R.string.crm_field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Type
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it }
        ) {
            OutlinedTextField(
                value = fieldTypeLabel(editFieldtype),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.crm_field_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false }
            ) {
                FIELD_TYPE_KEYS.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(fieldTypeLabel(value)) },
                        onClick = {
                            editFieldtype = value
                            typeExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Flags
        Text(stringResource(R.string.crm_field_flags), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        FlagRow(stringResource(R.string.crm_field_required), isRequired) { isRequired = it }
        FlagRow(stringResource(R.string.crm_field_readonly), isReadonly) { isReadonly = it }
        FlagRow(stringResource(R.string.crm_field_sortable), isSortable) { isSortable = it }
        FlagRow(stringResource(R.string.crm_field_filterable), isFilterable) { isFilterable = it }
        FlagRow(stringResource(R.string.crm_field_show_on_card), showOnCard) { showOnCard = it }

        if (editFieldtype == "enumerated") {
            FlagRow(stringResource(R.string.crm_field_multi), isMulti) { isMulti = it }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Display position
        var posExpanded by remember { mutableStateOf(false) }
        Text(stringResource(R.string.crm_field_position), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = posExpanded,
            onExpandedChange = { posExpanded = it }
        ) {
            OutlinedTextField(
                value = positionLabel(editPosition),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = posExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = posExpanded,
                onDismissRequest = { posExpanded = false }
            ) {
                POSITION_KEYS.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(positionLabel(value)) },
                        onClick = {
                            editPosition = value
                            posExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Validation
        Text(stringResource(R.string.crm_field_validation), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (editFieldtype == "text") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editMinlength,
                    onValueChange = { editMinlength = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.crm_field_min_length)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = editMaxlength,
                    onValueChange = { editMaxlength = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.crm_field_max_length)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editPattern,
                onValueChange = { editPattern = it },
                label = { Text(stringResource(R.string.crm_field_pattern)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = editRows,
            onValueChange = { editRows = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.crm_field_rows)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Save changes button
        val flagsString = buildList {
            if (isRequired) add("required")
            if (isReadonly) add("readonly")
            if (isSortable) add("sort")
            if (isFilterable) add("filter")
        }.joinToString(",").ifEmpty { null }

        val rowsInt = editRows.toIntOrNull()
        val hasChanges = editName != field.name
                || editFieldtype != field.fieldtype
                || flagsString != field.flags.ifEmpty { null }
                || isMulti != field.isMulti
                || showOnCard != field.showOnCard
                || (rowsInt ?: 0) != field.rows
                || editPattern != field.pattern
                || (editMinlength.toIntOrNull() ?: 0) != field.minlength
                || (editMaxlength.toIntOrNull() ?: 0) != field.maxlength

        if (hasChanges) {
            TextButton(
                onClick = {
                    viewModel.updateField(
                        classId = classId,
                        fieldId = field.id,
                        name = editName.takeIf { it != field.name },
                        fieldtype = editFieldtype.takeIf { it != field.fieldtype },
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
                Text(stringResource(R.string.crm_field_save))
            }
        }

        // Options section (only for enumerated fields)
        if (field.fieldtype == "enumerated" || editFieldtype == "enumerated") {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.crm_field_options), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { showAddOptionDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.crm_field_add_option))
                }
            }

            options.sortedBy { it.rank }.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingOption = option }
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
                    IconButton(
                        onClick = { editingOption = option },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(MochiR.string.common_edit),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.deleteOption(classId, field.id, option.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(MochiR.string.common_delete),
                            tint = MaterialTheme.colorScheme.error,
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
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Delete field
        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.crm_field_delete))
        }
    }

    if (showAddOptionDialog) {
        OptionDialog(
            title = stringResource(R.string.crm_option_add),
            initialName = "",
            initialColour = "",
            initialIcon = "",
            onDismiss = { showAddOptionDialog = false },
            onSave = { name, colour, icon ->
                viewModel.createOption(classId, field.id, name, colour, icon)
                showAddOptionDialog = false
            }
        )
    }

    editingOption?.let { option ->
        OptionDialog(
            title = stringResource(R.string.crm_option_edit),
            initialName = option.name,
            initialColour = option.colour,
            initialIcon = option.icon,
            onDismiss = { editingOption = null },
            onSave = { name, colour, icon ->
                viewModel.updateOption(classId, field.id, option.id, name, colour, icon)
                editingOption = null
            }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.crm_field_delete_title),
            message = stringResource(R.string.crm_field_delete_message, field.name),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteField(classId, field.id)
                onBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
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

@Composable
private fun OptionDialog(
    title: String,
    initialName: String,
    initialColour: String,
    initialIcon: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, colour: String?, icon: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var colour by remember { mutableStateOf(initialColour) }
    var icon by remember { mutableStateOf(initialIcon) }

    val presetColours = listOf(
        "#ef4444", "#f97316", "#eab308", "#22c55e",
        "#06b6d4", "#3b82f6", "#8b5cf6", "#ec4899",
        "#6b7280", "#000000"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.crm_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.crm_option_color), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColours.take(5).forEach { hex ->
                        IconButton(
                            onClick = { colour = hex },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = hex,
                                tint = parseColor(hex),
                                modifier = if (colour == hex) Modifier.size(28.dp) else Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColours.drop(5).forEach { hex ->
                        IconButton(
                            onClick = { colour = hex },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = hex,
                                tint = parseColor(hex),
                                modifier = if (colour == hex) Modifier.size(28.dp) else Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.crm_option_icon), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    placeholder = { Text(stringResource(R.string.crm_option_icon_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, colour.ifBlank { null }, icon.ifBlank { null }) },
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
