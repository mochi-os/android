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
import org.mochios.projects.util.hierarchyParameter
import javax.inject.Inject

/**
 * Screen state for the class-detail destination.
 *
 * @property projectDetails Design the class is read out of, null until loaded.
 * @property isLoading Whether the design is being fetched.
 * @property error Last failure, cleared by [ClassDetailViewModel.clearError].
 * @property deleted Set once the class is gone, so the screen can navigate away.
 */
data class ClassDetailUiState(
    val projectDetails: ProjectDetails? = null,
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val deleted: Boolean = false
)

/**
 * Backs the class-detail destination: one class of a project's design, with
 * its name, title field, merge-request mode, parents and fields.
 */
@HiltViewModel
class ClassDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    /** Project the class belongs to, taken from the route. */
    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    /** Class this screen edits, taken from the route. */
    val classId: String = savedStateHandle.get<String>("classId") ?: ""

    private val _uiState = MutableStateFlow(ClassDetailUiState(isLoading = true))
    val uiState: StateFlow<ClassDetailUiState> = _uiState.asStateFlow()

    /**
     * Fetches the design. Called on every resume, so edits made further down
     * the back stack are picked up on the way back.
     */
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

    /** Renames the class, or changes its title field or merge-request mode. */
    fun updateClass(name: String? = null, title: String? = null, requests: String? = null) {
        viewModelScope.launch {
            try {
                repository.updateClass(projectId, classId, name, title, requests)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Deletes the class and raises [ClassDetailUiState.deleted]. The screen
     * waits for that flag rather than navigating straight away, which would
     * cancel this scope mid-request.
     */
    fun deleteClass() {
        viewModelScope.launch {
            try {
                repository.deleteClass(projectId, classId)
                _uiState.value = _uiState.value.copy(deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Replaces the classes this one may be created under. */
    fun setHierarchy(parents: List<String>) {
        viewModelScope.launch {
            try {
                repository.setHierarchy(projectId, classId, hierarchyParameter(parents))
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Applies a new field order, as a comma-separated list of field ids. */
    fun reorderFields(order: String) {
        viewModelScope.launch {
            try {
                repository.reorderFields(projectId, classId, order)
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
