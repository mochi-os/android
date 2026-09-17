// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import org.mochios.projects.R

private val FIELD_TYPE_KEYS = listOf("text", "number", "enumerated", "user", "date", "checklist")

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

/**
 * Form for adding a field to one class of a project's design.
 *
 * @param onBack Called by the back button.
 * @param onCreated Called once the field exists.
 * @param viewModel Screen's view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFieldScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateFieldViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var fieldtype by rememberSaveable { mutableStateOf("text") }
    var typeExpanded by remember { mutableStateOf(false) }
    var isRequired by rememberSaveable { mutableStateOf(false) }
    var isReadonly by rememberSaveable { mutableStateOf(false) }
    var isSortable by rememberSaveable { mutableStateOf(false) }
    var isFilterable by rememberSaveable { mutableStateOf(false) }
    var isMulti by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.created) {
        if (uiState.created) {
            onCreated()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.projects_field_add_field_dialog_title),
        submitLabel = stringResource(R.string.projects_classes_create),
        submitEnabled = name.isNotBlank() && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = {
            val flags = buildList {
                if (isRequired) {
                    add("required")
                }
                if (isReadonly) {
                    add("readonly")
                }
                if (isSortable) {
                    add("sort")
                }
                if (isFilterable) {
                    add("filter")
                }
            }.joinToString(",").ifEmpty { null }
            val multi = if (fieldtype == "enumerated" && isMulti) true else null
            viewModel.createField(name, fieldtype, flags, multi)
        }
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(stringResource(R.string.projects_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { expanded -> typeExpanded = expanded }
            ) {
                MochiTextField(
                    value = fieldTypeLabel(fieldtype),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.projects_field_type)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    FIELD_TYPE_KEYS.forEach { value ->
                        MochiDropdownMenuItem(
                            text = { Text(fieldTypeLabel(value)) },
                            onClick = {
                                fieldtype = value
                                typeExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            CheckboxRow(
                label = stringResource(R.string.projects_field_required),
                checked = isRequired,
                onCheckedChange = { checked -> isRequired = checked }
            )
            CheckboxRow(
                label = stringResource(R.string.projects_field_readonly),
                checked = isReadonly,
                onCheckedChange = { checked -> isReadonly = checked }
            )
            CheckboxRow(
                label = stringResource(R.string.projects_field_sortable),
                checked = isSortable,
                onCheckedChange = { checked -> isSortable = checked }
            )
            CheckboxRow(
                label = stringResource(R.string.projects_field_filterable),
                checked = isFilterable,
                onCheckedChange = { checked -> isFilterable = checked }
            )
            if (fieldtype == "enumerated") {
                CheckboxRow(
                    label = stringResource(R.string.projects_field_multi),
                    checked = isMulti,
                    onCheckedChange = { checked -> isMulti = checked }
                )
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
