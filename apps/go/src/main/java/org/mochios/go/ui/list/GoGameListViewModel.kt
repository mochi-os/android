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
import org.mochios.go.model.Game
import org.mochios.go.model.NewGameFriend
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

    val newGameDialogOpen: Boolean = false,
    val newGameFriends: List<NewGameFriend>? = null,
    val newGameFriendsLoading: Boolean = false,
    val newGameFriendsError: MochiError? = null,

    val creatingGame: Boolean = false,
)

/** Side-effect events the screen listens for (snackbar + open-game nav). */
sealed class GoGameListEvent {
    data class Toast(val message: String) : GoGameListEvent()
    data class OpenGame(val gameId: String) : GoGameListEvent()
}

@HiltViewModel
class GoGameListViewModel @Inject constructor(
    private val repo: GoRepository,
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

    // ---------------- new-game dialog ----------------

    fun openNewGameDialog() {
        _uiState.value = _uiState.value.copy(newGameDialogOpen = true)
        // Lazy-load the friends list the first time the dialog opens —
        // refresh on every reopen so a friend added in another app
        // surfaces without restarting the app.
        loadNewGameFriends()
    }

    fun closeNewGameDialog() {
        _uiState.value = _uiState.value.copy(
            newGameDialogOpen = false,
            // Reset any in-flight error so the next open starts fresh.
            newGameFriendsError = null,
        )
    }

    private fun loadNewGameFriends() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                newGameFriendsLoading = true,
                newGameFriendsError = null,
            )
            try {
                val friends = repo.getNewGameFriends()
                _uiState.value = _uiState.value.copy(
                    newGameFriends = friends,
                    newGameFriendsLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    newGameFriendsLoading = false,
                    newGameFriendsError = e.toMochiError(),
                )
            }
        }
    }

    /**
     * Submit the new-game request and, on success, close the dialog,
     * refresh the list and navigate to the new game's detail page. Toast
     * on failure — matches the web behaviour.
     */
    fun createGame(opponent: String, boardSize: Int, komi: Double, errorMessage: String) {
        if (_uiState.value.creatingGame) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(creatingGame = true)
            try {
                val resp = repo.createGame(opponent, boardSize, komi)
                _uiState.value = _uiState.value.copy(
                    creatingGame = false,
                    newGameDialogOpen = false,
                )
                // Refresh so the new game appears under Active when the user
                // taps back from the detail screen.
                loadGames()
                _events.emit(GoGameListEvent.OpenGame(resp.id))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(creatingGame = false)
                val mochi = e.toMochiError()
                _events.emit(GoGameListEvent.Toast(messageOr(mochi, errorMessage)))
            }
        }
    }

    private fun messageOr(err: MochiError, fallback: String): String {
        return when (err) {
            is MochiError.AuthError -> err.message ?: fallback
            is MochiError.ForbiddenError -> err.message ?: fallback
            is MochiError.NotFoundError -> err.message ?: fallback
            is MochiError.ServerError -> err.message ?: fallback
            is MochiError.Unknown -> err.message ?: fallback
            else -> fallback
        }
    }
}
