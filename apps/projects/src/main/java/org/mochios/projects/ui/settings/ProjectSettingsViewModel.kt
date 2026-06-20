// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.settings

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
import org.mochios.android.model.AccessRule
import org.mochios.android.util.NaturalCompare
import org.mochios.projects.model.Person
import org.mochios.projects.model.Project
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

data class ProjectSettingsUiState(
    val project: Project? = null,
    val accessRules: List<AccessRule> = emptyList(),
    val people: List<Person> = emptyList(),
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val name: String = "",
    val description: String = "",
    val prefix: String = ""
)

@HiltViewModel
class ProjectSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ProjectSettingsUiState())
    val uiState: StateFlow<ProjectSettingsUiState> = _uiState.asStateFlow()

    init {
        loadProject()
        loadAccess()
    }

    private fun loadProject() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val details = repository.getProjectInfo(projectId)
                _uiState.value = _uiState.value.copy(
                    project = details.project,
                    name = details.project.name,
                    description = details.project.description,
                    prefix = details.project.prefix,
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

    private fun loadAccess() {
        viewModelScope.launch {
            try {
                val rules = repository.getAccess(projectId)
                    .sortedWith(compareBy(NaturalCompare) { it.name ?: it.subject })
                val people = repository.getPeople(projectId)
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    accessRules = rules,
                    people = people
                )
            } catch (_: Exception) { }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updatePrefix(prefix: String) {
        _uiState.value = _uiState.value.copy(prefix = prefix)
    }

    fun saveProject() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                val state = _uiState.value
                repository.updateProject(
                    projectId,
                    name = state.name,
                    description = state.description,
                    prefix = state.prefix
                )
                _uiState.value = _uiState.value.copy(isSaving = false)
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun deleteProject(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deleteProject(projectId)
                _uiState.value = _uiState.value.copy(isDeleting = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun setAccess(subject: String, level: String) {
        viewModelScope.launch {
            try {
                repository.setAccess(projectId, subject, level)
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun unsubscribe(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(projectId)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun revokeAccess(subject: String) {
        viewModelScope.launch {
            try {
                repository.revokeAccess(projectId, subject)
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
