// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.design

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.projects.model.ProjectDetails
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

/**
 * Screen state for the field-detail destination.
 *
 * @property projectDetails Design the field is read out of, null until loaded.
 * @property isLoading Whether the design is being fetched.
 * @property error Last failure, cleared by [FieldDetailViewModel.clearError].
 * @property deleted Set once the field is gone, so the screen can navigate away.
 */
data class FieldDetailUiState(
    val projectDetails: ProjectDetails? = null,
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val deleted: Boolean = false
)

/**
 * Backs the field-detail destination: one field of one class, with its flags,
 * display position, validation rules and enumerated options.
 */
@HiltViewModel
class FieldDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    /** Project the field belongs to, taken from the route. */
    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    /** Class the field belongs to, taken from the route. */
    val classId: String = savedStateHandle.get<String>("classId") ?: ""

    /** Field this screen edits, taken from the route. */
    val fieldId: String = savedStateHandle.get<String>("fieldId") ?: ""

    private val _uiState = MutableStateFlow(FieldDetailUiState(isLoading = true))
    val uiState: StateFlow<FieldDetailUiState> = _uiState.asStateFlow()

    /** Fetches the design. Called on every resume, like the class screen. */
    fun loadProject() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val details = repository.getProjectInfo(projectId)
                _uiState.value = _uiState.value.copy(
                    projectDetails = details,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    /** Saves the edited field. Null arguments leave that property unchanged. */
    fun updateField(
        name: String?,
        flags: String?,
        multi: Boolean?,
        card: Boolean?,
        position: String?,
        rows: Int?,
        pattern: String? = null,
        minlength: Int? = null,
        maxlength: Int? = null
    ) {
        viewModelScope.launch {
            try {
                repository.updateField(
                    projectId,
                    classId,
                    fieldId,
                    name,
                    flags,
                    multi,
                    card,
                    position,
                    rows,
                    pattern,
                    minlength,
                    maxlength
                )
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Deletes the field and raises [FieldDetailUiState.deleted]. The screen
     * waits for that flag rather than navigating straight away, which would
     * cancel this scope mid-request.
     */
    fun deleteField() {
        viewModelScope.launch {
            try {
                repository.deleteField(projectId, classId, fieldId)
                _uiState.value = _uiState.value.copy(deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Removes an option from an enumerated field. */
    fun deleteOption(optionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteOption(projectId, classId, fieldId, optionId)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Drops the current error once it has been shown. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
