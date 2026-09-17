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
import org.mochios.projects.model.FieldOption
import org.mochios.projects.repository.ProjectsRepository

/**
 * Screen state for the option destination.
 *
 * @property option Option being edited, null when adding one or until loaded.
 * @property isLoading Whether the option being edited is being fetched.
 * @property loadError Failure fetching the option, shown in place of the form.
 * @property isSaving Whether the save request is in flight.
 * @property error Last save failure, shown above the submit button.
 * @property saved Set once the option is saved, so the screen can go back.
 */
data class OptionUiState(
    val option: FieldOption? = null,
    val isLoading: Boolean = false,
    val loadError: MochiError? = null,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val saved: Boolean = false
)

/**
 * Backs the option destination: adds an option to an enumerated field of
 * a project, or edits one. A board's "Add column" is the same thing.
 */
@HiltViewModel
class OptionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    /** Project the field belongs to, taken from the route. */
    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    /** Class the field belongs to, taken from the route. */
    val classId: String = savedStateHandle.get<String>("classId") ?: ""

    /** Field the option belongs to, taken from the route. */
    val fieldId: String = savedStateHandle.get<String>("fieldId") ?: ""

    /** Option being edited, blank when adding one. */
    val optionId: String = savedStateHandle.get<String>("optionId") ?: ""

    /** Whether this screen edits an existing option rather than adding one. */
    val isEditing: Boolean
        get() = optionId.isNotBlank()

    private val _uiState = MutableStateFlow(OptionUiState())

    /** State of the option form. */
    val uiState: StateFlow<OptionUiState> = _uiState.asStateFlow()

    init {
        if (isEditing) {
            loadOption()
        }
    }

    /** Fetches the option being edited out of the design. */
    fun loadOption() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val details = repository.getProjectInfo(projectId)
                val option = details.options[classId]
                    ?.get(fieldId)
                    ?.find { candidate -> candidate.id == optionId }
                _uiState.value = _uiState.value.copy(option = option, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = e.toMochiError()
                )
            }
        }
    }

    /**
     * Adds or updates the option and raises [OptionUiState.saved].
     *
     * @param name Option name.
     * @param colour Hex colour, or null for none.
     * @param icon Icon name, or null for none.
     */
    fun save(name: String, colour: String?, icon: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                if (isEditing) {
                    repository.updateOption(
                        projectId,
                        classId,
                        fieldId,
                        optionId,
                        name.trim(),
                        colour,
                        icon
                    )
                } else {
                    repository.createOption(projectId, classId, fieldId, name.trim(), colour, icon)
                }
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
