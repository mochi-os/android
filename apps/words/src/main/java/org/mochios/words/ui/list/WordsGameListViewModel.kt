// Copyright © 2026 Mochi OÜ
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
import org.mochios.words.model.NewGameFriend
import org.mochios.words.repository.WordsRepository
import javax.inject.Inject

/**
 * UI state for the Words games-list screen + drawer. Holds the loaded
 * games array plus the new-game friends list (loaded lazily when the
 * dialog opens). The detail screen reuses the same VM for the drawer.
 */
data class WordsGameListUiState(
    val games: List<GameListItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val myIdentity: String = "",
    val newGameFriends: List<NewGameFriend> = emptyList(),
    val isLoadingFriends: Boolean = false,
    val friendsError: MochiError? = null,
    val isCreatingGame: Boolean = false,
    val createGameError: MochiError? = null,
    val createdGameId: String? = null,
)

@HiltViewModel
class WordsGameListViewModel @Inject constructor(
    private val repository: WordsRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordsGameListUiState())
    val uiState: StateFlow<WordsGameListUiState> = _uiState.asStateFlow()

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
     * Read the user's primary identity once at construction so the
     * sidebar's "other players" rendering can filter out the caller. The
     * Words server tags every game row with the caller's player number,
     * but the sidebar wants names — and games come back with raw entity
     * IDs in the player columns, so we still need the identity to
     * compare against.
     */
    private fun captureIdentity() {
        viewModelScope.launch {
            val identity = sessionManager.getBoundIdentity().orEmpty()
            _uiState.value = _uiState.value.copy(myIdentity = identity)
        }
    }

    fun loadNewGameFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFriends = true, friendsError = null)
            try {
                val friends = repository.getNewGameFriends()
                _uiState.value = _uiState.value.copy(newGameFriends = friends, isLoadingFriends = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingFriends = false,
                    friendsError = e.toMochiError(),
                )
            }
        }
    }

    fun createGame(opponents: List<String>, language: String) {
        if (opponents.isEmpty() || opponents.size > 3) return
        if (language != "en_US" && language != "en_UK") return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingGame = true, createGameError = null)
            try {
                val gameId = repository.createGame(opponents, language)
                _uiState.value = _uiState.value.copy(
                    isCreatingGame = false,
                    createdGameId = gameId,
                )
                // Refresh the games list so the new game shows up in the
                // sidebar/landing immediately rather than waiting for the
                // next on-resume tick.
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingGame = false,
                    createGameError = e.toMochiError(),
                )
            }
        }
    }

    /** Mark the created-game id as consumed so the dialog can dismiss without re-firing. */
    fun consumeCreatedGame() {
        _uiState.value = _uiState.value.copy(createdGameId = null)
    }

    /** Clear the friends-load error after the user dismisses an inline error. */
    fun clearFriendsError() {
        _uiState.value = _uiState.value.copy(friendsError = null)
    }

    /** Clear the create-game error (used after toast surfaces it). */
    fun clearCreateError() {
        _uiState.value = _uiState.value.copy(createGameError = null)
    }
}
