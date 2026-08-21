// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.wikis.model.SearchResult
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * Search state. [results] belong to [debouncedQuery], not to the latest
 * keystroke in [query].
 */
data class SearchUiState(
    val query: String = "",
    val debouncedQuery: String = "",
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val error: MochiError? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    private val initialQuery: String = savedStateHandle.get<String>("q").orEmpty()

    private val _uiState = MutableStateFlow(
        SearchUiState(query = initialQuery, debouncedQuery = initialQuery)
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Hot debounced source for the search query — drives both the server fetch and the URL sync. */
    private val _queryFlow = MutableStateFlow(initialQuery)

    init {
        // Debounced fetch + back-stack arg sync. Web uses 300ms — keep that
        // here so the perceived responsiveness matches.
        viewModelScope.launch {
            _queryFlow
                .debounce(SEARCH_DEBOUNCE)
                .distinctUntilChanged()
                .onEach { debounced ->
                    // Persist into SavedStateHandle so a rotation / process
                    // death reload restores the same query.
                    savedStateHandle["q"] = debounced
                    _uiState.value = _uiState.value.copy(debouncedQuery = debounced)
                }
                .collect { debounced ->
                    runSearch(debounced)
                }
        }

        // Kick off an initial fetch if the route arrived with a `q=` value.
        if (initialQuery.isNotBlank()) {
            viewModelScope.launch { runSearch(initialQuery) }
        }
    }

    /** Update the query as the user types. Debounces before hitting the server. */
    fun updateQuery(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
        _queryFlow.value = value
    }

    /**
     * Calls runSearch directly: _queryFlow is distinctUntilChanged, so
     * re-emitting the query that just failed would be dropped and the button
     * would do nothing.
     */
    fun retry() {
        viewModelScope.launch { runSearch(_uiState.value.debouncedQuery) }
    }

    private suspend fun runSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                results = emptyList(),
                error = null,
            )
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val response = repository.search(wikiId, trimmed)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                results = response.results,
                error = null,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.toMochiError(),
            )
        }
    }
}
