// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.interests

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
import org.mochios.settings.api.Interest
import org.mochios.settings.api.InterestSearchResult
import org.mochios.settings.api.InterestsApi
import retrofit2.Response
import javax.inject.Inject

data class InterestsUiState(
    val isLoading: Boolean = true,
    val interests: List<Interest> = emptyList(),
    val summary: String = "",
    val isRegeneratingSummary: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<InterestSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: MochiError? = null,
    val error: MochiError? = null,
)

@HiltViewModel
class InterestsViewModel @Inject constructor(
    private val api: InterestsApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterestsUiState())
    val uiState: StateFlow<InterestsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.getInterests().bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    interests = data.interests,
                    summary = data.summary,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun setWeight(qid: String, weight: Int) {
        // Optimistic update so the slider is responsive
        val current = _uiState.value.interests
        val next = current.map { if (it.qid == qid) it.copy(weight = weight) else it }
        _uiState.value = _uiState.value.copy(interests = next)
        viewModelScope.launch {
            try {
                api.setInterest(qid, weight).bodyOrThrowUnit()
            } catch (e: Exception) {
                // Revert on failure
                _uiState.value = _uiState.value.copy(
                    interests = current,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun remove(qid: String) {
        viewModelScope.launch {
            try {
                api.removeInterest(qid).bodyOrThrowUnit()
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun add(result: InterestSearchResult) {
        viewModelScope.launch {
            try {
                // Web defaults to weight 50 on add via the search picker
                api.setInterest(qid = result.qid, weight = 50).bodyOrThrowUnit()
                _uiState.value = _uiState.value.copy(
                    searchQuery = "",
                    searchResults = emptyList(),
                )
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun regenerateSummary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegeneratingSummary = true, error = null)
            try {
                val resp = api.regenerateSummary().bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isRegeneratingSummary = false,
                    summary = resp.summary,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRegeneratingSummary = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, searchError = null)
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                val resp = api.searchInterests(trimmed).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = resp.results,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = emptyList(),
                    searchError = e.toMochiError(),
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }

    private fun Response<Unit>.bodyOrThrowUnit() {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
    }
}
