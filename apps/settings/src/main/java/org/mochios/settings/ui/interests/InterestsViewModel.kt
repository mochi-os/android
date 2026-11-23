// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.interests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.settings.api.Interest
import org.mochios.settings.api.InterestSearchResult
import org.mochios.settings.api.InterestsApi
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

    /**
     * Read the interests. A [quiet] read, after a change, leaves the list on
     * screen while it is read again rather than blanking it to a spinner.
     */
    fun refresh(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.getInterests().unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    interests = data.interests,
                    summary = data.summary,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Save a weight the slider was let go at: the row shows it at once, and
     * goes back to the weight it had if the server refuses, unless a later
     * save has moved it since. The rest of the list is left as it is.
     */
    fun setWeight(qid: String, weight: Int) {
        val before = _uiState.value.interests.firstOrNull { it.qid == qid }?.weight ?: return
        if (before == weight) return
        setWeightShown(qid, weight)
        viewModelScope.launch {
            try {
                api.setInterest(qid, weight).unwrapEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.interests.any { it.qid == qid && it.weight == weight }) setWeightShown(qid, before)
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun setWeightShown(qid: String, weight: Int) {
        _uiState.value = _uiState.value.copy(
            interests = _uiState.value.interests.map { if (it.qid == qid) it.copy(weight = weight) else it },
        )
    }

    /** The row goes on the tap and comes back, with the error, if the server
     *  refuses; the list reloads once the server has it. */
    fun remove(qid: String) {
        val list = _uiState.value.interests
        val index = list.indexOfFirst { it.qid == qid }
        val removed = list.getOrNull(index)
        if (removed != null) {
            _uiState.value = _uiState.value.copy(interests = list.filter { it.qid != qid })
        }
        viewModelScope.launch {
            try {
                api.removeInterest(qid).unwrapEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Back where it stood, unless a reload since has brought it back.
                val now = _uiState.value.interests
                val restored = if (removed == null || now.any { it.qid == qid }) now
                else now.toMutableList().apply { add(index.coerceAtMost(size), removed) }
                _uiState.value = _uiState.value.copy(interests = restored, error = e.toMochiError())
                return@launch
            }
            refresh(quiet = true)
        }
    }

    fun add(result: InterestSearchResult) {
        viewModelScope.launch {
            try {
                // Web defaults to weight 50 on add via the search picker
                api.setInterest(qid = result.qid, weight = 50).unwrapEmpty()
                _uiState.value = _uiState.value.copy(
                    searchQuery = "",
                    searchResults = emptyList(),
                )
                refresh(quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun regenerateSummary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegeneratingSummary = true, error = null)
            try {
                val resp = api.regenerateSummary().unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isRegeneratingSummary = false,
                    summary = resp.summary,
                )
            } catch (e: CancellationException) {
                throw e
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
            delay(SEARCH_DEBOUNCE)
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                val resp = api.searchInterests(trimmed).unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = resp.results,
                )
            } catch (e: CancellationException) {
                throw e
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
}
