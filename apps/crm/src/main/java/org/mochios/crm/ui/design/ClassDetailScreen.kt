// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    cls: CrmClass,
    fields: List<CrmField>,
    hierarchy: List<String>,
    allClasses: List<CrmClass>,
    viewModel: DesignViewModel,
    onBack: () -> Unit,
    onFieldClick: (String) -> Unit
) {
    var editName by remember { mutableStateOf(cls.name) }
    var titleFieldId by remember { mutableStateOf(cls.title) }
    var titleExpanded by remember { mutableStateOf(false) }
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val crmDetails = uiState.crmDetails

    // Pick the first view that includes this class (or any view if none filter
    // by class) so the preview shows how this class's objects would lay out.
    val previewView = crmDetails?.views?.sortedBy { it.rank }?.firstOrNull { v ->
        v.classes.isEmpty() || cls.id in v.classes
    } ?: crmDetails?.views?.sortedBy { it.rank }?.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Back button
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(MochiR.string.common_back))
            }
            Text(
                text = stringResource(R.string.crm_class_label, cls.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (crmDetails != null) {
            Spacer(modifier = Modifier.height(8.dp))
            DesignPreview(
                crm = crmDetails,
                view = previewView,
                classFilter = cls
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name
        OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text(stringResource(R.string.crm_class_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (editName != cls.name && editName.isNotBlank()) {
            TextButton(onClick = {
                viewModel.updateClass(cls.id, name = editName)
            }) {
                Text(stringResource(R.string.crm_class_save_name))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title field selector
        Text(stringResource(R.string.crm_class_title_field), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.crm_class_title_field_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        val defaultReadableLabel = stringResource(R.string.crm_class_title_field_default)
        ExposedDropdownMenuBox(
            expanded = titleExpanded,
            onExpandedChange = { titleExpanded = it }
        ) {
            val titleFieldName = fields.find { it.id == titleFieldId }?.name ?: defaultReadableLabel
            OutlinedTextField(
                value = titleFieldName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = titleExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = titleExpanded,
                onDismissRequest = { titleExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(defaultReadableLabel) },
                    onClick = {
                        titleFieldId = ""
                        titleExpanded = false
                        viewModel.updateClass(cls.id, title = "")
                    }
                )
                fields.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(field.name) },
                        onClick = {
                            titleFieldId = field.id
                            titleExpanded = false
                            viewModel.updateClass(cls.id, title = field.id)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Hierarchy
        Text(stringResource(R.string.crm_class_parents), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        val otherClasses = allClasses.filter { it.id != cls.id }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            otherClasses.forEach { otherCls ->
                FilterChip(
                    selected = otherCls.id in hierarchy,
                    onClick = {
                        val newHierarchy = if (otherCls.id in hierarchy) {
                            hierarchy - otherCls.id
                        } else {
                            hierarchy + otherCls.id
                        }
                        viewModel.setHierarchy(cls.id, newHierarchy)
                    },
                    label = { Text(otherCls.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.crm_class_fields), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { showAddFieldDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.crm_class_add_field))
            }
        }

        val sortedFields = fields.sortedBy { it.rank }
        sortedFields.forEachIndexed { index, field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFieldClick(field.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reorder buttons
                if (sortedFields.size > 1) {
                    Column {
                        if (index > 0) {
                            IconButton(
                                onClick = {
                                    val newOrder = sortedFields.toMutableList()
                                    newOrder.removeAt(index)
                                    newOrder.add(index - 1, field)
                                    viewModel.reorderFields(cls.id, newOrder.joinToString(",") { it.id })
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.crm_class_move_up), modifier = Modifier.size(16.dp))
                            }
                        }
                        if (index < sortedFields.lastIndex) {
                            IconButton(
                                onClick = {
                                    val newOrder = sortedFields.toMutableList()
                                    newOrder.removeAt(index)
                                    newOrder.add(index + 1, field)
                                    viewModel.reorderFields(cls.id, newOrder.joinToString(",") { it.id })
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.crm_class_move_down), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = field.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = fieldTypeLabel(field.fieldtype),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Delete class
        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.crm_class_delete))
        }
    }

    if (showAddFieldDialog) {
        AddFieldDialog(
            onDismiss = { showAddFieldDialog = false },
            onAdd = { name, fieldtype, flags, multi ->
                viewModel.createField(cls.id, name, fieldtype, flags, multi)
                showAddFieldDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.crm_class_delete_title),
            message = stringResource(R.string.crm_class_delete_message, cls.name),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteClass(cls.id)
                onBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String?, Boolean?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var fieldtype by remember { mutableStateOf("text") }
    var typeExpanded by remember { mutableStateOf(false) }
    var isRequired by remember { mutableStateOf(false) }
    var isReadonly by remember { mutableStateOf(false) }
    var isSortable by remember { mutableStateOf(false) }
    var isFilterable by remember { mutableStateOf(false) }
    var isMulti by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crm_field_add_field_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.crm_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = fieldTypeLabel(fieldtype),
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
                                    fieldtype = value
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRequired, onCheckedChange = { isRequired = it })
                    Text(stringResource(R.string.crm_field_required))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isReadonly, onCheckedChange = { isReadonly = it })
                    Text(stringResource(R.string.crm_field_readonly))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSortable, onCheckedChange = { isSortable = it })
                    Text(stringResource(R.string.crm_field_sortable))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFilterable, onCheckedChange = { isFilterable = it })
                    Text(stringResource(R.string.crm_field_filterable))
                }
                if (fieldtype == "enumerated") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isMulti, onCheckedChange = { isMulti = it })
                        Text(stringResource(R.string.crm_field_multi))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val flags = buildList {
                        if (isRequired) add("required")
                        if (isReadonly) add("readonly")
                        if (isSortable) add("sort")
                        if (isFilterable) add("filter")
                    }.joinToString(",").ifEmpty { null }
                    onAdd(name, fieldtype, flags, if (fieldtype == "enumerated" && isMulti) true else null)
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.crm_classes_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        }
    )
}
