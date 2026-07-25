// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.projectlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.projects.model.Project
import org.mochios.projects.model.Template
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

data class ProjectListUiState(
    val projects: List<Project> = emptyList(),
    val templates: List<Template> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val searchQuery: String = "",
    val showCreateDialog: Boolean = false,
    val showSearch: Boolean = false,
    // Set to the new project's id after a successful create so the screen can
    // navigate into it; cleared once consumed.
    val createdProjectId: String? = null
)

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val repository: ProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectListUiState())
    val uiState: StateFlow<ProjectListUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val projects = repository.listProjects()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    projects = projects,
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

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val projects = repository.listProjects()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    projects = projects,
                    isRefreshing = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleSearch() {
        val current = _uiState.value
        _uiState.value = current.copy(
            showSearch = !current.showSearch,
            searchQuery = if (current.showSearch) "" else current.searchQuery
        )
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
        viewModelScope.launch {
            try {
                val templates = repository.getTemplates()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(templates = templates)
            } catch (_: Exception) {
            }
        }
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    /** Clears the pending-navigation id once the screen has opened the project. */
    fun consumeCreatedProject() {
        _uiState.value = _uiState.value.copy(createdProjectId = null)
    }

    fun createProject(
        name: String,
        prefix: String,
        privacy: String,
        template: String?,
        backupJson: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            try {
                val project = repository.createProject(
                    name = name,
                    description = null,
                    prefix = prefix.ifBlank { null },
                    privacy = privacy,
                    template = template
                )
                val newProjectId = project.fingerprint.ifEmpty { project.id }
                if (!backupJson.isNullOrBlank()) {
                    repository.importDesign(newProjectId, data = backupJson)
                }
                // The create response only carries id/fingerprint, so reload the
                // full list before opening the project — this way the drawer and
                // list already contain it (with its name) when we navigate in.
                val projects = runCatching {
                    repository.listProjects().sortedWith(compareBy(NaturalCompare) { item -> item.name })
                }.getOrDefault(_uiState.value.projects)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    showCreateDialog = false,
                    projects = projects,
                    // Open the new project straight after creation. Only when the
                    // backend returned an id — navigating to a blank id would land
                    // on the empty-project placeholder.
                    createdProjectId = newProjectId.takeIf { id -> id.isNotBlank() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun filteredProjects(): List<Project> {
        val query = _uiState.value.searchQuery.lowercase()
        if (query.isBlank()) return _uiState.value.projects
        return _uiState.value.projects.filter {
            it.name.lowercase().contains(query) ||
                it.prefix.lowercase().contains(query) ||
                it.description.lowercase().contains(query)
        }
    }

    fun unsubscribe(projectId: String) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(projectId)
                _uiState.value = _uiState.value.copy(
                    projects = _uiState.value.projects.filterNot { it.id == projectId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
