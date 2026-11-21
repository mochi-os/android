// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.messageOrFallback
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.GameChat
import org.mochios.android.ui.components.GameChatPage
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.REOPEN
import org.mochios.go.engine.GoGame
import org.mochios.go.engine.IllegalMoveException
import org.mochios.go.engine.Score
import org.mochios.go.engine.Stone
import org.mochios.go.engine.lastStone
import org.mochios.go.model.Game
import org.mochios.go.model.GameMessage
import org.mochios.go.model.MoveRequest
import org.mochios.go.model.PassRequest
import org.mochios.go.repository.GoRepository
import javax.inject.Inject

/**
 * UI state for [GoGameDetailScreen]. [game] is the server record, refetched
 * after every mutation; [goGame] is the engine parsed from it, held in state so
 * the screen and [GoBoard] share one legality source; the chat is
 * [GoGameViewModel.chat]'s. [score] is set only once the game is finished.
 */
data class GoGameDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val game: Game? = null,
    val goGame: GoGame? = null,
    val myIdentity: String = "",
    val isMyTurn: Boolean = false,
    val score: Score? = null,
    val lastMove: Pair<Int, Int>? = null,
    val error: MochiError? = null,
    /** True while a `place(row, col)` request is in flight. */
    val isMoving: Boolean = false,
    /** True while a `passTurn()` request is in flight. */
    val isPassing: Boolean = false,
    /** True while the resign request is in flight. */
    val isResigning: Boolean = false,
    /** True while a score accept / resume is in flight. */
    val isScoreAccepting: Boolean = false,
    val isScoreResuming: Boolean = false,
    /** True while a draw-offer / accept / decline is in flight. */
    val isDrawOffering: Boolean = false,
    val isDrawAccepting: Boolean = false,
    val isDrawDeclining: Boolean = false,
    /** True while the delete request is in flight. */
    val isDeleting: Boolean = false,
    /** True while a rematch is being created. */
    val isCreatingRematch: Boolean = false,
)

sealed class GoGameDetailEvent {
    data class Toast(val message: String) : GoGameDetailEvent()
    data class OpenGame(val gameId: String) : GoGameDetailEvent()
    data object NavigateBack : GoGameDetailEvent()
}

/**
 * ViewModel for the game-detail screen; mirrors `GoGameView` in
 * `apps/go/web/src/features/go/index.tsx`.
 */
