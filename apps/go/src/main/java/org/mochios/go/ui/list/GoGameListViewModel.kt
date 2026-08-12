// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.list

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
import org.mochios.go.model.Game
import org.mochios.go.repository.GoRepository
import javax.inject.Inject

/**
 * UI state for the Go landing list. Mirrors the web `GamesListPage`
 * (`apps/go/web/src/routes/_authenticated/index.tsx`) — a single list of
 * the user's games partitioned by status into Active vs Completed, plus
 * the New-game dialog state.
 *
 *  - [games] is the full list as returned by `-/list` (server sorts by
 *    `updated DESC`); the screen splits into active / completed on render
 *  - [newGameFriends] is loaded eagerly on first open so the dialog can
 *    show the picker without an extra round trip; null = not yet fetched
 *  - [creatingGame] disables the dialog's Start button while the create
 *    request is in flight
 */
data class GoGameListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val games: List<Game> = emptyList(),
    val error: MochiError? = null,

    /**
     * The signed-in entity ID, needed to tell which side of a game the user is
     * on. `opponent_name` holds the invitee's name on both peers, so without
     * this a game the user did not create names the user back to themselves.
     */
    val identity: String? = null,
)

/** Side-effect events the screen listens for (snackbar + open-game nav). */
sealed class GoGameListEvent {
    data class Toast(val message: String) : GoGameListEvent()
    data class OpenGame(val gameId: String) : GoGameListEvent()
}

@HiltViewModel
class GoGameListViewModel @Inject constructor(
    private val repo: GoRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoGameListUiState())
    val uiState: StateFlow<GoGameListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GoGameListEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GoGameListEvent> = _events.asSharedFlow()

    init {
        loadGames()
    }

    fun loadGames() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Resolved alongside the games rather than in init, so a list
                // loaded before the identity arrives cannot render a card
                // naming the user back to themselves.
                val identity = sessionManager.getBoundIdentity()
                _uiState.value = _uiState.value.copy(identity = identity)
                val games = repo.listGames()
                _uiState.value = _uiState.value.copy(games = games, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val games = repo.listGames()
                _uiState.value = _uiState.value.copy(
                    games = games,
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
        }
    }
}
