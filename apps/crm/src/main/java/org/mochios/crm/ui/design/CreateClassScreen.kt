// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.MochiTextField
import org.mochios.crm.R

/**
 * Form for adding a class to a CRM's design.
 *
 * @param onBack Called by the back button.
 * @param onCreated Called with the new class's id once it exists.
 * @param viewModel Screen's view model.
 */
@Composable
fun CreateClassScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateClassViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.createdClassId) {
        uiState.createdClassId?.let { classId ->
            onCreated(classId)
            viewModel.consumeCreatedClass()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.crm_classes_add_dialog_title),
        submitLabel = stringResource(R.string.crm_classes_create),
        submitEnabled = name.isNotBlank() && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = { viewModel.createClass(name) }
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(stringResource(R.string.crm_class_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
