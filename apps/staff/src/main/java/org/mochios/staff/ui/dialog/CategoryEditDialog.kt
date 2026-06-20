// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import org.mochios.staff.R
import org.mochios.staff.model.Category
import org.mochios.staff.ui.categories.CategoryDialogMode
import org.mochios.staff.ui.categories.CategoryForm

/**
 * Create/Edit dialog for staff Categories.
 *
 * Mirrors web's `<Dialog>` block in `apps/staff/web/src/features/categories/
 * categories-page.tsx`. Used by both flows:
 *
 *  - Create: dialog opens with an empty [CategoryForm]; the `active`
 *    checkbox is hidden because new categories default to active server-side.
 *  - Edit: dialog opens with [CategoryForm] pre-populated from the row; the
 *    `active` checkbox is visible.
 *
 * Submit is disabled until name + slug are both filled (mirrors web). The
 * parent dropdown lists every category except the one being edited (avoids
 * a category being its own parent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditDialog(
    mode: CategoryDialogMode,
    form: CategoryForm,
    categories: List<Category>,
    submitting: Boolean,
    onFormChange: (CategoryForm) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val title = when (mode) {
        is CategoryDialogMode.Create -> stringResource(R.string.staff_categories_dialog_create_title)
        is CategoryDialogMode.Edit -> stringResource(R.string.staff_categories_dialog_edit_title)
    }
    val isEdit = mode is CategoryDialogMode.Edit
    val editingId = if (mode is CategoryDialogMode.Edit) mode.category.id else ""
    val canSubmit = form.name.isNotBlank() && form.slug.isNotBlank() && !submitting

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = 0.dp, max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = { Text(stringResource(R.string.staff_categories_dialog_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.slug,
                    onValueChange = { onFormChange(form.copy(slug = it)) },
                    label = { Text(stringResource(R.string.staff_categories_dialog_slug)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ParentDropdown(
                    current = form.parent,
                    categories = categories,
                    excludeId = editingId,
                    onChange = { onFormChange(form.copy(parent = it)) },
                )
                OutlinedTextField(
                    value = form.icon,
                    onValueChange = { onFormChange(form.copy(icon = it)) },
                    label = { Text(stringResource(R.string.staff_categories_dialog_icon)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.position,
                    onValueChange = { v -> onFormChange(form.copy(position = v.filter { it.isDigit() || it == '-' })) },
                    label = { Text(stringResource(R.string.staff_categories_dialog_position)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LabeledCheckbox(
                        checked = form.digital,
                        onCheckedChange = { onFormChange(form.copy(digital = it)) },
                        label = stringResource(R.string.staff_categories_dialog_digital),
                    )
                    LabeledCheckbox(
                        checked = form.physical,
                        onCheckedChange = { onFormChange(form.copy(physical = it)) },
                        label = stringResource(R.string.staff_categories_dialog_physical),
                    )
                }
                if (isEdit) {
                    LabeledCheckbox(
                        checked = form.active,
                        onCheckedChange = { onFormChange(form.copy(active = it)) },
                        label = stringResource(R.string.staff_categories_dialog_active),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = canSubmit) {
                Text(
                    text = when {
                        submitting && isEdit -> stringResource(R.string.staff_categories_dialog_saving)
                        submitting && !isEdit -> stringResource(R.string.staff_categories_dialog_creating)
                        isEdit -> stringResource(R.string.staff_categories_dialog_save)
                        else -> stringResource(R.string.staff_categories_create)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.staff_categories_dialog_cancel))
            }
        },
    )
}

@Composable
private fun ParentDropdown(
    current: String,
    categories: List<Category>,
    excludeId: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = if (current.isEmpty()) {
        stringResource(R.string.staff_categories_dialog_parent_none)
    } else {
        categories.firstOrNull { it.id == current }?.name
            ?: stringResource(R.string.staff_categories_dialog_parent_none)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.staff_categories_dialog_parent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box {
            androidx.compose.material3.OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = currentName,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.staff_categories_dialog_parent_none)) },
                    onClick = {
                        expanded = false
                        onChange("")
                    },
                )
                val options = categories.filter { it.id != excludeId }
                options.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat.name) },
                        onClick = {
                            expanded = false
                            onChange(cat.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
