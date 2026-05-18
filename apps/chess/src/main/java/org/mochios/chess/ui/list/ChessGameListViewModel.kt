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
 * UI state for the chess game-list landing screen. Mirrors the web
 * `ChessLayout` + the route `/index.tsx` view body:
 *
 *  - [games] is the raw `Game` list fetched from `-/list`. Both the empty
 *    state and the visible card grid derive from this single list (and
 *    [activeSidebar] / [completedSidebar] are pre-computed sidebar
 *    projections so the drawer doesn't redo the work on every recomposition).
 *  - [identity] is the caller's entity ID — needed to resolve "the
 *    opponent" on every row. Captured from [SessionManager.boundIdentity]
 *    at load time so it stays stable for a session.
 *  - [error] is non-null when the initial `-/list` call failed; the UI
 *    surfaces it with a retry button.
 *  - [newGameDialogOpen] / [creating] back the new-game dialog the screen
 *    embeds. The dialog itself has its own ViewModel for the friends
 *    fetch, but the open/close flag and the cross-screen "OpenGame" event
 *    live here so the dialog's success path can drive navigation through
 *    the same event channel that any future success path uses.
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
 * Side-effect events from the list ViewModel. Mirrors the pattern in
 * `WikiListViewModel` — toasts and navigation are one-shot and shouldn't
 * persist in the UI state across recompositions.
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

    /** Server origin — captured at construction for opponent-avatar URLs. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

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

    /**
     * Fetch the full game list. On success, also re-project the active /
     * completed sidebar lists from the new data so the drawer reflects the
     * same source of truth without an extra `LaunchedEffect`.
     */
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

    // ---- New-game dialog ----

    fun openNewGameDialog() {
        _uiState.value = _uiState.value.copy(newGameDialogOpen = true)
    }

    fun closeNewGameDialog() {
        _uiState.value = _uiState.value.copy(newGameDialogOpen = false)
    }

    /**
     * Called by the new-game dialog after a successful create. Refresh the
     * list so the new game shows up and emit an [OpenGame] event so the
     * screen can navigate straight in.
     */
    fun onGameCreated(gameId: String) {
        closeNewGameDialog()
        refresh()
        viewModelScope.launch {
            _events.emit(ChessGameListEvent.OpenGame(gameId))
        }
    }

    fun onToast(message: String) {
        viewModelScope.launch {
            _events.emit(ChessGameListEvent.Toast(message))
        }
    }

    private fun Game.toSidebarRow(identity: String): ChessSidebarGame {
        val routeId = fingerprint?.takeIf { it.isNotBlank() } ?: id
        val statusLabel = if (status == "active") null else status
        return ChessSidebarGame(
            id = routeId,
            opponentId = opponentId(identity),
            opponentName = opponentName(identity),
            statusLabel = statusLabel,
            updated = updated,
        )
    }
}
