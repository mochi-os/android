// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.go.engine.GoGame
import org.mochios.go.engine.IllegalMoveException
import org.mochios.go.engine.Score
import org.mochios.go.engine.Stone
import org.mochios.go.model.Game
import org.mochios.go.model.GameMessage
import org.mochios.go.model.MoveRequest
import org.mochios.go.model.PassRequest
import org.mochios.go.repository.GoRepository
import javax.inject.Inject

/**
 * UI state for [GoGameDetailScreen]. Mirrors the page-level state in
 * `apps/go/web/src/features/go/index.tsx` minus the React-Query plumbing:
 *
 *  - [game] — the canonical record from `-/view`, refreshed after every
 *    mutation so server-side bookkeeping (status transitions, ko clearance,
 *    capture counters) is what the UI renders. Null while the initial load
 *    is in flight.
 *  - [goGame] — the parsed [GoGame] engine instance derived from [Game.fen]
 *    and [Game.previousFen]. Held in state (not derived on every render)
 *    because [GoBoard] also parses it and we want one source of truth for
 *    legality checks the screen relies on.
 *  - [messages] — chat + move + system rows in created-ascending order
 *    (the panel anchors to the bottom). Replaced wholesale on refresh and
 *    appended to on WebSocket events.
 *  - [myIdentity] — the asking user's entity id; resolved from the
 *    `GameViewResponse` so the screen can decide which side the user is on.
 *  - [isMyTurn] — derived: True when [GoGame.turn] matches the user's
 *    colour. Cached so the action menu doesn't recompute on every recomp.
 *  - [score] — only populated when the game ended (status `"finished"`),
 *    using the same area-scoring algorithm web uses. Drives the
 *    "Black wins — B:… W:…" status line.
 */
data class GoGameDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val game: Game? = null,
    val goGame: GoGame? = null,
    val messages: List<GameMessage> = emptyList(),
    val isLoadingMessages: Boolean = false,
    val isLoadingMoreMessages: Boolean = false,
    val hasMoreMessages: Boolean = false,
    val messagesError: MochiError? = null,
    val myIdentity: String = "",
    val isMyTurn: Boolean = false,
    val score: Score? = null,
    val lastMove: Pair<Int, Int>? = null,
    val error: MochiError? = null,
    /** True while a `place(row, col)` request is in flight. */
    val isMoving: Boolean = false,
    /** True while a `passTurn()` request is in flight. */
    val isPassing: Boolean = false,
    /** True while a chat message is being sent. */
    val isSendingMessage: Boolean = false,
    /** True while the resign request is in flight. */
    val isResigning: Boolean = false,
    /** True while a draw-offer / accept / decline is in flight. */
    val isDrawOffering: Boolean = false,
    val isDrawAccepting: Boolean = false,
    val isDrawDeclining: Boolean = false,
    /** True while the delete request is in flight. */
    val isDeleting: Boolean = false,
    /** True while a rematch is being created. */
    val isCreatingRematch: Boolean = false,
)

/**
 * Side-effect events emitted to [GoGameDetailScreen]. The web equivalent
 * uses `toast()` and `navigate()`; here we marshal both through one flow
 * to keep the ViewModel free of UI types.
 */
sealed class GoGameDetailEvent {
    data class Toast(val message: String) : GoGameDetailEvent()
    data class OpenGame(val gameId: String) : GoGameDetailEvent()
    data object NavigateBack : GoGameDetailEvent()
}

/**
 * ViewModel for the Go game-detail screen. Loads the game + messages in
 * `init`, holds the engine instance and identity, and exposes the move /
 * pass / chat / resign / draw / rematch / delete mutations the screen
 * binds to its menu items, board, chat composer, and confirm dialogs.
 *
 * Mirrors the structure of `apps/go/web/src/features/go/index.tsx`
 * `GoGameView` — `useMoveMutation` ↔ [place], `usePassMutation` ↔
 * [passTurn], `useDrawOfferMutation` ↔ [offerDraw], etc.
 */
