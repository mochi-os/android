// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.GameChatInput
import org.mochios.android.ui.components.GameChatMessage
import org.mochios.android.ui.components.GameChatPanel
import org.mochios.android.ui.components.GameHeader
import org.mochios.android.ui.components.GameHeaderStat
import org.mochios.android.ws.rememberGameWebSocket
import org.mochios.words.R
import org.mochios.words.engine.BOARD_SIZE
import org.mochios.words.engine.DraftStatus
import org.mochios.words.engine.MoveDraft
import org.mochios.words.engine.deriveMoveDraft
import org.mochios.words.engine.getLetterValue
import org.mochios.words.engine.parseBoard
import org.mochios.words.model.Game
import org.mochios.words.model.GameMessage
import org.mochios.words.ui.detail.board.BlankTileDialog
import org.mochios.words.ui.detail.board.MoveComposer
import org.mochios.words.ui.detail.board.TileRack
import org.mochios.words.ui.detail.board.WordsBoard
import org.mochios.android.R as MochiR

/**
 * Words game-detail screen. Renders the 15x15 board, the player's rack,
 * the move-composer action bar, and the chat panel side-by-side (or
 * behind a sheet on narrow screens).
 *
 * The screen reads everything from [WordsGameViewModel] — including the
 * board state, the per-cell pending placements, the rack, the drag
 * source, the live word-validation state map, and any in-flight server
 * call. A `rememberGameWebSocket` subscription drives refresh on
 * opponent moves and chat messages.
 *
 * Layout: on screens wide enough for the chat panel (≥600dp) the chat
 * lives on the right at a fixed 320dp width; the rest of the screen is
 * the board+rack+composer column. On narrow screens the chat is hidden
 * behind a bottom sheet opened from the GameHeader actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsGameDetailScreen(
    @Suppress("UNUSED_PARAMETER") gameId: String,
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onLogout: () -> Unit = {},
    viewModel: WordsGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val game = state.game

    // ─── Lifecycle: refresh on resume ──────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // ─── Live word validation ────────────────────────────────────────
    // Re-derive validation whenever the placements / exchange-mode flag
    // changes. The ViewModel debounces internally (350ms).
    LaunchedEffect(state.pendingPlacements, state.exchangeMode, state.game?.board, state.game?.language) {
        viewModel.refreshWordValidation()
    }

    // ─── WebSocket bridge ────────────────────────────────────────────
    val controller = rememberGameWebSocket(game?.key)
    LaunchedEffect(controller) {
        controller?.events?.collect { event ->
            val msg = if (event.type == "message") {
                GameMessage(
                    id = (event.raw["id"] as? String) ?: "ws_${event.created}_${event.member ?: ""}",
                    game = state.game?.id.orEmpty(),
                    member = event.member.orEmpty(),
                    name = event.name.orEmpty(),
                    body = event.body.orEmpty(),
                    type = event.type,
                    created = event.created,
                )
            } else null
            viewModel.onWebsocketEvent(event.type, msg)
        }
    }

    // ─── Rematch navigation ──────────────────────────────────────────
    // When a rematch is created the view model surfaces the new game id;
    // we navigate back and let the list/sidebar route to the new detail.
    LaunchedEffect(state.createdRematchId) {
        if (state.createdRematchId != null) {
            viewModel.consumeRematch()
            onBack()
        }
    }
    LaunchedEffect(state.gameDeleted) {
        if (state.gameDeleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.words_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && game == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && game == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.error!!.userMessage(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                game != null -> GameDetailContent(
                    state = state,
                    game = game,
                    viewModel = viewModel,
                    onOpenNotifications = onOpenNotifications,
                )
            }
        }
    }

    if (state.blankPromptOpen) {
        BlankTileDialog(
            onSelect = { letter -> viewModel.selectBlankLetter(letter) },
            onDismiss = { viewModel.cancelBlankPrompt() },
        )
    }

    if (state.showResignDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResignDialog() },
            title = { Text(stringResource(R.string.words_detail_resign_title)) },
            text = { Text(stringResource(R.string.words_detail_resign_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmResign() },
                    enabled = !state.isResigning,
                ) {
                    if (state.isResigning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                    }
                    Text(stringResource(R.string.words_detail_action_resign))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissResignDialog() }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailContent(
    state: WordsGameDetailUiState,
    game: Game,
    viewModel: WordsGameViewModel,
    @Suppress("UNUSED_PARAMETER") onOpenNotifications: () -> Unit,
) {
    val myIdentity = state.myIdentity
    val isActive = game.status == "active"
    val isMyTurn = isActive && game.current_turn == game.my_player_number

    // Derive board, draft, scores from the engine.
    val board = remember(game.board) { parseBoard(game.board) }
    val invalidMoveFallback = stringResource(R.string.words_detail_invalid_move)
    val moveDraft: MoveDraft = remember(board, state.pendingPlacements, invalidMoveFallback) {
        deriveMoveDraft(board, state.pendingPlacements, invalidMoveFallback)
    }
    val draftWords: List<Pair<String, Int>> = remember(moveDraft) {
        if (moveDraft.status == DraftStatus.READY) {
            moveDraft.result!!.wordsFormed.map { it.word to it.score }
        } else emptyList()
    }
    val draftScore: Int = remember(moveDraft) {
        if (moveDraft.status == DraftStatus.READY) moveDraft.result!!.totalScore else 0
    }
    val canSubmit = isMyTurn &&
        !state.exchangeMode &&
        moveDraft.status == DraftStatus.READY &&
        !state.isSubmittingMove
    val canRecallMove = isMyTurn && state.pendingPlacements.isNotEmpty() && !state.isSubmittingMove

    val header = buildHeaderModel(game, myIdentity)

    // ─── Continuous drag-and-drop ─────────────────────────────────────
    // Track the live drag pointer in root coordinates, the bounds of the
    // board and rack in root coordinates, and a "ghost tile" descriptor
    // for screen-level rendering. The board/rack composables report their
    // bounds + slot rects via callbacks; the pointer is updated on every
    // drag delta. Release dispatches to the appropriate ViewModel method
    // based on which target rect the pointer is over.
    var boardBounds by remember { mutableStateOf<Rect?>(null) }
    var boardCellSize by remember { mutableStateOf(0f) }
    var rackBounds by remember { mutableStateOf<Rect?>(null) }
    var rackSlotBounds by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    var dragLetter by remember { mutableStateOf<Char?>(null) }
    var dragRackChar by remember { mutableStateOf<Char?>(null) }

    fun resolveTarget(pos: Offset): DropTarget {
        val r = rackBounds
        if (r != null && r.contains(pos)) {
            // Pointer is on the rack — pick the closest slot by x.
            val slots = rackSlotBounds
            if (slots.isNotEmpty()) {
                var bestIndex = 0
                var bestDist = Float.MAX_VALUE
                for ((i, s) in slots.withIndex()) {
                    if (s == Rect.Zero) continue
                    val cx = (s.left + s.right) / 2f
                    val d = kotlin.math.abs(pos.x - cx)
                    if (d < bestDist) {
                        bestDist = d
                        bestIndex = i
                    }
                }
                return DropTarget.RackSlot(bestIndex)
            }
            return DropTarget.None
        }
        val b = boardBounds
        if (b != null && b.contains(pos) && boardCellSize > 0f) {
            val col = ((pos.x - b.left) / boardCellSize).toInt().coerceIn(0, BOARD_SIZE - 1)
            val row = ((pos.y - b.top) / boardCellSize).toInt().coerceIn(0, BOARD_SIZE - 1)
            return DropTarget.BoardCell(row, col)
        }
        return DropTarget.None
    }

    fun finishDrag() {
        val pos = dragPointer
        val target = if (pos != null) resolveTarget(pos) else DropTarget.None
        when (target) {
            is DropTarget.BoardCell -> viewModel.onDropOnBoard(target.row, target.col)
            is DropTarget.RackSlot -> viewModel.onDropOnRack(target.index)
            DropTarget.None -> viewModel.onDragEnd()
        }
        dragPointer = null
        dragLetter = null
        dragRackChar = null
    }

    fun cancelDrag() {
        viewModel.onDragEnd()
        dragPointer = null
        dragLetter = null
        dragRackChar = null
    }

    // Anchor the ghost-tile overlay coordinates. The root-space drag pointer
    // needs to be converted into this BoxWithConstraints's local space before
    // it's used as a Modifier.offset, otherwise the offset would be relative
    // to the wrong origin (e.g. shifted up by the TopAppBar).
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft },
    ) {
        val showChatInline = maxWidth >= 600.dp
        var showMobileChat by remember { mutableStateOf(false) }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
            ) {
                WordsGameHeader(
                    header = header,
                    game = game,
                    isMyTurn = isMyTurn,
                    pendingCount = state.pendingPlacements.size,
                    exchangeMode = state.exchangeMode,
                    isActive = isActive,
                    isRematchInflight = state.isCreatingRematch,
                    onShuffle = { viewModel.shuffleRack() },
                    onPass = { viewModel.passTurn() },
                    onToggleExchange = {
                        if (state.exchangeMode) viewModel.cancelExchange()
                        else viewModel.enterExchangeMode()
                    },
                    onResign = { viewModel.openResignDialog() },
                    onRematch = { viewModel.rematch() },
                    onDelete = { viewModel.deleteGame() },
                    onOpenChat = if (!showChatInline) {
                        { showMobileChat = true }
                    } else null,
                )

                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WordsBoard(
                        board = board,
                        pendingPlacements = state.pendingPlacements,
                        selectedRackIndex = state.selectedRackIndex,
                        isMyTurn = isMyTurn,
                        gameStatus = game.status,
                        onCellClick = { row, col -> viewModel.placeAtCursor(row, col) },
                        onRemovePlacement = { row, col -> viewModel.removePlacement(row, col) },
                        dragSource = state.dragSource,
                        onBoardDragStart = { row, col, rootPos ->
                            val placement = state.pendingPlacements.firstOrNull {
                                it.row == row && it.col == col
                            }
                            if (placement != null) {
                                viewModel.onBoardDragStart(row, col)
                                dragLetter = placement.letter.uppercaseChar()
                                dragRackChar = placement.rackTile
                                dragPointer = rootPos
                            }
                        },
                        onDrag = { rootPos -> dragPointer = rootPos },
                        onDragEndAt = { _ -> finishDrag() },
                        onDragCancel = { cancelDrag() },
                        onBoundsChanged = { rect, cellSize ->
                            boardBounds = rect
                            boardCellSize = cellSize
                        },
                        dragPointer = dragPointer,
                    )
                }

                if (isActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 576.dp),
                        ) {
                            TileRack(
                                tiles = state.rackTiles,
                                selectedIndex = state.selectedRackIndex,
                                onSelectTile = { i -> viewModel.selectRackTile(i) },
                                disabled = !isMyTurn,
                                exchangeMode = state.exchangeMode,
                                exchangeSelected = state.exchangeSelected,
                                onToggleExchange = { i -> viewModel.toggleExchange(i) },
                                draggingIndex = (state.dragSource as? DragSource.Rack)?.index,
                                onRackDragStart = { index, rootPos ->
                                    val tile = state.rackTiles.getOrNull(index)
                                    if (tile != null) {
                                        viewModel.onRackDragStart(index)
                                        dragLetter = if (tile == '_') '?' else tile.uppercaseChar()
                                        dragRackChar = tile
                                        dragPointer = rootPos
                                    }
                                },
                                onDrag = { rootPos -> dragPointer = rootPos },
                                onDragEndAt = { _ -> finishDrag() },
                                onDragCancel = { cancelDrag() },
                                onBoundsChanged = { rect, slots ->
                                    rackBounds = rect
                                    rackSlotBounds = slots
                                },
                                dragPointer = dragPointer,
                            )

                            MoveComposer(
                                pendingPlacements = state.pendingPlacements.size,
                                exchangeMode = state.exchangeMode,
                                exchangeSelected = state.exchangeSelected.size,
                                moveDraft = moveDraft,
                                draftWords = draftWords,
                                wordValidationState = state.wordValidationState,
                                draftScore = draftScore,
                                onRecall = { viewModel.recallPlacements() },
                                onSubmit = { viewModel.submitMove() },
                                onExchangeConfirm = { viewModel.confirmExchange() },
                                onExchangeCancel = { viewModel.cancelExchange() },
                                canSubmit = canSubmit,
                                canRecallMove = canRecallMove,
                                isSubmitting = state.isSubmittingMove,
                                isExchanging = state.isExchanging,
                            )
                        }
                    }
                }
            }

            if (showChatInline) {
                Surface(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    GameChatColumn(
                        messages = state.messages,
                        myIdentity = myIdentity,
                        isLoading = state.isLoadingMessages,
                        onSend = { body -> viewModel.sendChatMessage(body) },
                    )
                }
            }
        }

        // Ghost tile: render the dragged tile at the live pointer position,
        // floating above everything else. Use board cell size when over the
        // board, otherwise the rack tile size, so the ghost feels the same
        // weight as the slot it left.
        val ghostLetter = dragLetter
        val ghostRack = dragRackChar
        val ghostPos = dragPointer
        if (ghostLetter != null && ghostRack != null && ghostPos != null) {
            val density = LocalDensity.current
            // Pick a sensible tile size: match board cell if pointer is over
            // the board, else 40dp (rack tile width).
            val px = with(density) {
                val overBoard = boardBounds?.contains(ghostPos) == true && boardCellSize > 0f
                if (overBoard) boardCellSize else 40.dp.toPx()
            }
            val sizeDp = with(density) { px.toDp() }
            // Convert root → overlay-local by subtracting the
            // BoxWithConstraints's origin; centre the ghost on the pointer.
            val localX = ghostPos.x - overlayOrigin.x
            val localY = ghostPos.y - overlayOrigin.y
            val offsetX = (localX - px / 2f).roundToInt()
            val offsetY = (localY - px / 2f).roundToInt()
            Box(
                modifier = Modifier
                    .zIndex(10f)
                    .offset { IntOffset(offsetX, offsetY) }
                    .size(sizeDp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(GHOST_TILE_BG)
                    .border(
                        width = 2.dp,
                        color = GHOST_TILE_BORDER,
                        shape = RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val display = when {
                    ghostRack == '_' && ghostLetter == '?' -> ""
                    else -> ghostLetter.toString()
                }
                Text(
                    text = display,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (sizeDp.value * 0.4f).sp,
                    ),
                    color = Color(0xFF1F1300),
                )
                if (ghostRack != '_') {
                    val value = getLetterValue(ghostRack)
                    if (value > 0) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = (sizeDp.value * 0.18f).sp,
                            ),
                            color = Color(0xFF555555),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 3.dp, bottom = 2.dp),
                        )
                    }
                } else {
                    Text(
                        text = "?",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = (sizeDp.value * 0.35f).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFF888888),
                    )
                }
            }
        }

        if (!showChatInline && showMobileChat) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showMobileChat = false },
                sheetState = sheetState,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                ) {
                    GameChatColumn(
                        messages = state.messages,
                        myIdentity = myIdentity,
                        isLoading = state.isLoadingMessages,
                        onSend = { body -> viewModel.sendChatMessage(body) },
                    )
                }
            }
        }
    }
}

private val GHOST_TILE_BG = Color(0xFFFBBF24)
private val GHOST_TILE_BORDER = Color(0xFFD97706)

@Composable
private fun WordsGameHeader(
    header: WordsHeaderModel,
    @Suppress("UNUSED_PARAMETER") game: Game,
    isMyTurn: Boolean,
    pendingCount: Int,
    exchangeMode: Boolean,
    isActive: Boolean,
    isRematchInflight: Boolean,
    onShuffle: () -> Unit,
    onPass: () -> Unit,
    onToggleExchange: () -> Unit,
    onResign: () -> Unit,
    onRematch: () -> Unit,
    onDelete: () -> Unit,
    onOpenChat: (() -> Unit)?,
) {
    GameHeader(
        title = header.title,
        status = header.status,
        myTurn = if (isActive) isMyTurn else null,
        stats = {
            for (player in header.players) {
                GameHeaderStat(
                    label = player.label,
                    value = player.score.toString(),
                    isHighlighted = player.isCurrentTurn,
                    isMe = player.isMe,
                )
            }
            GameHeaderStat(
                label = header.tilesLeftLabel,
            )
        },
        actions = {
            if (onOpenChat != null) {
                IconButton(onClick = onOpenChat) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = stringResource(R.string.words_detail_action_open_chat),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = stringResource(R.string.words_detail_action_more),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    if (isActive) {
                        if (isMyTurn) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.words_detail_action_shuffle)) },
                                onClick = { menuOpen = false; onShuffle() },
                                leadingIcon = {
                                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                                },
                            )
                        }
                        if (isMyTurn && pendingCount == 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.words_detail_action_pass)) },
                                onClick = { menuOpen = false; onPass() },
                                leadingIcon = {
                                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                                },
                            )
                        }
                        if (isMyTurn) {
                            DropdownMenuItem(
                                text = {
                                    val label = if (exchangeMode) {
                                        stringResource(R.string.words_detail_action_cancel_exchange)
                                    } else {
                                        stringResource(R.string.words_detail_action_exchange)
                                    }
                                    Text(label)
                                },
                                onClick = { menuOpen = false; onToggleExchange() },
                                leadingIcon = {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.words_detail_action_resign)) },
                            onClick = { menuOpen = false; onResign() },
                            leadingIcon = {
                                Icon(Icons.Filled.Flag, contentDescription = null)
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.words_detail_action_rematch)) },
                            onClick = { menuOpen = false; onRematch() },
                            enabled = !isRematchInflight,
                            leadingIcon = {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.words_detail_action_delete)) },
                            onClick = { menuOpen = false; onDelete() },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun GameChatColumn(
    messages: List<GameMessage>,
    myIdentity: String,
    isLoading: Boolean,
    onSend: (String) -> Unit,
) {
    val chatMessages = remember(messages) {
        messages.map { msg ->
            GameChatMessage(
                id = msg.id,
                game = msg.game,
                member = msg.member,
                name = msg.name,
                body = msg.body,
                type = msg.type,
                event = msg.event,
                created = msg.created,
            )
        }
    }
    var chatDraft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.words_detail_chat_title),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
        ) {
            GameChatPanel(
                messages = chatMessages,
                currentUserIdentity = myIdentity,
                isLoading = isLoading,
                isError = false,
                hasMore = false,
                isLoadingMore = false,
                onLoadMore = {},
                onRetry = {},
                moveMessageRenderer = { msg, isSent ->
                    {
                        WordsMoveRow(msg = msg, isSent = isSent)
                    }
                },
                systemMessageRenderer = { msg ->
                    {
                        WordsSystemRow(msg)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        GameChatInput(
            text = chatDraft,
            onTextChange = { chatDraft = it },
            onSend = {
                if (chatDraft.isNotBlank()) {
                    isSending = true
                    onSend(chatDraft)
                    chatDraft = ""
                    isSending = false
                }
            },
            isSending = isSending,
        )
    }
}

@Composable
private fun WordsMoveRow(msg: GameChatMessage, isSent: Boolean) {
    val text = if (isSent) {
        stringResource(R.string.words_detail_chat_you_played, msg.body)
    } else {
        stringResource(R.string.words_detail_chat_player_played, msg.name, msg.body)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WordsSystemRow(message: GameChatMessage) {
    // Localise per viewer from the structured event kind; legacy rows (and
    // REST-loaded resigns, which don't persist the event column) fall back to
    // the server-stored English body. Words only emits a resign system event.
    val text = when (message.event) {
        "resign" -> stringResource(MochiR.string.game_system_resign, message.name)
        else -> message.body
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Header model ─────────────────────────────────────────────────────

/**
 * Per-player header line. Mirrors the web `WordsHeaderPlayer` interface.
 */
