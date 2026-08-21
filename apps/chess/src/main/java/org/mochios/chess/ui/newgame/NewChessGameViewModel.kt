// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.newgame

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
import org.mochios.chess.model.NewGameFriend
import org.mochios.chess.repository.ChessRepository
import javax.inject.Inject

data class NewChessGameUiState(
    val isLoadingFriends: Boolean = true,
    val friends: List<NewGameFriend> = emptyList(),
    val friendsError: MochiError? = null,
    val selectedId: String = "",
    val isCreating: Boolean = false,
    val createError: MochiError? = null,
)

@HiltViewModel
class NewChessGameViewModel @Inject constructor(
    private val repo: ChessRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewChessGameUiState())
    val uiState: StateFlow<NewChessGameUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
    }

    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFriends = true, friendsError = null)
            try {
                val friends = repo.getNewGameFriends()
                _uiState.value = _uiState.value.copy(
                    friends = friends.sortedWith(compareBy(NaturalCompare) { it.name }),
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

    fun select(id: String) {
        _uiState.value = _uiState.value.copy(selectedId = id)
    }

    /**
     * [onCreated] receives the new game's id; [onError] an already-localised
     * message.
     */
    fun create(onCreated: (String) -> Unit, onError: (String) -> Unit) {
        val state = _uiState.value
        if (state.selectedId.isBlank() || state.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
            try {
                val response = repo.createGame(state.selectedId)
                _uiState.value = _uiState.value.copy(isCreating = false)
                if (response.id.isNotBlank()) onCreated(response.id)
            } catch (e: Exception) {
                val err = e.toMochiError()
                _uiState.value = _uiState.value.copy(isCreating = false, createError = err)
                onError(messageOf(err))
            }
        }
    }

    private fun messageOf(err: MochiError): String = when (err) {
        is MochiError.AuthError -> err.message.orEmpty()
        is MochiError.ForbiddenError -> err.message.orEmpty()
        is MochiError.NotFoundError -> err.message.orEmpty()
        is MochiError.ServerError -> err.message.orEmpty()
        is MochiError.Unknown -> err.message.orEmpty()
        else -> ""
    }
}
