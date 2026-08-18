// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail

import kotlinx.coroutines.CancellationException
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.util.mergeMessage
import org.mochios.words.engine.BOARD_SIZE
import org.mochios.words.engine.DraftStatus
import org.mochios.words.engine.Placement
import org.mochios.words.engine.createDraftSignature
import org.mochios.words.engine.deriveMoveDraft
import org.mochios.words.engine.getUniqueDraftWords
import org.mochios.words.engine.parseBoard
import org.mochios.words.engine.serializeBoard
import org.mochios.words.model.Game
import org.mochios.words.model.GameMessage
import org.mochios.words.model.MoveRequest
import org.mochios.words.repository.WordsRepository
import javax.inject.Inject

/**
 * Per-word validation outcome surfaced to the move composer. Mirrors the
 * web `DraftWordValidationState` union — `CHECKING` while the request is
 * inflight, `VALID` / `INVALID` after the dictionary call settles, and
 * `UNKNOWN` when the validation request itself failed (server / network).
 */
enum class ValidState { CHECKING, VALID, INVALID, UNKNOWN }

/**
 * Where the user picked up a tile they're now dragging. Used by both the
 * board and the rack to render the drop targets and decide whether a drop
 * is a place / move / reorder / return-to-rack.
 */
sealed class DragSource {
    data class Rack(val index: Int) : DragSource()
    data class BoardCell(val row: Int, val col: Int) : DragSource()
}

/**
 * Drop targets the screen can resolve from a drag's final pointer position.
 * Used by the continuous-drag pipeline: the screen maps the release position
 * to one of these and dispatches to the matching ViewModel function.
 */
sealed class DropTarget {
    data class BoardCell(val row: Int, val col: Int) : DropTarget()
    data class RackSlot(val index: Int) : DropTarget()
    object None : DropTarget()
}

/**
 * Snapshot of everything the game-detail screen renders. Reduced from the
 * web `index.tsx`'s scattered `useState` hooks — same names where they
 * carry over for easier cross-referencing.
 */
data class WordsGameDetailUiState(
    val isLoading: Boolean = true,
    val isLoadingMessages: Boolean = false,
    val isLoadingMoreMessages: Boolean = false,
    val hasMoreMessages: Boolean = false,
    /** The server's own "<created>:<id>" cursor, not a derived timestamp. */
    val nextMessageCursor: String? = null,
    val error: MochiError? = null,
    val game: Game? = null,
    val myIdentity: String = "",
    val rackTiles: List<Char> = emptyList(),
    val pendingPlacements: List<Placement> = emptyList(),
    val selectedRackIndex: Int? = null,
    val exchangeMode: Boolean = false,
    val exchangeSelected: Set<Int> = emptySet(),
    val dragSource: DragSource? = null,
    val blankPromptOpen: Boolean = false,
    val pendingBlankCell: Pair<Int, Int>? = null,
    val pendingBlankRackIndex: Int? = null,
    val wordValidationState: Map<String, ValidState> = emptyMap(),
    val isValidationChecking: Boolean = false,
    val validationUnavailable: Boolean = false,
    val isSubmittingMove: Boolean = false,
    val isPassing: Boolean = false,
    val isExchanging: Boolean = false,
    val isResigning: Boolean = false,
    val isDeleting: Boolean = false,
    val isCreatingRematch: Boolean = false,
    val messages: List<GameMessage> = emptyList(),
    val showResignDialog: Boolean = false,
    val createdRematchId: String? = null,
    val gameDeleted: Boolean = false,
    val transientToast: String? = null,
)

/**
 * ViewModel for the game-detail screen. Holds the (board + rack + pending
 * placements + drag state + validation map) tuple and exposes every
 * mutation the screen needs as a void function — composables stay free of
 * coroutine plumbing.
 *
 * Live word validation is debounced 350ms after `pendingPlacements`
 * changes, then fires `repo.validateWord` in parallel for every unique
 * word the engine reports. The result is gated on a signature derived
 * from the (board, placement-set) pair so stale responses from earlier
 * keystrokes can't overwrite a fresher state. Matches the web
 * `useEffect` block at `index.tsx` lines 200-272.
 */
private const val TAG = "WordsGame"

