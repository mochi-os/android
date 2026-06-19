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
import org.mochios.chess.model.Game
import org.mochios.chess.model.GameMessage
import org.mochios.chess.model.MoveRequest
import org.mochios.chess.repository.ChessRepository
import javax.inject.Inject

/**
 * UI state for the chess game-detail screen. Mirrors the web's `ChessGame`
 * (apps/chess/web/src/features/chess/index.tsx) view body.
 *
 *  - [game] is the authoritative game row (from `/-/view`). Refreshed on
 *    every successful mutation so we always reflect the server's idea of
 *    state.
 *  - [identity] is the caller's entity ID — captured from the view
 *    response (and cross-checked against [SessionManager.boundIdentity]).
 *  - [messages] is the chat / move / system message stream in display
 *    order (oldest-first, newest-last); [hasMore]/[nextCursor] drive the
 *    paginated load-older behaviour the same way the chat app does.
 *  - [lastMove] is the (from, to) pair of the most recent move; used to
 *    highlight those squares on the board until the next move is made.
 *  - [pendingMove] is set while a move request is in flight so the UI
 *    can disable further input without losing the optimistic state.
 *  - [error] holds the first surfaced failure for the initial load /
 *    refresh; per-mutation errors land in [transientToasts] so they
 *    surface as snackbars without sticking around.
 */
data class ChessGameUiState(
    val game: Game? = null,
    val identity: String = "",
    val messages: List<GameMessage> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long? = null,
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
 * Side-effect events emitted by the ViewModel. Toasts and one-shot
 * navigation actions live here so the screen can route them through a
 * snackbar / NavController without persisting them in [ChessGameUiState].
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
                    lastMove = deriveLastMove(msgs.messages),
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
                    messages = msgs.messages.sortedBy { it.created },
                    hasMore = msgs.hasMore,
                    nextCursor = msgs.nextCursor,
                    lastMove = deriveLastMove(msgs.messages),
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
                    messages = (older.messages + _uiState.value.messages).sortedBy { it.created },
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
     * Submit a move. Computes post-move FEN/PGN/SAN locally via chesslib
     * and POSTs to the server — same shape as the web client. Optimistically
     * updates the local FEN so the board flips to the opponent's turn
     * immediately; if the server rejects we re-fetch via [refresh] so the
     * UI returns to authoritative state.
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
                } else if (board.isDraw) {
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
     * Called by the screen when a WS event arrives. We re-fetch the game +
     * latest messages so the UI converges on the server view without
     * trying to surgically apply every event type — chess events are
     * infrequent (~1 per minute in an active game) so the extra
     * round-trip is well within budget.
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
     * Walk the message list backwards to find the last `"move"` entry and
     * derive its from/to pair. We only know the SAN from the server, so we
     * replay the moves from the starting position to learn the
     * coordinates. This is O(N) per refresh — the move list is at most a
     * few hundred entries even in a long game, so cheap enough.
     */
    private fun deriveLastMove(messages: List<GameMessage>): Pair<String, String>? {
        val moves = messages
            .asSequence()
            .filter { it.type == "move" }
            .sortedBy { it.created }
            .map { it.body }
            .toList()
        if (moves.isEmpty()) return null

        return try {
            val list = MoveList()
            for (san in moves) list.addSanMove(san, true, true)
            val last = list.lastOrNull() ?: return null
            last.from.value().lowercase() to last.to.value().lowercase()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildMoveText(from: String, to: String, promotion: String?): String {
        return if (promotion.isNullOrBlank()) "${from.lowercase()}${to.lowercase()}"
        else "${from.lowercase()}${to.lowercase()}${promotion.lowercase()}"
    }

    /**
     * Compute the SAN for a single move applied to [fenBefore]. We use a
     * fresh [Board] and let chesslib's [Move] / [MoveList] machinery do
     * the work: a [MoveList] backed by a starting FEN can convert its
     * entries to SAN strings via [MoveList.toSanArray].
     */
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
     * Naively append the SAN of the latest move to the existing PGN. The
     * server stores PGN as a flat concatenation of moves (no headers); each
     * move pair carries `N.` numbering. We re-derive from the post-move
     * board so the half-move counter stays correct.
     */
    private fun appendMoveToPgn(existing: String, san: String, postMoveBoard: Board): String {
        // halfMoveCounter resets on captures/pawn moves, so it isn't a
        // reliable move-number source. Walk the side-to-move + fullmove
        // number from the post-move board: after Black's move, postMove
        // side-to-move is White and full-move number has been bumped. Use
        // the full-move counter directly.
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

