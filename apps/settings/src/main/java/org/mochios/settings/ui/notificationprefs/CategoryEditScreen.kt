// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.settings.R
import org.mochios.settings.api.DestinationRow
import org.mochios.android.R as MochiR

/**
 * Create or edit one notification category: its name, whether it is the
 * default, and the destinations it delivers to.
 *
 * @param onBack leave the screen without writing anything.
 * @param onSaved leave the screen after the category was written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }
    LaunchedEffect(state.error) {
        val failure = state.error
        if (failure != null && state.isLoaded) {
            snackbar.showSnackbar(failure.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.notifprefs_new_category
                            } else {
                                R.string.notifprefs_edit_category
                            }
                        )
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.isLoaded) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        MochiButton(
                            onClick = { viewModel.save() },
                            enabled = state.name.isNotBlank() && !state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(stringResource(MochiR.string.common_save))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                !state.isLoaded && state.error != null -> ErrorState(
                    error = state.error!!,
                    onRetry = viewModel::load,
                )
                else -> CategoryForm(
                    state = state,
                    onNameChange = { name -> viewModel.setName(name) },
                    onDefaultChange = { isDefault -> viewModel.setDefault(isDefault) },
                    onToggleDestination = { row, checked ->
                        viewModel.toggleDestination(row, checked)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryForm(
    state: CategoryEditUiState,
    onNameChange: (String) -> Unit,
    onDefaultChange: (Boolean) -> Unit,
    onToggleDestination: (DestinationRow, Boolean) -> Unit,
) {
    val options = destinationOptions(state.available)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item("name") {
            MochiTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.notifprefs_name_label)) },
                singleLine = true,
            )
        }
        item("default") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDefaultChange(!state.isDefault) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MochiR.string.settings_theme_default),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.isDefault, onCheckedChange = onDefaultChange)
            }
        }
        item("destinations") {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notifprefs_destinations),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (options.isEmpty()) {
                Text(
                    text = stringResource(R.string.notifprefs_no_destinations),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        items(options, key = { option -> "${option.first.type}/${option.first.target}" }) { option ->
            val (row, label) = option
            val checked = (row.type to row.target) in state.selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleDestination(row, !checked) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = { value -> onToggleDestination(row, value) },
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
