// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.chess.model.Game
import org.mochios.chess.repository.ChessRepository
import org.mochios.chess.ui.components.ChessSidebarGame
import javax.inject.Inject

/**
 * List-screen state; [activeSidebar] / [completedSidebar] are projections of
 * [games] computed once per load.
 */
data class ChessGameListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val games: List<Game> = emptyList(),
    val identity: String? = null,
    val error: MochiError? = null,
    val newGameDialogOpen: Boolean = false,
    val activeSidebar: List<ChessSidebarGame> = emptyList(),
    val completedSidebar: List<ChessSidebarGame> = emptyList(),
)

/**
 * One-shot side effects (toasts, navigation) kept out of the UI state.
 */
sealed class ChessGameListEvent {
    /** Show a transient string (already localised) in a snackbar. */
    data class Toast(val message: String) : ChessGameListEvent()

    /** Navigate to a specific game (e.g. after Start Game returns). */
    data class OpenGame(val gameId: String) : ChessGameListEvent()
}

@HiltViewModel
class ChessGameListViewModel @Inject constructor(
    private val repo: ChessRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChessGameListUiState())
    val uiState: StateFlow<ChessGameListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChessGameListEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChessGameListEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val identity = sessionManager.getBoundIdentity()
            _uiState.value = _uiState.value.copy(identity = identity)
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val games = repo.listGames()
                applyGames(games)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val games = repo.listGames()
                applyGames(games, refreshing = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    private fun applyGames(games: List<Game>, refreshing: Boolean = false) {
        val identity = _uiState.value.identity.orEmpty()
        // Web sorts the master list by `updated` desc, then filters by
        // status into the two sidebar groups. Replicated 1:1.
        val sorted = games.sortedByDescending { it.updated }
        val active = sorted.filter { it.status == "active" }.map { it.toSidebarRow(identity) }
        val completed = sorted.filter { it.status != "active" }.map { it.toSidebarRow(identity) }
        _uiState.value = _uiState.value.copy(
            games = sorted,
            activeSidebar = active,
            completedSidebar = completed,
            isLoading = false,
            isRefreshing = refreshing.let { if (it) false else _uiState.value.isRefreshing },
            error = null,
        )
    }

    private fun Game.toSidebarRow(identity: String): ChessSidebarGame {
        val routeId = fingerprint?.takeIf { it.isNotBlank() } ?: id
        return ChessSidebarGame(
            id = routeId,
            opponentId = opponentId(identity),
            opponentName = opponentName(identity),
            updated = updated,
        )
    }
}
