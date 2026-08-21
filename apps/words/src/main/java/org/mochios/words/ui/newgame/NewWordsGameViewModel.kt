// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.newgame

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
import org.mochios.words.model.NewGameFriend
import org.mochios.words.repository.WordsRepository
import javax.inject.Inject

data class NewWordsGameUiState(
    val friends: List<NewGameFriend> = emptyList(),
    val isLoadingFriends: Boolean = true,
    val friendsError: MochiError? = null,
    val isCreating: Boolean = false,
    val createError: MochiError? = null,
    val createdGameId: String? = null
)

/** Drives the new-words-game screen: opponent list and the create call. */
@HiltViewModel
class NewWordsGameViewModel @Inject constructor(
    private val repository: WordsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewWordsGameUiState())
    val uiState: StateFlow<NewWordsGameUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
    }

    /** (Re)loads the eligible-opponent list. */
    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFriends = true, friendsError = null)
            try {
                // The service orders by entity id, which is opaque, so the
                // picker listed friends in no order a reader could follow.
                // Sorting belongs here rather than in SQL — chess does the same.
                val friends = repository.getNewGameFriends()
                    .sortedWith(compareBy(NaturalCompare) { friend -> friend.name })
                _uiState.value = _uiState.value.copy(
                    friends = friends,
                    isLoadingFriends = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingFriends = false,
                    friendsError = e.toMochiError(),
                )
            }
        }
    }

    /** Clears the pending-navigation id once the screen has opened the game. */
    fun consumeCreatedGame() {
        _uiState.value = _uiState.value.copy(createdGameId = null)
    }

    /** Starts a game and reports its id back through [NewWordsGameUiState]. */
    fun createGame(opponents: List<String>, language: String) {
        if (opponents.isEmpty() || opponents.size > 3) return
        if (language != "en_US" && language != "en_UK") return
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
            try {
                val gameId = repository.createGame(opponents, language)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdGameId = gameId.takeIf { id -> id.isNotBlank() }
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
