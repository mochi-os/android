// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.words.model.GameListItem
import org.mochios.words.repository.WordsRepository
import javax.inject.Inject

data class WordsGameListUiState(
    val games: List<GameListItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val myIdentity: String = "",
)

@HiltViewModel
class WordsGameListViewModel @Inject constructor(
    private val repository: WordsRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordsGameListUiState())
    val uiState: StateFlow<WordsGameListUiState> = _uiState.asStateFlow()

    private var resumedBefore = false

    init {
        load()
        captureIdentity()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val games = repository.listGames()
                _uiState.value = _uiState.value.copy(games = games, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /**
     * The screen's ON_RESUME. The first arrives as the screen opens, while
     * init is already loading, so only a later one - back from a game or
     * from another app - refreshes.
     */
    fun onScreenResumed() {
        if (!resumedBefore) {
            resumedBefore = true
            return
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val games = repository.listGames()
                _uiState.value = _uiState.value.copy(games = games, isRefreshing = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    /**
     * The sidebar filters the caller out of the player names, which arrive as
     * raw entity IDs.
     */
    private fun captureIdentity() {
        viewModelScope.launch {
            val identity = sessionManager.getBoundIdentity().orEmpty()
            _uiState.value = _uiState.value.copy(myIdentity = identity)
        }
    }
}
