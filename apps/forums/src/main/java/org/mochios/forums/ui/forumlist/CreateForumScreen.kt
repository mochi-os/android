// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forumlist

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
import org.mochios.android.ui.components.LabeledSwitchRow
import org.mochios.android.ui.components.MochiTextField
import org.mochios.forums.R

@Composable
fun CreateForumScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateForumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var allowSearch by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.createdForumId) {
        uiState.createdForumId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedForum()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.forums_create_title),
        submitLabel = stringResource(R.string.forums_create_action),
        submitEnabled = name.isNotBlank() && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = {
            viewModel.createForum(name, if (allowSearch) "public" else "private")
        }
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(stringResource(R.string.forums_create_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            LabeledSwitchRow(
                label = stringResource(R.string.forums_create_allow_search),
                checked = allowSearch,
                onCheckedChange = { checked -> allowSearch = checked }
            )
        }
    }
}