@HiltViewModel
class GoGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoRepository,
) : ViewModel() {

    val gameId: String = savedStateHandle.get<String>("gameId").orEmpty()

    /** The in-flight game fetch; cancelled when a newer one starts. */
    private var loadJob: Job? = null

    private var resumedBefore = false

    private val _state = MutableStateFlow(GoGameDetailUiState(isLoading = true))
    val state: StateFlow<GoGameDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GoGameDetailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GoGameDetailEvent> = _events.asSharedFlow()

    /** The game's chat; a refresh keeps the older pages already read. */
    val chat = GameChat(
        scope = viewModelScope,
        fetch = { before ->
            val page = repository.getMessages(gameId, before = before)
            GameChatPage(page.messages, page.more == true, page.cursor)
        },
        post = { body -> repository.sendMessage(gameId, body) },
        key = { row -> row.id },
        created = { row -> row.created },
        match = ::messageKey,
        report = { error -> _events.tryEmit(GoGameDetailEvent.Toast(error.userMessage())) },
    )

    init {
        if (gameId.isNotBlank()) {
            loadGame()
            loadMessages()
        }
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * The screen's ON_RESUME. The first arrives as the screen opens, while
     * init is already fetching, so only a later one - a return after frames
     * may have been missed - refetches.
     */
    fun onScreenResumed() {
        if (!resumedBefore) {
            resumedBefore = true
            return
        }
        refreshGame()
        refreshMessages()
    }

    /**
     * Fetch the game now, cancelling any pending fetch. For the first load and
     * a retry, where the user is waiting.
     */
    fun loadGame() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            fetchGame()
        }
    }

    /**
     * Refetch the game in the background: socket frames, action results and a
     * return to the screen. Cancels the previous fetch, so a slower earlier
     * response cannot put a stale position back on the board, and waits
     * [REFRESH_DEBOUNCE] first, so a request right behind - our own move's
     * echo lands with the move's response - cancels this one before it is sent.
     */
    private fun refreshGame() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            fetchGame()
        }
    }

    private suspend fun fetchGame() {
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
            // From the record, so the marker follows the opponent's reply too
            // and a game opened fresh shows one.
            val lastMove = goGame?.let { engine -> lastStone(game.sgf, engine) }
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    game = game,
                    goGame = goGame,
                    myIdentity = myIdentity,
                    isMyTurn = isMyTurn,
                    score = score,
                    lastMove = lastMove,
                    error = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Fetch the newest page of messages now, cancelling any pending fetch. */
    fun loadMessages() = chat.load()

    /**
     * Refetch the newest page of messages in the background, after
     * [REFRESH_DEBOUNCE], cancelling the previous fetch the way [refreshGame]
     * does. It folds into the pages already read rather than replacing them.
     */
    private fun refreshMessages() = chat.refresh()

    fun loadMoreMessages() = chat.loadMore()

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

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
                refreshGame()
                // Refresh the log so our own move row appears at once. The
                // server echoes our own frame too, so this races the echo; the
                // id-keyed merge keeps the row from appearing twice.
                refreshMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isMoving = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedMoveMessage)),
                )
                // Take the stone back: the server never took it, and the
                // refetch below fails as well when the connection is what
                // failed. Only while the board is still this move's - a
                // refetch or a frame may have replaced it meanwhile.
                _state.update {
                    if (it.goGame !== moved) {
                        it
                    } else {
                        it.copy(
                            goGame = snapshot.goGame,
                            game = snapshot.game,
                            isMyTurn = snapshot.isMyTurn,
                            lastMove = snapshot.lastMove,
                        )
                    }
                }
                // Then read the server's state, which holds the move after all
                // when only its answer was lost.
                refreshGame()
            }
        }
    }

    /**
     * Pass. On the second consecutive pass the score is computed locally and
     * sent so the server records the finish in one round trip.
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

        // Two passes put the game into scoring and send the count as a
        // proposal. The result belongs to the server, once both players accept
        // it: counting cannot tell a dead stone from a live one, so a player
        // who disagrees resumes play and captures them instead. Declaring
        // "finished" here would end a game the opponent never agreed was over.
        val scoreResult: Score? = if (isGameOver) newGame.score(game.komi) else null

        _state.update { it.copy(isPassing = true) }
        viewModelScope.launch {
            try {
                repository.pass(
                    gameId,
                    PassRequest(
                        fen = newGame.board,
                        sgf = newSgf,
                        status = if (isGameOver) "scoring" else null,
                        scoreBlack = scoreResult?.black,
                        scoreWhite = scoreResult?.white,
                    ),
                )
                _state.update { it.copy(isPassing = false) }
                refreshGame()
                // As in place(): our own frame is echoed back, so this races it
                // and the id-keyed merge deduplicates the result.
                refreshMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isPassing = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedPassMessage)),
                )
            }
        }
    }

    /**
     * Accept the proposed score. The server ends the game once both players
     * have; the first acceptance only records who agreed.
     */
    fun acceptScore(failedMessage: String) {
        val game = _state.value.game ?: return
        if (game.status != "scoring" || _state.value.isScoreAccepting) return
        _state.update { it.copy(isScoreAccepting = true) }
        viewModelScope.launch {
            try {
                repository.scoreAccept(gameId)
                _state.update { it.copy(isScoreAccepting = false) }
                loadGame()
                loadMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isScoreAccepting = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedMessage)),
                )
            }
        }
    }

    /** Refuse the proposed score and play on, which is how the rules of Go settle a disagreement about which stones are dead. */
    fun resumeScoring(failedMessage: String) {
        val game = _state.value.game ?: return
        if (game.status != "scoring" || _state.value.isScoreResuming) return
        _state.update { it.copy(isScoreResuming = true) }
        viewModelScope.launch {
            try {
                repository.scoreResume(gameId)
                _state.update { it.copy(isScoreResuming = false) }
                loadGame()
                loadMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isScoreResuming = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedMessage)),
                )
            }
        }
    }

    /**
     * Send [body]; [onFinished] hears whether it went, so the composer keeps
     * the words of one that failed, and the failure is said.
     */
    fun sendChat(body: String, failedSendMessage: String, onFinished: (Boolean) -> Unit) {
        chat.send(body) { error ->
            if (error != null) {
                _events.tryEmit(GoGameDetailEvent.Toast(error.messageOrFallback(failedSendMessage)))
            }
            onFinished(error == null)
        }
    }

    fun resign(failedResignMessage: String) {
        if (_state.value.isResigning) return
        _state.update { it.copy(isResigning = true) }
        viewModelScope.launch {
            try {
                repository.resign(gameId)
                _state.update { it.copy(isResigning = false) }
                refreshGame()
                refreshMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isResigning = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedResignMessage)),
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
                refreshGame()
            } catch (e: Exception) {
                _state.update { it.copy(isDrawOffering = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedOfferMessage)),
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
                refreshGame()
                refreshMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isDrawAccepting = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedAcceptMessage)),
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
                refreshGame()
                refreshMessages()
            } catch (e: Exception) {
                _state.update { it.copy(isDrawDeclining = false) }
                _events.tryEmit(
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedDeclineMessage)),
                )
            }
        }
    }

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
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedRematchMessage)),
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
                    GoGameDetailEvent.Toast(e.toMochiError().messageOrFallback(failedDeleteMessage)),
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // WebSocket integration — invoked by the screen on each event
    // ------------------------------------------------------------------

    /**
     * A row's content, matching an older server's frame, which carries no id,
     * to the stored row that replaces it. Rows are keyed by their ids.
     */
    private fun messageKey(message: GameMessage): String =
        "${message.created}|${message.body}|${message.name}|${message.type}"

    /**
     * Apply a websocket event: merge the row into the chat (deduplicated on
     * content, see [messageKey]) and refetch the game for anything that changes
     * state.
     */
    fun applyWsEvent(rawType: String, message: GameMessage?) {
        if (message != null) chat.receive(message)
        // Move and system events change the game state; "state" is the
        // snapshot event_sync emits after a P2P convergence repair, and
        // ignoring it left the board showing what the repair replaced, with
        // no pull-to-refresh on this screen to escape it.
        if (rawType == "move" || rawType == "system" || rawType == "state") {
            refreshGame()
        }
        // The socket was down: moves and messages sent meanwhile are gone.
        if (rawType == REOPEN) {
            refreshGame()
            refreshMessages()
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
}
