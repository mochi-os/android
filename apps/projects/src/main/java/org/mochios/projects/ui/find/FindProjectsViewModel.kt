// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.find

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
import org.mochios.android.util.SEARCH_DEBOUNCE
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
    val subscribingId: String? = null,
    // Both handles (id and fingerprint) of every project the user already has,
    // so a directory hit can be matched on whichever one it carries.
    val subscribedIds: Set<String> = emptySet()
)

@HiltViewModel
class FindProjectsViewModel @Inject constructor(
    private val repository: ProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindProjectsUiState())
    val uiState: StateFlow<FindProjectsUiState> = _uiState.asStateFlow()

    // The pending debounced search; cancelled when a newer keystroke arrives so
    // only the latest query runs.
    private var searchJob: Job? = null

    init {
        loadRecommendations()
        loadSubscribed()
    }

    private fun loadSubscribed() {
        viewModelScope.launch {
            try {
                val projects = repository.listProjects()
                val handles = projects.flatMap { project ->
                    listOf(project.id, project.fingerprint).filter { handle -> handle.isNotEmpty() }
                }
                _uiState.value = _uiState.value.copy(
                    subscribedIds = _uiState.value.subscribedIds + handles
                )
            } catch (_: Exception) {
                // Non-critical: the rows just fall back to offering Subscribe.
            }
        }
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
        // Auto-search after a short pause once the user stops typing; cancel any
        // in-flight run so a stale response can't overwrite a newer one.
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            runSearch(trimmed)
        }
    }

    fun search() {
        // Explicit submit (keyboard Search): run at once, dropping the debounce.
        searchJob?.cancel()
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    private suspend fun runSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchResults = emptyList())
        try {
            val isUrl = query.startsWith("http://") || query.startsWith("https://")
            val results = if (isUrl) listOf(repository.probe(query)) else repository.searchDirectory(query)
            _uiState.value = _uiState.value.copy(
                searchResults = results,
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.toMochiError()
            )
        }
    }

    /**
     * Subscribes to [project], then hands [onSuccess] the id of the project the
     * server says it joined, so the caller can open it.
     */
    fun subscribe(project: Project, onSuccess: (String) -> Unit) {
        // The full entity id is what `-/subscribe` resolves; a bare directory
        // hit without one leaves the fingerprint as the only handle.
        val id = project.id.ifEmpty { project.fingerprint }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(subscribingId = id)
            try {
                val landingId = repository.subscribe(id, project.server ?: project.location)
                _uiState.value = _uiState.value.copy(
                    subscribingId = null,
                    subscribedIds = _uiState.value.subscribedIds + listOfNotNull(
                        project.id.takeIf { handle -> handle.isNotEmpty() },
                        project.fingerprint.takeIf { handle -> handle.isNotEmpty() },
                        landingId.takeIf { handle -> handle.isNotEmpty() }
                    )
                )
                // The app routes by fingerprint; a directory hit can hand us a
                // full entity id, so prefer what the server named.
                onSuccess(landingId.ifBlank { project.fingerprint.ifEmpty { id } })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    subscribingId = null,
                    error = e.toMochiError()
                )
            }
        }
    }

    /** Drop the error once the screen has shown it, so it isn't re-shown. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
