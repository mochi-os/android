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
import org.mochios.projects.repository.ProjectsRepository

/**
 * Screen state for the create-class destination.
 *
 * @property isCreating Whether the create request is in flight.
 * @property error Last failure, shown above the submit button.
 * @property createdClassId Id of the class just created, until the screen consumes it;
 *   blank when the server did not return one.
 */
data class CreateClassUiState(
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdClassId: String? = null
)

/** Backs the create-class destination: adds a class to a project's design. */
@HiltViewModel
class CreateClassViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    /** Project the class is added to, taken from the route. */
    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(CreateClassUiState())

    /** State of the create form. */
    val uiState: StateFlow<CreateClassUiState> = _uiState.asStateFlow()

    /** Creates a class named [name] and raises [CreateClassUiState.createdClassId]. */
    fun createClass(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val created = repository.createClass(projectId, name.trim())
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdClassId = created.id
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    /** Clears [CreateClassUiState.createdClassId] once the screen has navigated. */
    fun consumeCreatedClass() {
        _uiState.value = _uiState.value.copy(createdClassId = null)
    }
}
