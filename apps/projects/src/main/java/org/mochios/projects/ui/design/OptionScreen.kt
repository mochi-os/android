// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiTextField
import org.mochios.projects.R
import org.mochios.android.R as MochiR

private const val COLUMN_DEFAULT_COLOUR = "#3b82f6"

/**
 * Form for adding or editing an option of an enumerated field: name, colour
 * and icon. As a board's "Add column" it starts on a blue and hides the icon,
 * which boards don't draw.
 *
 * @param onBack Called by the back button, and when the edited option is gone.
 * @param onSaved Called once the option is saved.
 * @param isColumn Whether the screen adds a board column.
 * @param viewModel Screen's view model.
 */
@Composable
fun OptionScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    isColumn: Boolean = false,
    viewModel: OptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val option = uiState.option
    val isReady = !viewModel.isEditing || option != null
    var name by rememberSaveable { mutableStateOf("") }
    var colour by rememberSaveable { mutableStateOf(if (isColumn) COLUMN_DEFAULT_COLOUR else "") }
    var icon by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(option) {
        if (option != null && !seeded) {
            name = option.name
            colour = option.colour
            icon = option.icon
            seeded = true
        }
    }

    LaunchedEffect(uiState.isLoading, option, uiState.loadError) {
        val loaded = viewModel.isEditing && !uiState.isLoading && uiState.loadError == null
        if (loaded && option == null) {
            onBack()
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            onSaved()
        }
    }

    val title = when {
        isColumn -> stringResource(R.string.projects_board_add_column)
        viewModel.isEditing -> stringResource(R.string.projects_option_edit)
        else -> stringResource(R.string.projects_option_add)
    }

    CreateEntityScaffold(
        title = title,
        submitLabel = stringResource(
            if (isColumn) MochiR.string.common_add else MochiR.string.common_save
        ),
        submitEnabled = isReady && name.isNotBlank() && !uiState.isSaving,
        isBusy = uiState.isSaving,
        error = uiState.error,
        onBack = onBack,
        onSubmit = {
            val iconValue = if (isColumn) null else icon.ifBlank { null }
            viewModel.save(name, colour.ifBlank { null }, iconValue)
        }
    ) { padding ->
        if (!isReady) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val loadError = uiState.loadError
                if (loadError != null) {
                    ErrorState(error = loadError, onRetry = { viewModel.loadOption() })
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            CreateEntityForm(padding) {
                MochiTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(stringResource(R.string.projects_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.projects_option_color),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPicker(
                    hex = colour,
                    onHexChange = { hex -> colour = hex },
                )
                if (!isColumn) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.projects_option_icon),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MochiTextField(
                        value = icon,
                        onValueChange = { value -> icon = value },
                        placeholder = {
                            Text(stringResource(R.string.projects_option_icon_placeholder))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
