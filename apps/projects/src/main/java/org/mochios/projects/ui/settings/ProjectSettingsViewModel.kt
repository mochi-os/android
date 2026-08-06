// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.model.AccessRule
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.projects.R
import org.mochios.projects.model.Group
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
    val prefix: String = "",
    val userSearchResults: List<Person> = emptyList(),
    val groups: List<Group> = emptyList(),
    val actionMessage: Int? = null
)

@HiltViewModel
class ProjectSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectsRepository
) : ViewModel() {

    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ProjectSettingsUiState())
    val uiState: StateFlow<ProjectSettingsUiState> = _uiState.asStateFlow()

    private var userSearchJob: Job? = null

    init {
        loadProject()
        loadAccess()
    }

    /**
     * Re-runs the initial load for the error state's retry button. Clears the
     * error first: the screen selects its error branch on `error != null`, so
     * leaving the previous one set would keep the retry on screen behind a
     * successful reload.
     */
    fun retry() {
        _uiState.value = _uiState.value.copy(error = null)
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
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    actionMessage = R.string.projects_settings_updated
                )
                loadProject()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    /**
     * The canonical entity id for endpoints that reject a fingerprint. The app
     * navigates by fingerprint, so [projectId] is usually one; prefer the id the
     * loaded project carries and fall back only while it has not arrived yet.
     */
    private fun entityId(): String {
        val project = _uiState.value.project ?: return projectId
        return project.id.ifEmpty { project.fingerprint.ifEmpty { projectId } }
    }

    fun deleteProject(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deleteProject(entityId())
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
                _uiState.value = _uiState.value.copy(
                    actionMessage = R.string.projects_settings_access_updated
                )
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun searchUsers(query: String) {
        // Each keystroke replaces the last: cancel the in-flight request so a
        // slow early response can't land after a newer one and overwrite it.
        userSearchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            return
        }
        userSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            try {
                val results = repository.searchUsers(query.trim())
                _uiState.value = _uiState.value.copy(userSearchResults = results)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            try {
                val groups = repository.getGroups()
                    .sortedWith(compareBy(NaturalCompare) { group -> group.name })
                _uiState.value = _uiState.value.copy(groups = groups)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(groups = emptyList())
            }
        }
    }

    fun unsubscribe(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(entityId())
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
                _uiState.value = _uiState.value.copy(
                    actionMessage = R.string.projects_settings_access_revoked
                )
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }
}
