// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

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
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.repository.ProjectsRepository

/**
 * Screen state for the create-view destination.
 *
 * @property projectDetails Design the form picks classes and fields from, null until loaded.
 * @property isLoading Whether the design is being fetched.
 * @property loadError Failure fetching the design, shown in place of the form.
 * @property isCreating Whether the create request is in flight.
 * @property error Last create failure, shown above the submit button.
 * @property created Set once the view exists, so the screen can go back.
 */
data class CreateViewUiState(
    val projectDetails: ProjectDetails? = null,
    val isLoading: Boolean = false,
    val loadError: MochiError? = null,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val created: Boolean = false
)

/** Backs the create-view destination: adds a saved view to a project's design. */
@HiltViewModel
class CreateViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    /** Project the view is added to, taken from the route. */
    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(CreateViewUiState())

    /** State of the create form. */
    val uiState: StateFlow<CreateViewUiState> = _uiState.asStateFlow()

    init {
        loadDesign()
    }

    /** Fetches the design the form's pickers and preview draw on. */
    fun loadDesign() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val details = repository.getProjectInfo(projectId)
                _uiState.value = _uiState.value.copy(projectDetails = details, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = e.toMochiError()
                )
            }
        }
    }

    /** Creates a view from [draft] and raises [CreateViewUiState.created]. */
    fun createView(draft: ViewDraft) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                repository.createView(
                    projectId,
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
                _uiState.value = _uiState.value.copy(isCreating = false, created = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }
}
