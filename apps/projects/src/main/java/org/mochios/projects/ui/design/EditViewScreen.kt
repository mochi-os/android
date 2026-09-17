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
import org.mochios.android.R as MochiR

/**
 * Form for changing one saved view of a project's design, with a live preview.
 *
 * @param onBack Called by the back button, and when the view no longer exists.
 * @param onSaved Called once the view is updated.
 * @param viewModel Screen's view model.
 */
@Composable
fun EditViewScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditViewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val details = uiState.projectDetails
    val view = details?.views
        ?.find { candidate -> candidate.id == viewModel.viewId }
        ?.toListItem()
    val formState = rememberViewFormState(view)

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            onSaved()
        }
    }

    LaunchedEffect(details, view) {
        if (details != null && view == null) {
            onBack()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.projects_views_edit_dialog_title),
        submitLabel = stringResource(MochiR.string.common_save),
        submitEnabled = view != null && formState.canSave && !uiState.isSaving,
        isBusy = uiState.isSaving,
        error = uiState.error,
        onBack = onBack,
        onSubmit = { viewModel.updateView(formState.toDraft()) }
    ) { padding ->
        FormLoadGate(
            padding = padding,
            data = details?.takeIf { view != null },
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
