// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.projects.model.Project
import org.mochios.projects.repository.ProjectsRepository
import javax.inject.Inject

data class FindProjectsUiState(
    val searchQuery: String = "",
    val searchResults: List<Project> = emptyList(),
    val recommendations: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val subscribingId: String? = null
)

@HiltViewModel
class FindProjectsViewModel @Inject constructor(
    private val repository: ProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindProjectsUiState())
    val uiState: StateFlow<FindProjectsUiState> = _uiState.asStateFlow()

    init {
        loadRecommendations()
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            try {
                val recommendations = repository.getRecommendations()
                _uiState.value = _uiState.value.copy(recommendations = recommendations)
            } catch (_: Exception) { }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val recommendations = repository.getRecommendations()
                val query = _uiState.value.searchQuery.trim()
                if (query.isNotBlank()) {
                    val isUrl = query.startsWith("http://") || query.startsWith("https://")
                    val results = if (isUrl) listOf(repository.probe(query)) else repository.searchDirectory(query)
                    _uiState.value = _uiState.value.copy(
                        recommendations = recommendations,
                        searchResults = results,
                        isRefreshing = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        recommendations = recommendations,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, error = null)
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchResults = emptyList())
            try {
                val isUrl = query.startsWith("http://") || query.startsWith("https://")
                if (isUrl) {
                    val project = repository.probe(query)
                    _uiState.value = _uiState.value.copy(
                        searchResults = listOf(project),
                        isLoading = false
                    )
                } else {
                    val results = repository.searchDirectory(query)
                    _uiState.value = _uiState.value.copy(
                        searchResults = results,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun subscribe(project: Project, onSuccess: () -> Unit) {
        val id = project.fingerprint.ifEmpty { project.id }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(subscribingId = id)
            try {
                repository.subscribe(id, project.server)
                _uiState.value = _uiState.value.copy(subscribingId = null)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    subscribingId = null,
                    error = e.toMochiError()
                )
            }
        }
    }
}
