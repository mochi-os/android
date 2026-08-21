// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList
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
import org.mochios.android.api.userMessage
import org.mochios.android.util.mergeMessages
import org.mochios.chess.engine.isDrawnPosition
import org.mochios.chess.model.Game
import org.mochios.chess.model.GameMessage
import org.mochios.chess.model.MoveRequest
import org.mochios.chess.repository.ChessRepository
import javax.inject.Inject

/**
 * Detail-screen state. [error] holds only load and refresh failures; mutation
 * failures surface as [ChessGameEvent.Toast].
 */
data class ChessGameUiState(
    val game: Game? = null,
    val identity: String = "",
    val messages: List<GameMessage> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: MochiError? = null,
    val lastMove: Pair<String, String>? = null,
    val pendingMove: Boolean = false,
    val isResigning: Boolean = false,
    val isDrawOffering: Boolean = false,
    val isDrawAccepting: Boolean = false,
    val isDrawDeclining: Boolean = false,
    val isDeleting: Boolean = false,
    val isRematching: Boolean = false,
    val isSendingChat: Boolean = false,
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

    init {
        load()
    }

    // ---- Loading ----

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val view = repo.getGame(gameId)
                val msgs = repo.getMessages(gameId)
                _uiState.value = _uiState.value.copy(
                    game = view.game,
                    identity = view.identity,
                    messages = msgs.messages.sortedBy { it.created },
                    hasMore = msgs.hasMore,
                    nextCursor = msgs.nextCursor,
                    lastMove = deriveLastMove(view.game.pgn),
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

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val view = repo.getGame(gameId)
                val msgs = repo.getMessages(gameId)
                _uiState.value = _uiState.value.copy(
                    game = view.game,
                    identity = view.identity,
                    // refresh() runs on every websocket frame: merge so
                    // loadMoreOlder's pages survive, and leave
                    // hasMore/nextCursor alone - they describe how far back we
                    // have paged.
                    messages = mergeMessages(
                        _uiState.value.messages,
                        msgs.messages,
                        key = ::messageKey,
                        created = { row -> row.created },
                    ),
                    lastMove = deriveLastMove(view.game.pgn),
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

    fun loadMoreOlder() {
        val cursor = _uiState.value.nextCursor ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val older = repo.getMessages(gameId, before = cursor)
                _uiState.value = _uiState.value.copy(
                    messages = mergeMessages(
                        _uiState.value.messages,
                        older.messages,
                        key = ::messageKey,
                        created = { row -> row.created },
                    ),
                    hasMore = older.hasMore,
                    nextCursor = older.nextCursor,
                    isLoadingMore = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
                emitToast(e.toMochiError())
            }
        }
    }

    // ---- Move submission ----

    /**
     * Compute the post-move FEN/PGN/SAN locally, apply it optimistically and
     * POST; a rejection triggers [refresh].
     */
    fun submitMove(from: String, to: String, promotion: String?) {
        val game = _uiState.value.game ?: return
        if (game.status != "active") return
        if (_uiState.value.pendingMove) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingMove = true)
            try {
                // Build a fresh board and apply the move locally.
                val board = Board()
                board.loadFromFen(game.fen)
                val moveText = buildMoveText(from, to, promotion)
                val move = Move(moveText, board.sideToMove)
                if (!board.isMoveLegal(move, true)) {
                    _uiState.value = _uiState.value.copy(pendingMove = false)
                    return@launch
                }
                val san = computeSan(game.fen, move)
                val ok = board.doMove(move)
                if (!ok) {
                    _uiState.value = _uiState.value.copy(pendingMove = false)
                    return@launch
                }
                val newFen = board.fen
                val newPgn = appendMoveToPgn(game.pgn, san, board)

                // Derive terminal-state hints from the post-move position.
                val mySide = if (game.white == _uiState.value.identity) Side.WHITE else Side.BLACK
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

                // Optimistically swap in the post-move state so the board
                // reflects the move while the request is in flight.
                _uiState.value = _uiState.value.copy(
                    game = game.copy(fen = newFen, pgn = newPgn),
                    lastMove = from to to,
                )

                repo.move(
                    game = gameId,
                    request = MoveRequest(
                        from = from,
                        to = to,
                        promotion = promotion,
                        fen = newFen,
                        pgn = newPgn,
                        san = san,
                        status = status,
                        winner = winner,
                    ),
                )
                // Refresh authoritative state (game.status / winner /
                // draw_offer reset / etc. updated by the server).
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(pendingMove = false)
                emitToast(e.toMochiError())
                refresh()
            } finally {
                _uiState.value = _uiState.value.copy(pendingMove = false)
            }
        }
    }

    // ---- Chat ----

    fun sendChat(body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingChat = true)
            try {
                repo.sendMessage(gameId, trimmed)
                refresh()
            } catch (e: Exception) {
                emitToast(e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isSendingChat = false)
            }
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
     * From/to of the last move, replayed from the full [pgn]. SAN only replays
     * from the starting position, so a single page of move messages would start
     * mid-game and throw or mis-resolve.
     */
    private fun deriveLastMove(pgn: String): Pair<String, String>? {
        if (pgn.isBlank()) return null
        return try {
            val list = MoveList()
            list.loadFromSan(pgn)
            val last = list.lastOrNull() ?: return null
            last.from.value().lowercase() to last.to.value().lowercase()
        } catch (_: Exception) {
            null
        }
    }

    /** Content key for chat dedupe; the websocket frame carries no id. */
    private fun messageKey(message: GameMessage): String =
        "${message.created}|${message.body}|${message.name}|${message.type}"

    private fun buildMoveText(from: String, to: String, promotion: String?): String {
        return if (promotion.isNullOrBlank()) "${from.lowercase()}${to.lowercase()}"
        else "${from.lowercase()}${to.lowercase()}${promotion.lowercase()}"
    }

    private fun computeSan(fenBefore: String, move: Move): String {
        return try {
            val board = Board()
            board.loadFromFen(fenBefore)
            val list = MoveList(fenBefore)
            list.add(move)
            val sans = list.toSanArray()
            sans.lastOrNull() ?: move.toString()
        } catch (_: Exception) {
            move.toString()
        }
    }

    /**
     * Append [san] to the server's header-less PGN, numbering each White move
     * from the post-move board.
     */
    private fun appendMoveToPgn(existing: String, san: String, postMoveBoard: Board): String {
        // halfMoveCounter resets on captures and pawn moves; the full-move
        // counter is the move number.
        val fullMove = postMoveBoard.moveCounter
        val sideAfter = postMoveBoard.sideToMove
        val sep = if (existing.isEmpty()) "" else " "
        val prefix = if (sideAfter == Side.BLACK) {
            // we just moved White → start a new move pair "N. san"
            "${fullMove}. $san"
        } else {
            // we just moved Black → append "san" to the running pair
            san
        }
        return existing + sep + prefix
    }
}

