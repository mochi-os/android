// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.GameChat
import org.mochios.android.ui.components.GameChatPage
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.chess.engine.Record
import org.mochios.chess.engine.isDrawnPosition
import org.mochios.chess.model.Game
import org.mochios.chess.model.GameMessage
import org.mochios.chess.model.messageKey
import org.mochios.chess.model.MoveRequest
import org.mochios.chess.repository.ChessRepository
import org.mochios.chess.ui.detail.board.CapturedPiece
import org.mochios.chess.ui.detail.board.capturedPieces
import javax.inject.Inject

/**
 * Detail-screen state. [error] holds only load and refresh failures; mutation
 * failures surface as [ChessGameEvent.Toast]. The chat is [ChessGameViewModel.chat]'s.
 */
data class ChessGameUiState(
    val game: Game? = null,
    val identity: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val lastMove: Pair<String, String>? = null,
    /** (taken by White, taken by Black), from the record. */
    val captured: Pair<List<CapturedPiece>, List<CapturedPiece>> = emptyList<CapturedPiece>() to emptyList(),
    val pendingMove: Boolean = false,
    val isResigning: Boolean = false,
    val isDrawOffering: Boolean = false,
    val isDrawAccepting: Boolean = false,
    val isDrawDeclining: Boolean = false,
    val isDeleting: Boolean = false,
    val isRematching: Boolean = false,
)

/**
 * One-shot side effects (toasts, navigation) kept out of [ChessGameUiState].
 */
sealed class ChessGameEvent {
    /** Show a transient, already-localised string in a snackbar. */
    data class Toast(val message: String) : ChessGameEvent()

    /** Navigate to a different game's detail page (e.g. after rematch). */
    data class OpenGame(val gameId: String) : ChessGameEvent()

    /** Game was deleted — pop back to the list. */
    data object NavigateUp : ChessGameEvent()
}

