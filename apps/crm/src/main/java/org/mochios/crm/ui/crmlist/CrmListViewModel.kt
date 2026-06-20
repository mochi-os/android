// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.crmlist

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
import org.mochios.crm.model.Crm
import org.mochios.crm.model.Template
import org.mochios.crm.repository.CrmsRepository
import javax.inject.Inject

data class CrmListUiState(
    val crm: List<Crm> = emptyList(),
    val templates: List<Template> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val searchQuery: String = "",
    val showCreateDialog: Boolean = false,
    val showSearch: Boolean = false
)

@HiltViewModel
class CrmListViewModel @Inject constructor(
    private val repository: CrmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrmListUiState())
    val uiState: StateFlow<CrmListUiState> = _uiState.asStateFlow()

    init {
        loadCrms()
    }

    fun loadCrms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val crm = repository.listCrms()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    crm = crm,
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
                val crm = repository.listCrms()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    crm = crm,
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

    fun createCrm(name: String, description: String, prefix: String, privacy: String, template: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            try {
                repository.createCrm(
                    name = name,
                    description = description.ifBlank { null },
                    prefix = prefix.ifBlank { null },
                    privacy = privacy,
                    template = template
                )
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    showCreateDialog = false
                )
                loadCrms()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun filteredCrm(): List<Crm> {
        val query = _uiState.value.searchQuery.lowercase()
        if (query.isBlank()) return _uiState.value.crm
        return _uiState.value.crm.filter {
            it.name.lowercase().contains(query) ||
                it.prefix.lowercase().contains(query) ||
                it.description.lowercase().contains(query)
        }
    }

    fun unsubscribe(crmId: String) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(crmId)
                _uiState.value = _uiState.value.copy(
                    crm = _uiState.value.crm.filterNot { it.id == crmId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