@HiltViewModel
class WordsGameViewModel @Inject constructor(
    private val repository: WordsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val gameId: String = savedStateHandle.get<String>("gameId").orEmpty()

    private val _uiState = MutableStateFlow(WordsGameDetailUiState())
    val uiState: StateFlow<WordsGameDetailUiState> = _uiState.asStateFlow()

    private var validationJob: Job? = null

    init {
        load()
        loadMessages()
    }

    // ─── Load / refresh ────────────────────────────────────────────────

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = repository.getGame(gameId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        game = response.game,
                        myIdentity = response.identity,
                        rackTiles = response.game.my_rack.toList(),
                        pendingPlacements = emptyList(),
                        selectedRackIndex = null,
                        dragSource = null,
                        exchangeMode = false,
                        exchangeSelected = emptySet(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toMochiError()) }
            }
        }
    }

    /**
     * Refresh the game state without resetting local placement / rack
     * state — used after a websocket event signals an opponent's move
     * landed. The server returns the post-move rack already; we only
     * overwrite the rack + board if no pending placements would be lost.
     * If the player is mid-composition we keep the local state and let
     * the next user action push us back to a consistent state.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val response = repository.getGame(gameId)
                _uiState.update { state ->
                    // If there are pending placements, only update non-rack
                    // game data so the user doesn't lose their work on an
                    // opponent's chat message. Once the user's own move
                    // succeeds, `submitMove` clears placements and a fresh
                    // load supersedes this branch anyway.
                    if (state.pendingPlacements.isEmpty() && !state.exchangeMode) {
                        // Re-seed the rack only when the server's tiles differ
                        // as a multiset from what is on screen. Overwriting
                        // unconditionally threw away the user's shuffle on every
                        // opponent move and on every resume, which is the one
                        // people notice. Web compares the value the same way.
                        val incoming = response.game.my_rack.toList()
                        val same = incoming.sorted() == state.rackTiles.sorted()
                        state.copy(
                            game = response.game,
                            myIdentity = response.identity,
                            rackTiles = if (same) state.rackTiles else incoming,
                            selectedRackIndex = if (same) state.selectedRackIndex else null,
                        )
                    } else {
                        state.copy(
                            game = response.game,
                            myIdentity = response.identity,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Deliberately not surfaced: the websocket will retry and the
                // next user action refreshes again, so a toast here would fire
                // on transient drops. Logged so it is not wholly invisible.
                Log.d(TAG, "Background refresh failed", e)
            }
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMessages = true) }
            try {
                val response = repository.getMessages(gameId, before = null, limit = 100)
                // Server returns newest-first; render oldest-first.
                val ordered = response.messages.sortedBy { it.created }
                _uiState.update {
                    it.copy(
                        messages = ordered,
                        isLoadingMessages = false,
                        hasMoreMessages = response.hasMore == true,
                        nextMessageCursor = response.nextCursor,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Message load failed", e)
                _uiState.update { it.copy(isLoadingMessages = false) }
            }
        }
    }

    /**
     * Page in older chat. GameChatPanel has always supported this; the screen
     * passed hasMore = false and an empty onLoadMore, so the capability was
     * switched off and a game's chat stopped at its first hundred messages.
     */
    fun loadMoreMessages() {
        val current = _uiState.value
        if (current.isLoadingMoreMessages || !current.hasMoreMessages) return
        val cursor = current.nextMessageCursor ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreMessages = true) }
            try {
                val response = repository.getMessages(gameId, before = cursor)
                _uiState.update {
                    it.copy(
                        isLoadingMoreMessages = false,
                        messages = (response.messages + it.messages)
                            .distinctBy { message -> message.id }
                            .sortedBy { message -> message.created },
                        hasMoreMessages = response.hasMore == true,
                        nextMessageCursor = response.nextCursor,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Older messages failed to load", e)
                _uiState.update { it.copy(isLoadingMoreMessages = false) }
            }
        }
    }

    // ─── WebSocket bridge ──────────────────────────────────────────────

    /**
     * Bridge a `GameWsEvent` from the lib's controller into the local
     * state. Move + system events trigger a game refresh + message list
     * refresh; chat events append the new message to the in-memory list
     * without a round-trip.
     */
    fun onWebsocketEvent(type: String, message: GameMessage?) {
        when (type) {
            // "state" is the snapshot event_sync emits after a P2P convergence
            // repair. It used to fall through this when with no else, so the
            // board and scores sat on what the repair had replaced until the
            // next move or resume.
            "move", "system", "state" -> {
                // Refresh the game (board, rack, turn, scores) and message
                // history — the move payload includes the post-move state.
                refresh()
                loadMessages()
            }
            "message" -> {
                if (message != null) {
                    // The frame carries no id, so the screen synthesises one:
                    // it never matches the server uid the same row arrives with
                    // over REST, and two messages from one sender in the same
                    // second synthesise the SAME id, so an id-keyed guard threw
                    // the second away. Key on content, as the web does.
                    _uiState.update { state ->
                        state.copy(
                            messages = mergeMessage(
                                state.messages,
                                message,
                                key = ::messageKey,
                                created = { row -> row.created },
                            ),
                        )
                    }
                } else {
                    loadMessages()
                }
            }
        }
    }

    /** Content key for chat dedupe; see [onWebsocketEvent]. */
    private fun messageKey(message: GameMessage): String =
        "${message.created}|${message.body}|${message.name}|${message.type}"

    // ─── Rack + placement mutations ───────────────────────────────────

    fun selectRackTile(index: Int) {
        _uiState.update { state ->
            if (state.exchangeMode) return@update state
            val newSelection = if (state.selectedRackIndex == index) null else index
            state.copy(selectedRackIndex = newSelection)
        }
    }

    fun toggleExchange(index: Int) {
        _uiState.update { state ->
            if (!state.exchangeMode) return@update state
            val next = state.exchangeSelected.toMutableSet()
            if (!next.add(index)) next.remove(index)
            state.copy(exchangeSelected = next)
        }
    }

    /**
     * Try to place the currently-selected rack tile at (row, col). If the
     * selected tile is a blank ('_'), open the blank-letter picker instead;
     * the dialog's `selectBlankLetter` finishes the placement.
     */
    fun placeAtCursor(row: Int, col: Int) {
        val state = _uiState.value
        val index = state.selectedRackIndex ?: return
        val tile = state.rackTiles.getOrNull(index) ?: return

        if (tile == '_') {
            _uiState.update {
                it.copy(
                    blankPromptOpen = true,
                    pendingBlankCell = row to col,
                    pendingBlankRackIndex = index,
                )
            }
            return
        }

        val placement = Placement(row = row, col = col, letter = tile, rackTile = tile)
        _uiState.update {
            val newRack = it.rackTiles.toMutableList().apply { removeAt(index) }
            it.copy(
                pendingPlacements = it.pendingPlacements + placement,
                rackTiles = newRack,
                selectedRackIndex = null,
            )
        }
    }

    fun selectBlankLetter(letter: Char) {
        val state = _uiState.value
        val cell = state.pendingBlankCell ?: return
        val rackIdx = state.pendingBlankRackIndex ?: return
        // A blank defers its placement until the letter is chosen, so the drop
        // check has to be repeated here — the square can have been filled in
        // between, by this player or by a refresh landing an opponent's move.
        if (!canDropOn(cell.first, cell.second)) {
            cancelBlankPrompt()
            return
        }
        val upper = letter.uppercaseChar()
        val placement = Placement(row = cell.first, col = cell.second, letter = upper, rackTile = '_')
        _uiState.update {
            val newRack = it.rackTiles.toMutableList().apply { removeAt(rackIdx) }
            it.copy(
                pendingPlacements = it.pendingPlacements + placement,
                rackTiles = newRack,
                selectedRackIndex = null,
                blankPromptOpen = false,
                pendingBlankCell = null,
                pendingBlankRackIndex = null,
                dragSource = null,
            )
        }
    }

    fun cancelBlankPrompt() {
        _uiState.update {
            it.copy(
                blankPromptOpen = false,
                pendingBlankCell = null,
                pendingBlankRackIndex = null,
                dragSource = null,
            )
        }
    }

    fun removePlacement(row: Int, col: Int) {
        _uiState.update { state ->
            val idx = state.pendingPlacements.indexOfFirst { it.row == row && it.col == col }
            if (idx < 0) return@update state
            val removed = state.pendingPlacements[idx]
            state.copy(
                pendingPlacements = state.pendingPlacements.toMutableList().apply { removeAt(idx) },
                rackTiles = state.rackTiles + removed.rackTile,
            )
        }
    }

    fun recallPlacements() {
        _uiState.update { state ->
            if (state.pendingPlacements.isEmpty()) return@update state
            val returned = state.pendingPlacements.map { it.rackTile }
            state.copy(
                pendingPlacements = emptyList(),
                rackTiles = state.rackTiles + returned,
                selectedRackIndex = null,
            )
        }
    }

    fun shuffleRack() {
        _uiState.update { state ->
            if (state.rackTiles.size < 2) return@update state
            state.copy(rackTiles = state.rackTiles.shuffled())
        }
    }

    // ─── Drag and drop ────────────────────────────────────────────────

    fun onRackDragStart(index: Int) {
        _uiState.update { it.copy(dragSource = DragSource.Rack(index), selectedRackIndex = null) }
    }

    fun onBoardDragStart(row: Int, col: Int) {
        _uiState.update { it.copy(dragSource = DragSource.BoardCell(row, col), selectedRackIndex = null) }
    }

    fun onDragEnd() {
        _uiState.update { it.copy(dragSource = null) }
    }

    /** Drop the currently-dragged tile on (row, col). */
    /**
     * Whether a tile may be dropped on ([row], [col]).
     *
     * Rejects a square that already holds a board tile or a pending placement.
     * Without this the same square could take two placements: the engine builds
     * the board from the deduplicated square but counts tilesUsed from the
     * placement list, so two rack tiles were consumed for one visible tile, the
     * cross-word at that square was found and scored twice, and seven
     * placements could fire the fifty-point bingo across six distinct squares.
     * The board renders placements keyed by cell, so the second tile was
     * invisible and the player only saw it missing from their rack.
     *
     * [from] is the drag's own source cell, which does not block its own drop —
     * dragging a tile back where it started stays a no-op rather than an error.
     */
    private fun canDropOn(row: Int, col: Int, from: Pair<Int, Int>? = null): Boolean {
        val state = _uiState.value
        if (row !in 0 until BOARD_SIZE || col !in 0 until BOARD_SIZE) return false
        val board = state.game?.board?.let { parseBoard(it) } ?: return false
        if (board[row, col] != '.') return false
        return state.pendingPlacements.none {
            it.row == row && it.col == col && (from == null || it.row != from.first || it.col != from.second)
        }
    }

    fun onDropOnBoard(row: Int, col: Int) {
        val state = _uiState.value
        val source = state.dragSource ?: return
        val from = (source as? DragSource.BoardCell)?.let { it.row to it.col }
        if (!canDropOn(row, col, from)) {
            // Clear the drag so the tile returns to where it came from rather
            // than staying stuck to the pointer.
            _uiState.update { it.copy(dragSource = null) }
            return
        }
        when (source) {
            is DragSource.Rack -> {
                val tile = state.rackTiles.getOrNull(source.index) ?: return
                if (tile == '_') {
                    _uiState.update {
                        it.copy(
                            blankPromptOpen = true,
                            pendingBlankCell = row to col,
                            pendingBlankRackIndex = source.index,
                            dragSource = null,
                        )
                    }
                    return
                }
                val placement = Placement(row, col, tile, tile)
                _uiState.update {
                    val newRack = it.rackTiles.toMutableList().apply { removeAt(source.index) }
                    it.copy(
                        pendingPlacements = it.pendingPlacements + placement,
                        rackTiles = newRack,
                        dragSource = null,
                    )
                }
            }
            is DragSource.BoardCell -> {
                val existing = state.pendingPlacements.find { it.row == source.row && it.col == source.col }
                    ?: return
                _uiState.update {
                    val filtered = it.pendingPlacements.filterNot { p -> p.row == source.row && p.col == source.col }
                    it.copy(
                        pendingPlacements = filtered + existing.copy(row = row, col = col),
                        dragSource = null,
                    )
                }
            }
        }
    }

    /** Drop the currently-dragged tile back on the rack at [targetIndex]. */
    fun onDropOnRack(targetIndex: Int) {
        val state = _uiState.value
        val source = state.dragSource ?: return
        when (source) {
            is DragSource.BoardCell -> {
                val existing = state.pendingPlacements.find { it.row == source.row && it.col == source.col }
                    ?: return
                _uiState.update {
                    val filtered = it.pendingPlacements.filterNot { p -> p.row == source.row && p.col == source.col }
                    val newRack = it.rackTiles.toMutableList().apply {
                        val at = targetIndex.coerceAtMost(size)
                        add(at, existing.rackTile)
                    }
                    it.copy(
                        pendingPlacements = filtered,
                        rackTiles = newRack,
                        dragSource = null,
                    )
                }
            }
            is DragSource.Rack -> {
                if (source.index == targetIndex) {
                    _uiState.update { it.copy(dragSource = null) }
                    return
                }
                _uiState.update {
                    val rack = it.rackTiles.toMutableList()
                    val tile = rack.removeAt(source.index)
                    val at = targetIndex.coerceAtMost(rack.size)
                    rack.add(at, tile)
                    it.copy(rackTiles = rack, dragSource = null)
                }
            }
        }
    }

    // ─── Exchange / pass / resign / rematch / delete ──────────────────

    fun enterExchangeMode() {
        // Recall pending placements first so the rack is in a clean state.
        recallPlacements()
        _uiState.update {
            it.copy(
                exchangeMode = true,
                exchangeSelected = emptySet(),
                selectedRackIndex = null,
            )
        }
    }

    fun cancelExchange() {
        _uiState.update { it.copy(exchangeMode = false, exchangeSelected = emptySet()) }
    }

    fun confirmExchange() {
        val state = _uiState.value
        if (state.exchangeSelected.isEmpty()) return
        val tiles = state.exchangeSelected
            .sorted()
            .mapNotNull { state.rackTiles.getOrNull(it) }
            .joinToString("")
        if (tiles.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExchanging = true) }
            try {
                repository.exchange(gameId, tiles)
                _uiState.update { it.copy(isExchanging = false, exchangeMode = false, exchangeSelected = emptySet()) }
                load()
                loadMessages()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExchanging = false,
                        transientToast = e.toMochiError().userMessage(),
                    )
                }
            }
        }
    }

    fun passTurn() {
        if (_uiState.value.pendingPlacements.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPassing = true) }
            try {
                repository.pass(gameId)
                _uiState.update { it.copy(isPassing = false) }
                load()
                loadMessages()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPassing = false,
                        transientToast = e.toMochiError().userMessage(),
                    )
                }
            }
        }
    }

    fun submitMove() {
        val state = _uiState.value
        val game = state.game ?: return
        val board = parseBoard(game.board)
        val fallback = "Invalid move"
        val draft = deriveMoveDraft(board, state.pendingPlacements)
        if (draft.status != DraftStatus.READY) return
        val result = draft.result ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingMove = true) }
            try {
                val request = MoveRequest(
                    board = serializeBoard(result.newBoard),
                    score = result.totalScore,
                    tiles_used = result.tilesUsed,
                    words_formed = result.wordsFormed.joinToString(", ") { it.word },
                )
                repository.move(gameId, request)
                _uiState.update {
                    it.copy(
                        isSubmittingMove = false,
                        pendingPlacements = emptyList(),
                        selectedRackIndex = null,
                    )
                }
                load()
                loadMessages()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmittingMove = false,
                        transientToast = e.toMochiError().userMessage(),
                    )
                }
            }
        }
    }

    fun openResignDialog() {
        _uiState.update { it.copy(showResignDialog = true) }
    }

    fun dismissResignDialog() {
        _uiState.update { it.copy(showResignDialog = false) }
    }

    fun confirmResign() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResigning = true) }
            try {
                repository.resign(gameId)
                _uiState.update { it.copy(isResigning = false, showResignDialog = false) }
                load()
                loadMessages()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResigning = false,
                        showResignDialog = false,
                        transientToast = e.toMochiError().userMessage(),
                    )
                }
            }
        }
    }

    fun deleteGame() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            try {
                repository.deleteGame(gameId)
                _uiState.update { it.copy(isDeleting = false, gameDeleted = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        transientToast = e.toMochiError().userMessage(),
                    )
                }
            }
        }
    }

    /**
     * Open a new game with the same opponents and language as the current
     * one. Surfaces the new game ID through `createdRematchId` for the
     * screen to navigate to.
     */
    fun rematch() {
        val state = _uiState.value
        val game = state.game ?: return
        val me = state.myIdentity
        val opponents = mutableListOf<String>()
        listOf(game.player1, game.player2, game.player3 ?: "", game.player4 ?: "").forEach { id ->
            if (id.isNotEmpty() && id != me) opponents.add(id)
        }
        if (opponents.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingRematch = true) }
            try {
                val newId = repository.createGame(opponents, game.language)
                _uiState.update { it.copy(isCreatingRematch = false, createdRematchId = newId) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingRematch = false,
                        transientToast = e.toMochiError().userMessage(),
                    )
                }
            }
        }
    }

    fun consumeRematch() {
        _uiState.update { it.copy(createdRematchId = null) }
    }

    fun consumeToast() {
        _uiState.update { it.copy(transientToast = null) }
    }

    // ─── Sending chat messages ────────────────────────────────────────

    /**
     * [onFinished] reports whether the send succeeded, so the composer can
     * clear the draft only once it has. It used to clear synchronously at the
     * call site, before this coroutine had even started, so a failed send lost
     * the typed text with no feedback.
     */
    fun sendChatMessage(
        body: String,
        onError: (MochiError) -> Unit = {},
        onFinished: (Boolean) -> Unit = {},
    ) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            onFinished(false)
            return
        }
        viewModelScope.launch {
            try {
                repository.sendMessage(gameId, trimmed)
                loadMessages()
                onFinished(true)
            } catch (e: Exception) {
                onError(e.toMochiError())
                onFinished(false)
            }
        }
    }

    // ─── Live word validation ─────────────────────────────────────────

    /**
     * Debounced live validation of the draft's words. Cancels any in-flight
     * job, then kicks off a 350ms delay before parallel-firing
     * `validateWord` for each unique word. Late responses are dropped via
     * a (board, placement-set) signature check matching the web behaviour.
     */
    fun refreshWordValidation() {
        val state = _uiState.value
        val game = state.game

        validationJob?.cancel()

        // Skip validation entirely when in exchange mode or no game.
        if (state.exchangeMode || game == null) {
            _uiState.update {
                it.copy(
                    wordValidationState = emptyMap(),
                    isValidationChecking = false,
                    validationUnavailable = false,
                )
            }
            return
        }

        val board = parseBoard(game.board)
        val draft = deriveMoveDraft(board, state.pendingPlacements)
        if (draft.status != DraftStatus.READY) {
            _uiState.update {
                it.copy(
                    wordValidationState = emptyMap(),
                    isValidationChecking = false,
                    validationUnavailable = false,
                )
            }
            return
        }

        val result = draft.result ?: return
        val uniqueWords = getUniqueDraftWords(result.wordsFormed)
        if (uniqueWords.isEmpty()) {
            _uiState.update {
                it.copy(
                    wordValidationState = emptyMap(),
                    isValidationChecking = false,
                    validationUnavailable = false,
                )
            }
            return
        }

        val initial = uniqueWords.associateWith { ValidState.CHECKING }
        _uiState.update {
            it.copy(
                wordValidationState = initial,
                isValidationChecking = true,
                validationUnavailable = false,
            )
        }

        val signature = createDraftSignature(game.board, state.pendingPlacements)
        val language = game.language.ifEmpty { "en_US" }

        validationJob = viewModelScope.launch {
            delay(350)
            try {
                val results = uniqueWords.map { word ->
                    async {
                        word to try {
                            val valid = repository.validateWord(word, language)
                            if (valid) ValidState.VALID else ValidState.INVALID
                        } catch (_: Exception) {
                            ValidState.UNKNOWN
                        }
                    }
                }.awaitAll()

                // Drop stale results — only apply if the (board, placements)
                // signature still matches the one we kicked off with.
                val now = _uiState.value
                if (now.game == null) return@launch
                val nowSignature = createDraftSignature(now.game.board, now.pendingPlacements)
                if (nowSignature != signature) return@launch

                val map = results.toMap()
                val hasUnavailable = map.values.any { it == ValidState.UNKNOWN }
                _uiState.update {
                    it.copy(
                        wordValidationState = map,
                        isValidationChecking = false,
                        validationUnavailable = hasUnavailable,
                    )
                }
            } catch (e: CancellationException) {
                // A newer refresh replaced this one. Rethrow: swallowing it
                // leaves the coroutine looking like it completed normally.
                throw e
            } catch (_: Exception) {
                // Any other failure leaves the state as-is for the next refresh
                // to overwrite, so a lookup outage cannot strand the composer.
                _uiState.update { it.copy(isValidationChecking = false) }
            }
        }
    }
}