data class WordsHeaderPlayer(
    val playerNumber: Int,
    val label: String,
    val score: Int,
    val isCurrentTurn: Boolean,
    val isMe: Boolean,
)

/**
 * Full header model. Built by [buildHeaderModel] from a [Game] +
 * `myIdentity`; rendered by [WordsGameHeader].
 */
data class WordsHeaderModel(
    val title: String,
    val status: String,
    val players: List<WordsHeaderPlayer>,
    val tilesLeftLabel: String,
)

@Composable
private fun buildHeaderModel(game: Game, myIdentity: String): WordsHeaderModel {
    val context = LocalContext.current
    val playerName = { num: Int ->
        val raw = when (num) {
            1 -> game.player1_name
            2 -> game.player2_name
            3 -> game.player3_name ?: ""
            4 -> game.player4_name ?: ""
            else -> ""
        }
        raw.ifBlank { context.getString(R.string.words_detail_player_fallback, num) }
    }
    val playerIdentity = { num: Int ->
        when (num) {
            1 -> game.player1
            2 -> game.player2
            3 -> game.player3 ?: ""
            4 -> game.player4 ?: ""
            else -> ""
        }
    }
    val isMeForPlayer = { num: Int ->
        if (myIdentity.isNotEmpty()) playerIdentity(num) == myIdentity
        else num == game.my_player_number
    }
    val isMyWin = run {
        val winner = game.winner ?: return@run false
        if (myIdentity.isNotEmpty()) winner == myIdentity
        else winner == playerIdentity(game.my_player_number)
    }

    val titleBase = (1..game.player_count)
        .filterNot { isMeForPlayer(it) }
        .joinToString(", ") { playerName(it) }
    val title = if (game.player_count > 2) "$titleBase (${game.player_count}p)" else titleBase

    val status: String = when (game.status) {
        "active" -> {
            if (game.current_turn == game.my_player_number) {
                context.getString(R.string.words_detail_status_your_move)
            } else {
                context.getString(R.string.words_detail_status_opponent_move, playerName(game.current_turn))
            }
        }
        "finished" -> {
            if (isMyWin) context.getString(R.string.words_detail_status_you_win)
            else {
                val winnerNum = (1..game.player_count).firstOrNull {
                    playerIdentity(it) == game.winner
                }
                if (winnerNum != null) {
                    context.getString(R.string.words_detail_status_winner_wins, playerName(winnerNum))
                } else {
                    context.getString(R.string.words_detail_status_game_over)
                }
            }
        }
        else -> {
            if (isMyWin) context.getString(R.string.words_detail_status_opponent_resigned)
            else context.getString(R.string.words_detail_status_you_resigned)
        }
    }

    val players: List<WordsHeaderPlayer> = (1..game.player_count).map { num ->
        val score = when (num) {
            1 -> game.player1_score
            2 -> game.player2_score
            3 -> game.player3_score
            4 -> game.player4_score
            else -> 0
        }
        val isMe = isMeForPlayer(num)
        WordsHeaderPlayer(
            playerNumber = num,
            label = if (isMe) context.getString(R.string.words_detail_label_you)
            else playerName(num),
            score = score,
            isCurrentTurn = game.status == "active" && game.current_turn == num,
            isMe = isMe,
        )
    }

    return WordsHeaderModel(
        title = title,
        status = status,
        players = players,
        tilesLeftLabel = context.getString(R.string.words_detail_label_tiles_left, game.bag_count),
    )
}
