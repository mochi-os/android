// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.FormLoadGate
import org.mochios.android.ui.components.OptionForm
import org.mochios.android.ui.components.OptionFormLabels
import org.mochios.android.ui.components.rememberOptionFormState
import org.mochios.crm.R
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
    val formState = rememberOptionFormState(if (isColumn) COLUMN_DEFAULT_COLOUR else "")

    LaunchedEffect(option) {
        if (option != null) {
            formState.seed(option.name, option.colour, option.icon)
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
        isColumn -> stringResource(R.string.crm_board_add_column)
        viewModel.isEditing -> stringResource(R.string.crm_option_edit)
        else -> stringResource(R.string.crm_option_add)
    }

    CreateEntityScaffold(
        title = title,
        submitLabel = stringResource(
            if (isColumn) MochiR.string.common_add else MochiR.string.common_save
        ),
        submitEnabled = isReady && formState.canSave && !uiState.isSaving,
        isBusy = uiState.isSaving,
        error = uiState.error,
        onBack = onBack,
        onSubmit = {
            val icon = if (isColumn) null else formState.icon.ifBlank { null }
            viewModel.save(formState.name, formState.colour.ifBlank { null }, icon)
        }
    ) { padding ->
        FormLoadGate(
            padding = padding,
            isReady = isReady,
            loadError = uiState.loadError,
            onRetry = { viewModel.loadOption() }
        ) {
            OptionForm(
                state = formState,
                labels = OptionFormLabels(
                    name = stringResource(R.string.crm_field_name),
                    colour = stringResource(R.string.crm_option_color),
                    icon = stringResource(R.string.crm_option_icon),
                    iconPlaceholder = stringResource(R.string.crm_option_icon_placeholder)
                ),
                modifier = Modifier.fillMaxWidth(),
                showIcon = !isColumn
            )
        }
    }
}
