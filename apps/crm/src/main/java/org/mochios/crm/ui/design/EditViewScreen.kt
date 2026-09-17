// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.ViewForm
import org.mochios.android.ui.components.rememberViewFormState
import org.mochios.crm.R
import org.mochios.android.R as MochiR

/**
 * Form for changing one saved view of a CRM's design, with a live preview.
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
    val details = uiState.crmDetails
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
        title = stringResource(R.string.crm_views_edit_dialog_title),
        submitLabel = stringResource(MochiR.string.common_save),
        submitEnabled = view != null && formState.canSave && !uiState.isSaving,
        isBusy = uiState.isSaving,
        error = uiState.error,
        onBack = onBack,
        onSubmit = { viewModel.updateView(formState.toDraft()) }
    ) { padding ->
        if (details == null || view == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val loadError = uiState.loadError
                if (loadError != null) {
                    ErrorState(error = loadError, onRetry = { viewModel.loadDesign() })
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            CreateEntityForm(padding) {
                ViewForm(
                    state = formState,
                    classes = details.classes.toClassListItems(),
                    fields = details.fields.toViewFieldOptions(),
                    labels = viewListLabels(),
                    sortOptions = viewSortOptions(),
                    modifier = Modifier.fillMaxWidth(),
                    preview = { preview, modifier ->
                        DesignPreview(
                            crm = details,
                            view = preview?.toCrmView(),
                            modifier = modifier
                        )
                    }
                )
            }
        }
    }
}
