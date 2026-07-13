// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.crm.model.Crm
import org.mochios.crm.repository.CrmsRepository
import javax.inject.Inject

data class FindCrmsUiState(
    val searchQuery: String = "",
    val searchResults: List<Crm> = emptyList(),
    val recommendations: List<Crm> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val subscribingId: String? = null
)

@HiltViewModel
class FindCrmsViewModel @Inject constructor(
    private val repository: CrmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindCrmsUiState())
    val uiState: StateFlow<FindCrmsUiState> = _uiState.asStateFlow()

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
                    val crm = repository.probe(query)
                    _uiState.value = _uiState.value.copy(
                        searchResults = listOf(crm),
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

    fun subscribe(crm: Crm, onSuccess: () -> Unit) {
        // subscribe requires the full entity id (server rejects a fingerprint).
        val id = crm.id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(subscribingId = id)
            try {
                repository.subscribe(id, crm.server)
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
