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
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.FormLoadGate
import org.mochios.android.ui.components.ViewForm
import org.mochios.android.ui.components.rememberViewFormState
import org.mochios.projects.R

/**
 * Form for adding a saved view to a project's design, with a live preview.
 *
 * @param onBack Called by the back button.
 * @param onCreated Called once the view exists.
 * @param viewModel Screen's view model.
 */
@Composable
fun CreateViewScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateViewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState = rememberViewFormState()
    val details = uiState.projectDetails

    LaunchedEffect(uiState.created) {
        if (uiState.created) {
            onCreated()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.projects_views_add_dialog_title),
        submitLabel = stringResource(R.string.projects_classes_create),
        submitEnabled = details != null && formState.canSave && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = { viewModel.createView(formState.toDraft()) }
    ) { padding ->
        FormLoadGate(
            padding = padding,
            data = details,
            loadError = uiState.loadError,
            onRetry = { viewModel.loadDesign() }
        ) { loaded ->
            ViewForm(
                state = formState,
                classes = loaded.classes.toClassListItems(),
                fields = loaded.fields.toViewFieldOptions(),
                labels = viewListLabels(),
                sortOptions = viewSortOptions(),
                modifier = Modifier.fillMaxWidth(),
                preview = { preview, previewModifier ->
                    DesignPreview(
                        project = loaded,
                        view = preview?.toProjectView(),
                        modifier = previewModifier
                    )
                }
            )
        }
    }
}
