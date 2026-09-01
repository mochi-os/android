// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.groups

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.MochiTextField
import org.mochios.people.R

@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val nameInvalid = name.isBlank()

    LaunchedEffect(uiState.createdGroupId) {
        uiState.createdGroupId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedGroup()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.people_groups_create),
        submitLabel = stringResource(R.string.people_groups_create),
        submitEnabled = !nameInvalid && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = { viewModel.createGroup(name, description) }
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(stringResource(R.string.people_group_name)) },
                singleLine = true,
                isError = name.isNotEmpty() && nameInvalid,
                supportingText = if (name.isNotEmpty() && nameInvalid) {
                    { Text(stringResource(R.string.people_group_name_required)) }
                } else {
                    null
                },
                enabled = !uiState.isCreating,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            MochiTextField(
                value = description,
                onValueChange = { value -> description = value },
                label = { Text(stringResource(R.string.people_group_description_optional)) },
                maxLines = 4,
                enabled = !uiState.isCreating,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
