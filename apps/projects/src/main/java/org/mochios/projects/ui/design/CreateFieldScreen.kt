// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.FieldForm
import org.mochios.android.ui.components.FieldFormLabels
import org.mochios.android.ui.components.FieldTypeOption
import org.mochios.android.ui.components.rememberFieldFormState
import org.mochios.projects.R

/**
 * Form for adding a field to one class of a project's design.
 *
 * @param onBack Called by the back button.
 * @param onCreated Called once the field exists.
 * @param viewModel Screen's view model.
 */
@Composable
fun CreateFieldScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateFieldViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState = rememberFieldFormState()

    LaunchedEffect(uiState.created) {
        if (uiState.created) {
            onCreated()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.projects_field_add_field_dialog_title),
        submitLabel = stringResource(R.string.projects_classes_create),
        submitEnabled = formState.canSave && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = {
            viewModel.createField(
                formState.name,
                formState.fieldtype,
                formState.flags(),
                formState.multi()
            )
        }
    ) { padding ->
        CreateEntityForm(padding) {
            FieldForm(
                state = formState,
                types = fieldTypeOptions(),
                labels = FieldFormLabels(
                    name = stringResource(R.string.projects_field_name),
                    type = stringResource(R.string.projects_field_type),
                    required = stringResource(R.string.projects_field_required),
                    readonly = stringResource(R.string.projects_field_readonly),
                    sortable = stringResource(R.string.projects_field_sortable),
                    filterable = stringResource(R.string.projects_field_filterable),
                    multi = stringResource(R.string.projects_field_multi)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun fieldTypeOptions() = listOf(
    FieldTypeOption("text", stringResource(R.string.projects_field_type_text)),
    FieldTypeOption("number", stringResource(R.string.projects_field_type_number)),
    FieldTypeOption("enumerated", stringResource(R.string.projects_field_type_enumerated)),
    FieldTypeOption("user", stringResource(R.string.projects_field_type_user)),
    FieldTypeOption("date", stringResource(R.string.projects_field_type_date)),
    FieldTypeOption("checklist", stringResource(R.string.projects_field_type_checklist))
)
