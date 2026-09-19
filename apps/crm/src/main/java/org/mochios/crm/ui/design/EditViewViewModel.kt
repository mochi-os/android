// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.ViewDraft
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.repository.CrmsRepository

/**
 * Screen state for the edit-view destination.
 *
 * @property crmDetails Design holding the view, null until loaded.
 * @property isLoading Whether the design is being fetched.
 * @property loadError Failure fetching the design, shown in place of the form.
 * @property isSaving Whether the update request is in flight.
 * @property error Last save failure, shown above the save button.
 * @property saved Set once the view is updated, so the screen can go back.
 */
data class EditViewUiState(
    val crmDetails: CrmDetails? = null,
    val isLoading: Boolean = false,
    val loadError: MochiError? = null,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val saved: Boolean = false
)

/** Backs the edit-view destination: changes one saved view of a CRM's design. */
@HiltViewModel
class EditViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CrmsRepository
) : ViewModel() {

    /** CRM the view belongs to, taken from the route. */
    val crmId: String = savedStateHandle.get<String>("crmId") ?: ""

    /** View this screen edits, taken from the route. */
    val viewId: String = savedStateHandle.get<String>("viewId") ?: ""

    private val _uiState = MutableStateFlow(EditViewUiState())

    /** State of the edit form. */
    val uiState: StateFlow<EditViewUiState> = _uiState.asStateFlow()

    init {
        loadDesign()
    }

    /** Fetches the design holding the view, its classes and its fields. */
    fun loadDesign() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val details = repository.getCrmInfo(crmId)
                _uiState.value = _uiState.value.copy(crmDetails = details, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = e.toMochiError()
                )
            }
        }
    }

    /** Saves [draft] over the view and raises [EditViewUiState.saved]. */
    fun updateView(draft: ViewDraft) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                repository.updateView(
                    crmId,
                    viewId,
                    draft.name.trim(),
                    draft.viewtype,
                    draft.columns,
                    draft.rows,
                    draft.filter,
                    draft.sort,
                    draft.direction,
                    draft.classes,
                    draft.border
                )
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.toMochiError()
                )
            }
        }
    }
}