@HiltViewModel
class GoGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoRepository,
) : ViewModel() {

    val gameId: String = savedStateHandle.get<String>("gameId").orEmpty()

    private val _state = MutableStateFlow(GoGameDetailUiState(isLoading = true))
    val state: StateFlow<GoGameDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GoGameDetailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GoGameDetailEvent> = _events.asSharedFlow()

    init {
        if (gameId.isNotBlank()) {
            loadGame()
            loadMessages()
        }
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    fun loadGame() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = it.game == null,
                    isRefreshing = it.game != null,
                    error = null,
                )
            }
            try {
                val response = repository.getGame(gameId)
                val game = response.game
                val goGame = parseGame(game)
                val myIdentity = response.identity.ifBlank { game.identity }
                val myColor = colorFor(game, myIdentity)
                val isMyTurn = game.status == "active" && goGame != null && goGame.turn == myColor
                val score = if (game.status == "finished" && goGame != null) {
                    goGame.score(game.komi)
                } else null
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        game = game,
                        goGame = goGame,
                        myIdentity = myIdentity,
                        isMyTurn = isMyTurn,
                        score = score,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.toMochiError(),
                    )
                }
            }
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMessages = true, messagesError = null) }
            try {
                val response = repository.getMessages(gameId)
                // The endpoint returns newest-first; the panel renders
                // newest-last so we reverse here. Server-side ordering is
                // intentional: that's what the cursor-based pagination
                // expects (older pages append at the front).
                _state.update {
                    it.copy(
                        isLoadingMessages = false,
                        messages = response.messages.asReversed(),
                        hasMoreMessages = response.hasMore == true,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingMessages = false,
                        messagesError = e.toMochiError(),
                    )
                }
            }
        }
    }

    fun loadMoreMessages() {
        val current = _state.value
        if (current.isLoadingMoreMessages || !current.hasMoreMessages) return
        val oldest = current.messages.firstOrNull()?.created ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMoreMessages = true) }
            try {
                val response = repository.getMessages(gameId, before = oldest)
                _state.update {
                    it.copy(
                        isLoadingMoreMessages = false,
                        // Older page comes back newest-first; reverse so
                        // chronological order is preserved when prepended.
                        messages = response.messages.asReversed() + it.messages,
                        hasMoreMessages = response.hasMore == true,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingMoreMessages = false,
                        messagesError = e.toMochiError(),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    /**
     * Place a stone at ([row], [col]). Runs the move through the local
     * engine first so the network round-trip never carries an illegal move
     * (and so the caller's pending UI flashes briefly with the new state),
     * then posts the resulting FEN to the server. The server's `/view`
     * response replaces our local snapshot — that's our source of truth
     * for capture counters and ko enforcement after the move.
     */
    fun place(row: Int, col: Int, failedMoveMessage: String) {
        val snapshot = _state.value
        val game = snapshot.game ?: return
        val goGame = snapshot.goGame ?: return
        if (game.status != "active" || !snapshot.isMyTurn || snapshot.isMoving) return
        if (!goGame.isLegal(row, col)) return

        val moved = try {
            goGame.place(row, col)
        } catch (_: IllegalMoveException) {
            return
        }

        // Optimistic local update — flip turn, swap the engine and lastMove,
        // bump captures — so the board reflects the placement immediately.
        // The server response in [refresh] re-syncs everything afterwards.
        _state.update {
            it.copy(
                goGame = moved,
                isMyTurn = false,
                isMoving = true,
                lastMove = row to col,
                game = it.game?.copy(
                    fen = moved.board,
                    previousFen = goGame.board,
                    capturesBlack = moved.capturesBlack,
                    capturesWhite = moved.capturesWhite,
                ),
            )
        }

        val moveLabel = GoGame.coordToLabel(row, col, goGame.size)
        // SGF move strings: `B[row,col]` / `W[row,col]` joined by `;`.
        // Web uses the player's colour for the prefix, derived from the
        // pre-move engine turn (i.e. whose stone we're placing).
        val sgfMove = "${if (goGame.turn == Stone.BLACK) "B" else "W"}[$row,$col]"
        val newSgf = if (game.sgf.isBlank()) sgfMove else "${game.sgf};$sgfMove"

        viewModelScope.launch {
            try {
                repository.move(
                    gameId,
                    MoveRequest(
                        fen = moved.board,
                        previousFen = goGame.board,
                        sgf = newSgf,
                        capturesBlack = moved.capturesBlack,
                        capturesWhite = moved.capturesWhite,
                        moveLabel = moveLabel,
                    ),
                )
                _state.update { it.copy(isMoving = false) }
                // Re-fetch so server-side capture / ko bookkeeping replaces
                // our optimistic state.
                loadGame()
                // Also refresh the move/chat log so our own move row appears
                // immediately; the websocket frame for our own move isn't
                // echoed back to us. Web invalidates messages on own move.
                loadMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isMoving = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedMoveMessage)),
                )
                // Roll back to the server's state on failure.
                loadGame()
            }
        }
    }

    /**
     * Pass turn. When this is the second consecutive pass we compute the
     * score locally via the engine and include it in the request so the
     * server can record `status=finished`, the winner identity, and both
     * scores in one round trip — matching the web `handlePass` flow.
     */
    fun passTurn(failedPassMessage: String) {
        val snapshot = _state.value
        val game = snapshot.game ?: return
        val goGame = snapshot.goGame ?: return
        if (game.status != "active" || !snapshot.isMyTurn || snapshot.isPassing) return

        val newGame = goGame.pass()
        val isGameOver = newGame.consecutivePasses >= 2
        val playerColor = colorFor(game, snapshot.myIdentity)
        val sgfMove = "${if (playerColor == Stone.BLACK) "B" else "W"}[pass]"
        val newSgf = if (game.sgf.isBlank()) sgfMove else "${game.sgf};$sgfMove"

        val scoreResult: Score? = if (isGameOver) newGame.score(game.komi) else null
        val winner: String? = if (isGameOver && scoreResult != null) {
            winnerIdentityFor(game, scoreResult.winner)
        } else null

        _state.update { it.copy(isPassing = true) }
        viewModelScope.launch {
            try {
                repository.pass(
                    gameId,
                    PassRequest(
                        fen = newGame.board,
                        sgf = newSgf,
                        status = if (isGameOver) "finished" else null,
                        winner = winner,
                        scoreBlack = scoreResult?.black,
                        scoreWhite = scoreResult?.white,
                    ),
                )
                _state.update { it.copy(isPassing = false) }
                loadGame()
                // Refresh the move/chat log so our own pass row appears
                // immediately; our own websocket frame isn't echoed back.
                loadMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isPassing = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedPassMessage)),
                )
            }
        }
    }

    fun sendChat(body: String, failedSendMessage: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        if (_state.value.isSendingMessage) return
        _state.update { it.copy(isSendingMessage = true) }
        viewModelScope.launch {
            try {
                repository.sendMessage(gameId, trimmed)
                _state.update { it.copy(isSendingMessage = false) }
                // The websocket will deliver the new message back to us
                // and the panel will append it. As a fallback for offline
                // websockets, refresh the message list explicitly.
                loadMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isSendingMessage = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedSendMessage)),
                )
            }
        }
    }

    fun resign(failedResignMessage: String) {
        if (_state.value.isResigning) return
        _state.update { it.copy(isResigning = true) }
        viewModelScope.launch {
            try {
                repository.resign(gameId)
                _state.update { it.copy(isResigning = false) }
                loadGame()
                loadMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isResigning = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedResignMessage)),
                )
            }
        }
    }

    fun offerDraw(failedOfferMessage: String) {
        if (_state.value.isDrawOffering) return
        _state.update { it.copy(isDrawOffering = true) }
        viewModelScope.launch {
            try {
                repository.drawOffer(gameId)
                _state.update { it.copy(isDrawOffering = false) }
                loadGame()
            } catch (e: Exception) {
                _state.update { it.copy(isDrawOffering = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedOfferMessage)),
                )
            }
        }
    }

    fun acceptDraw(failedAcceptMessage: String) {
        if (_state.value.isDrawAccepting) return
        _state.update { it.copy(isDrawAccepting = true) }
        viewModelScope.launch {
            try {
                repository.drawAccept(gameId)
                _state.update { it.copy(isDrawAccepting = false) }
                loadGame()
                loadMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isDrawAccepting = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedAcceptMessage)),
                )
            }
        }
    }

    fun declineDraw(failedDeclineMessage: String) {
        if (_state.value.isDrawDeclining) return
        _state.update { it.copy(isDrawDeclining = true) }
        viewModelScope.launch {
            try {
                repository.drawDecline(gameId)
                _state.update { it.copy(isDrawDeclining = false) }
                loadGame()
                loadMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isDrawDeclining = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedDeclineMessage)),
                )
            }
        }
    }

    /**
     * Create a new game against the same opponent with the same settings.
     * Web preserves [Game.boardSize] and [Game.komi]; we do the same and
     * emit an [GoGameDetailEvent.OpenGame] so the screen can navigate.
     */
    fun rematch(failedRematchMessage: String) {
        val game = _state.value.game ?: return
        val myIdentity = _state.value.myIdentity
        if (_state.value.isCreatingRematch) return
        val opponent = if (game.identity == myIdentity) game.opponent else game.identity
        if (opponent.isBlank()) return
        _state.update { it.copy(isCreatingRematch = true) }
        viewModelScope.launch {
            try {
                val resp = repository.createGame(
                    opponent = opponent,
                    boardSize = game.boardSize,
                    komi = game.komi,
                )
                _state.update { it.copy(isCreatingRematch = false) }
                _events.tryEmit(GoGameDetailEvent.OpenGame(resp.id))
            } catch (e: Exception) {
                _state.update { it.copy(isCreatingRematch = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedRematchMessage)),
                )
            }
        }
    }

    fun deleteGame(failedDeleteMessage: String, deletedMessage: String) {
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                repository.deleteGame(gameId)
                _state.update { it.copy(isDeleting = false) }
                _events.tryEmit(GoGameDetailEvent.Toast(deletedMessage))
                _events.tryEmit(GoGameDetailEvent.NavigateBack)
            } catch (e: Exception) {
                _state.update { it.copy(isDeleting = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(messageOr(e.toMochiError(), failedDeleteMessage)),
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // WebSocket integration — invoked by the screen on each event
    // ------------------------------------------------------------------

    /**
     * Apply a decoded WebSocket event to the state. The web app uses a
     * react-query invalidate to refetch on every event; we follow the same
     * pattern (server is source of truth) but also append the new message
     * row locally so the chat panel updates without waiting for the round
     * trip.
     */
    fun applyWsEvent(rawType: String, message: GameMessage?) {
        if (message != null && message.id.isNotBlank()) {
            _state.update {
                if (it.messages.any { existing -> existing.id == message.id }) it
                else it.copy(messages = it.messages + message)
            }
        }
        // Move / system events change the game state too — refresh the
        // game so the FEN, status, draw_offer, and captures all line up.
        if (rawType == "move" || rawType == "system") {
            loadGame()
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun parseGame(game: Game): GoGame? {
        if (game.fen.isBlank()) {
            return runCatching { GoGame(game.boardSize.coerceAtLeast(9)) }.getOrNull()
        }
        return runCatching { GoGame(game.fen, game.previousFen) }.getOrNull()
    }

    private fun colorFor(game: Game, myIdentity: String): Stone =
        if (game.black == myIdentity) Stone.BLACK else Stone.WHITE

    private fun winnerIdentityFor(game: Game, winnerColor: Stone): String {
        // The winner is the identity holding `winnerColor`. `game.black`
        // holds the black player's id; whichever side that isn't is white.
        val blackId = game.black
        val whiteId = if (game.identity == blackId) game.opponent else game.identity
        return if (winnerColor == Stone.BLACK) blackId else whiteId
    }

    private fun messageOr(error: MochiError, fallback: String): String {
        return when (error) {
            is MochiError.AuthError -> error.message ?: fallback
            is MochiError.ForbiddenError -> error.message ?: fallback
            is MochiError.NotFoundError -> error.message ?: fallback
            is MochiError.ServerError -> error.message ?: fallback
            is MochiError.Unknown -> error.message ?: fallback
            else -> fallback
        }
    }
}