@HiltViewModel
class ChessGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ChessRepository,
) : ViewModel() {

    /**
     * The path-segment value — either a row UID or a 9-char fingerprint.
     * Both resolve to the same game server-side.
     */
    val gameId: String = savedStateHandle.get<String>("gameId") ?: ""

    private val _uiState = MutableStateFlow(ChessGameUiState())
    val uiState: StateFlow<ChessGameUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChessGameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChessGameEvent> = _events.asSharedFlow()

    /** The game's chat, read beside the game rather than after it. */
    val chat = GameChat(
        scope = viewModelScope,
        fetch = { before ->
            val page = repo.getMessages(gameId, before = before)
            GameChatPage(page.messages, page.more, page.cursor)
        },
        post = { body -> repo.sendMessage(gameId, body) },
        key = ::messageKey,
        created = { row -> row.created },
        report = { error -> _events.tryEmit(ChessGameEvent.Toast(error.userMessage())) },
    )

    /** The pending or in-flight refresh; cancelled when a newer one starts. */
    private var refreshJob: Job? = null

    private var resumedBefore = false

    init {
        load()
    }

    // ---- Loading ----

    /** Read the game and its chat at once, rather than one after the other. */
    fun load() {
        chat.load()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val view = repo.getGame(gameId)
                val record = replay(view.game)
                _uiState.value = _uiState.value.copy(
                    game = view.game,
                    identity = view.identity,
                    lastMove = record?.last?.let { move -> move.from to move.to },
                    captured = captures(record),
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /**
     * The screen's ON_RESUME. The first arrives as the screen opens, while
     * [load] from init is already fetching, so only a later one - a return
     * after frames may have been missed - refreshes.
     */
    fun onScreenResumed() {
        if (!resumedBefore) {
            resumedBefore = true
            return
        }
        refresh()
    }

    /**
     * Refetch the game and messages. Each call cancels the previous refresh
     * and waits [REFRESH_DEBOUNCE] first, so a burst of websocket frames and
     * action results - our own move's echo lands with the move's response -
     * makes one fetch, and only the latest one updates the board. The chat
     * folds its newest page into the pages already read.
     */
    fun refresh() {
        chat.refresh()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val view = repo.getGame(gameId)
                val record = replay(view.game)
                _uiState.value = _uiState.value.copy(
                    game = view.game,
                    identity = view.identity,
                    lastMove = record?.last?.let { move -> move.from to move.to },
                    captured = captures(record),
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun loadMoreOlder() = chat.loadMore()

    // ---- Move submission ----

    /**
     * Play the move on the game's record, show it at once and POST the record
     * it makes. The record is replayed from the stored PGN, so the PGN sent
     * extends it, and the draw test sees every position the game has had. A
     * refused move takes the board back to where it was before any refresh
     * can, since with no connection that refresh fails too.
     */
    fun submitMove(from: String, to: String, promotion: String?) {
        val game = _uiState.value.game ?: return
        if (game.status != "active") return
        if (_uiState.value.pendingMove) return
        val record = Record.resume(game.fen, game.pgn) ?: return
        val played = record.play(from, to, promotion) ?: return
        val board = record.board

        // Terminal-state hints from the position the move reached.
        var status: String? = null
        var winner: String? = null
        if (board.isMated) {
            status = "checkmate"
            winner = _uiState.value.identity
        } else if (board.isStaleMate) {
            status = "stalemate"
        } else if (isDrawnPosition(board)) {
            status = "draw"
        }

        val moved = game.copy(fen = record.fen, pgn = record.pgn)
        val before = _uiState.value
        _uiState.update {
            it.copy(game = moved, lastMove = from to to, captured = captures(record), pendingMove = true)
        }
        viewModelScope.launch {
            try {
                repo.move(
                    game = gameId,
                    request = MoveRequest(
                        from = from,
                        to = to,
                        promotion = promotion,
                        fen = moved.fen,
                        pgn = moved.pgn,
                        san = played.san,
                        status = status,
                        winner = winner,
                    ),
                )
                // Refresh authoritative state (game.status / winner /
                // draw_offer reset / etc. updated by the server).
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Only while the board still shows this move: a refresh that
                // landed meanwhile already holds the server's position.
                _uiState.update { state ->
                    if (state.game == moved) {
                        state.copy(game = game, lastMove = before.lastMove, captured = before.captured)
                    } else {
                        state
                    }
                }
                emitToast(e.toMochiError())
                refresh()
            } finally {
                _uiState.update { it.copy(pendingMove = false) }
            }
        }
    }

    // ---- Chat ----

    /** Send [body]; [onFinished] hears whether it went, and a failure is said. */
    fun sendChat(body: String, onFinished: (Boolean) -> Unit) {
        chat.send(body) { error ->
            if (error != null) _events.tryEmit(ChessGameEvent.Toast(error.userMessage()))
            onFinished(error == null)
        }
    }

    // ---- Game-flow mutations ----

    fun resign() {
        if (_uiState.value.isResigning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResigning = true)
            try {
                repo.resign(gameId)
                refresh()
            } catch (e: Exception) {
                emitToast(e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isResigning = false)
            }
        }
    }

    fun offerDraw() {
        if (_uiState.value.isDrawOffering) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDrawOffering = true)
            try {
                repo.drawOffer(gameId)
                refresh()
            } catch (e: Exception) {
                emitToast(e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isDrawOffering = false)
            }
        }
    }

    fun acceptDraw() {
        if (_uiState.value.isDrawAccepting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDrawAccepting = true)
            try {
                repo.drawAccept(gameId)
                refresh()
            } catch (e: Exception) {
                emitToast(e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isDrawAccepting = false)
            }
        }
    }

    fun declineDraw() {
        if (_uiState.value.isDrawDeclining) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDrawDeclining = true)
            try {
                repo.drawDecline(gameId)
                refresh()
            } catch (e: Exception) {
                emitToast(e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isDrawDeclining = false)
            }
        }
    }

    fun rematch() {
        val game = _uiState.value.game ?: return
        val myIdentity = _uiState.value.identity
        val opponent = if (game.identity == myIdentity) game.opponent else game.identity
        if (opponent.isBlank()) return
        // Without this a second tap creates a second game: the menu closes on
        // click, but reopening it while the request is still in flight offers
        // Rematch again. submitMove has carried the same guard all along.
        if (_uiState.value.isRematching) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRematching = true)
            try {
                val created = repo.createGame(opponent)
                _events.emit(ChessGameEvent.OpenGame(created.id))
            } catch (e: Exception) {
                emitToast(e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isRematching = false)
            }
        }
    }

    fun deleteGame() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repo.deleteGame(gameId)
                _events.emit(ChessGameEvent.NavigateUp)
            } catch (e: Exception) {
                emitToast(e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isDeleting = false)
            }
        }
    }

    /**
     * Every websocket event re-fetches the game and messages rather than
     * applying frames piecemeal.
     */
    fun onWebsocketEvent() {
        refresh()
    }

    // ---- Helpers ----

    private suspend fun emitToast(error: MochiError) {
        val message = error.userMessage()
        _events.emit(ChessGameEvent.Toast(message))
    }

    /**
     * [game]'s record, replayed off the main thread: the last move it marks
     * and the captures it counts. A record that does not replay to the
     * position marks no move, as on the web: marking a move from some other
     * game's history is worse than marking none.
     */
    private suspend fun replay(game: Game): Record? =
        withContext(Dispatchers.Default) { Record.resume(game.fen, game.pgn) }

    private fun captures(record: Record?): Pair<List<CapturedPiece>, List<CapturedPiece>> =
        record?.let(::capturedPieces) ?: (emptyList<CapturedPiece>() to emptyList())
}
