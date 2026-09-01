// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.list

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import org.mochios.wikis.R

@Composable
fun CreateWikiScreen(
    onBack: () -> Unit,
    onCreated: (wikiId: String, home: String) -> Unit,
    viewModel: CreateWikiViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    // Public by default: a wiki nobody can find is the rarer intent, and the
    // switch is right there for the person who wants it.
    var allowSearch by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.created) {
        uiState.created?.let { created ->
            onCreated(created.wikiId, created.home)
            viewModel.consumeCreatedWiki()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.wikis_create_title),
        submitLabel = stringResource(R.string.wikis_create_submit),
        submitEnabled = name.trim().isNotEmpty() && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = {
            viewModel.createWiki(name.trim(), if (allowSearch) "public" else "private")
        }
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(stringResource(R.string.wikis_create_name_label)) },
                singleLine = true,
                enabled = !uiState.isCreating,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            LabeledSwitchRow(
                label = stringResource(R.string.wikis_create_privacy_label),
                checked = allowSearch,
                onCheckedChange = { checked -> allowSearch = checked },
                enabled = !uiState.isCreating,
                labelStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
