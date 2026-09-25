// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.staff.R
import org.mochios.staff.model.Category

/**
 * Full-screen form that creates a category, or edits one when the route
 * carries its id. The parent dropdown excludes the category being edited.
 *
 * @param onBack Leaves without saving.
 * @param onSaved Called with the message the list should show once the save succeeded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFormScreen(
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    viewModel: CategoryFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isEdit = state.editing != null
    val form = state.form
    val canSubmit = !state.isLoading && state.loadError == null &&
        form.name.isNotBlank() && form.slug.isNotBlank() && !state.submitting

    LaunchedEffect(state.saved) {
        val saved = state.saved
        if (saved != null) {
            onSaved(saved)
        }
    }
    LaunchedEffect(state.missing) {
        if (state.missing) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) {
                            stringResource(R.string.staff_categories_dialog_edit_title)
                        } else {
                            stringResource(R.string.staff_categories_dialog_create_title)
                        },
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = onBack, enabled = !state.submitting) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    state.saveError?.let { error ->
                        Text(
                            text = error.userMessage().ifBlank {
                                stringResource(R.string.staff_categories_toast_save_failed)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    MochiButton(
                        onClick = viewModel::submit,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                state.submitting && isEdit ->
                                    stringResource(R.string.staff_categories_dialog_saving)
                                state.submitting ->
                                    stringResource(R.string.staff_categories_dialog_creating)
                                isEdit -> stringResource(R.string.staff_categories_dialog_save)
                                else -> stringResource(R.string.staff_categories_create)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val loadError = state.loadError
            when {
                state.isLoading -> LoadingState()
                loadError != null -> ErrorState(error = loadError, onRetry = viewModel::load)
                else -> CategoryFields(
                    form = form,
                    categories = state.categories,
                    editingId = state.editing?.id.orEmpty(),
                    isEdit = isEdit,
                    enabled = !state.submitting,
                    onFormChange = viewModel::setForm,
                )
            }
        }
    }
}

@Composable
private fun CategoryFields(
    form: CategoryForm,
    categories: List<Category>,
    editingId: String,
    isEdit: Boolean,
    enabled: Boolean,
    onFormChange: (CategoryForm) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MochiTextField(
            value = form.name,
            onValueChange = { value -> onFormChange(form.copy(name = value)) },
            label = { Text(stringResource(R.string.staff_categories_dialog_name)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        MochiTextField(
            value = form.slug,
            onValueChange = { value -> onFormChange(form.copy(slug = value)) },
            label = { Text(stringResource(R.string.staff_categories_dialog_slug)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        ParentDropdown(
            current = form.parent,
            categories = categories,
            excludeId = editingId,
            enabled = enabled,
            onChange = { parent -> onFormChange(form.copy(parent = parent)) },
        )
        MochiTextField(
            value = form.icon,
            onValueChange = { value -> onFormChange(form.copy(icon = value)) },
            label = { Text(stringResource(R.string.staff_categories_dialog_icon)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        MochiTextField(
            value = form.position,
            onValueChange = { value ->
                val position = value.filter { char -> char.isDigit() || char == '-' }
                onFormChange(form.copy(position = position))
            },
            label = { Text(stringResource(R.string.staff_categories_dialog_position)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabeledCheckbox(
                checked = form.digital,
                enabled = enabled,
                onCheckedChange = { checked -> onFormChange(form.copy(digital = checked)) },
                label = stringResource(R.string.staff_categories_dialog_digital),
            )
            LabeledCheckbox(
                checked = form.physical,
                enabled = enabled,
                onCheckedChange = { checked -> onFormChange(form.copy(physical = checked)) },
                label = stringResource(R.string.staff_categories_dialog_physical),
            )
        }
        if (isEdit) {
            LabeledCheckbox(
                checked = form.active,
                enabled = enabled,
                onCheckedChange = { checked -> onFormChange(form.copy(active = checked)) },
                label = stringResource(R.string.staff_categories_dialog_active),
            )
        }
    }
}

@Composable
private fun ParentDropdown(
    current: String,
    categories: List<Category>,
    excludeId: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.staff_categories_dialog_parent_none)
    val currentName = categories.firstOrNull { category -> category.id == current }?.name ?: none
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.staff_categories_dialog_parent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box {
            MochiOutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = currentName,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            MochiDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                MochiDropdownMenuItem(
                    text = { Text(none) },
                    onClick = {
                        expanded = false
                        onChange("")
                    },
                    selected = current == "",
                )
                categories
                    .filter { category -> category.id != excludeId }
                    .forEach { category ->
                        MochiDropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                expanded = false
                                onChange(category.id)
                            },
                            selected = current == category.id,
                        )
                    }
            }
        }
    }
}

@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
