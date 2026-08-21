// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.newgame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.go.model.NewGameFriend
import org.mochios.go.repository.GoRepository
import javax.inject.Inject

/**
 * State of the new-game screen. [createdGameId] is set after a successful
 * create and cleared by [consumeCreatedGame].
 */
data class NewGoGameUiState(
    val friends: List<NewGameFriend> = emptyList(),
    val friendsLoading: Boolean = true,
    val friendsError: MochiError? = null,
    val isCreating: Boolean = false,
    val createError: MochiError? = null,
    val createdGameId: String? = null
)

/** Drives the new-go-game screen: opponent list and the create call. */
@HiltViewModel
class NewGoGameViewModel @Inject constructor(
    private val repo: GoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGoGameUiState())
    val uiState: StateFlow<NewGoGameUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
    }

    /** (Re)loads the eligible-opponent list. */
    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(friendsLoading = true, friendsError = null)
            try {
                val friends = repo.getNewGameFriends()
                _uiState.value = _uiState.value.copy(
                    friends = friends,
                    friendsLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    friendsLoading = false,
                    friendsError = e.toMochiError(),
                )
            }
        }
    }

    /** Clears the pending-navigation id once the screen has opened the game. */
    fun consumeCreatedGame() {
        _uiState.value = _uiState.value.copy(createdGameId = null)
    }

    /** Starts a game and reports its id back through [NewGoGameUiState]. */
    fun createGame(opponent: String, boardSize: Int, komi: Double) {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
            try {
                val resp = repo.createGame(opponent, boardSize, komi)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdGameId = resp.id.takeIf { id -> id.isNotBlank() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createError = e.toMochiError(),
                )
            }
        }
    }
}
