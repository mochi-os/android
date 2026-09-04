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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.GameChatMessage
import org.mochios.android.ui.components.GameChatPanel
import org.mochios.android.ui.components.GameStatusBar
import org.mochios.android.ui.components.GameHeaderStat
import org.mochios.android.ui.components.GameTopBarTitle
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ws.rememberStreamWebSocket
import org.mochios.words.R
import org.mochios.words.engine.BOARD_SIZE
import org.mochios.words.engine.DraftStatus
import org.mochios.words.engine.MoveDraft
import org.mochios.words.engine.deriveMoveDraft
import org.mochios.words.engine.getLetterValue
import org.mochios.words.engine.parseBoard
import org.mochios.words.model.Game
import org.mochios.words.model.GameMessage
import org.mochios.words.ui.detail.board.MoveActions
import org.mochios.words.ui.detail.board.MoveFeedback
import org.mochios.words.ui.detail.board.TileRack
import org.mochios.words.ui.detail.board.WordsBoard
import org.mochios.words.ui.router.WORDS_FEATURE
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsGameDetailScreen(
    @Suppress("UNUSED_PARAMETER") gameId: String,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenNotifications: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onLogout: () -> Unit = {},
    onOpenDrawer: () -> Unit,
    viewModel: WordsGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val game = state.game
    val snackbar = remember { SnackbarHostState() }

    // Every mutation writes its failure here — a rejected move, pass, exchange,
    // resign, delete or rematch. Nothing rendered it, so all six presented as
    // "nothing happened".
    LaunchedEffect(state.transientToast) {
        val toast = state.transientToast
        if (toast != null) {
            snackbar.showSnackbar(toast)
            viewModel.consumeToast()
        }
    }

    // ─── Lifecycle: refresh on resume ──────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ─── Live word validation ────────────────────────────────────────
    // Re-derive validation whenever the placements / exchange-mode flag
    // changes. The ViewModel debounces internally (350ms).
    LaunchedEffect(state.pendingPlacements, state.exchangeMode, state.game?.board, state.game?.language) {
        viewModel.refreshWordValidation()
    }

    // ─── WebSocket bridge ────────────────────────────────────────────
    val controller = rememberStreamWebSocket(game?.key)
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
    // The view model surfaces the new game's id; open it, the way chess and go
    // do. Going back instead dropped the id and left the finished game on
    // screen, with nothing pointing at the game just created.
    LaunchedEffect(state.createdRematchId) {
        val rematchId = state.createdRematchId
        if (rematchId != null) {
            viewModel.consumeRematch()
            onOpenGame(rematchId)
        }
    }
    LaunchedEffect(state.gameDeleted) {
        if (state.gameDeleted) {
            LastViewedStore.clear(context, WORDS_FEATURE)
            onBack()
        }
    }

    val isActive = game?.status == "active"
    val isMyTurn = isActive && game != null && game.current_turn == game.my_player_number
    val header = game?.let { current -> buildHeaderModel(current, state.myIdentity) }
    var showMobileChat by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // The board pane keeps its own BoxWithConstraints for layout; the top bar
    // needs the same answer a composition earlier, so it asks the window.
    val twoPane = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (header != null) {
                        GameTopBarTitle(
                            title = header.title,
                            opponentFingerprint = header.opponentId.ifEmpty { null },
                            opponentName = header.opponentName,
                            avatarUrl = header.opponentId
                                .ifEmpty { null }
                                ?.let { id -> "/people/$id/-/avatar" },
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.words_detail_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    MochiIconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.words_list_menu),
                        )
                    }
                },
                actions = {
                    if (game != null) {
                        if (!twoPane) {
                            MochiIconButton(onClick = { showMobileChat = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                    contentDescription = stringResource(
                                        R.string.words_detail_action_open_chat,
                                    ),
                                )
                            }
                        }
                        WordsActionsMenu(
                            isActive = isActive,
                            isMyTurn = isMyTurn,
                            exchangeMode = state.exchangeMode,
                            hasPendingTiles = state.pendingPlacements.isNotEmpty(),
                            rematching = state.isCreatingRematch,
                            onShuffle = { viewModel.shuffleRack() },
                            onPass = { viewModel.passTurn() },
                            onToggleExchange = {
                                if (state.exchangeMode) {
                                    viewModel.cancelExchange()
                                } else {
                                    viewModel.enterExchangeMode()
                                }
                            },
                            onResign = { viewModel.openResignDialog() },
                            onRematch = { viewModel.rematch() },
                            onDelete = { showDeleteDialog = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The tablet layout's chat bar takes the navigation-bar inset
                // itself; consume the Scaffold's so it is not counted twice.
                .consumeWindowInsets(padding)
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
                    ErrorState(
                        error = state.error!!,
                        onRetry = viewModel::load,
                    )
                }
                game != null -> GameDetailContent(
                    state = state,
                    game = game,
                    viewModel = viewModel,
                    header = header ?: buildHeaderModel(game, state.myIdentity),
                    showMobileChat = showMobileChat,
                    onDismissMobileChat = { showMobileChat = false },
                    showDeleteDialog = showDeleteDialog,
                    onDismissDeleteDialog = { showDeleteDialog = false },
                    onOpenNotifications = onOpenNotifications,
                )
            }
        }
    }

    if (state.blankPromptOpen) {
        // Letter picker for a blank tile: 26 buttons in a 7-wide grid, narrower
        // than the web's 9-wide so a phone screen still fits a row. The buttons
        // share the dialog's width rather than taking a fixed 40 dp, which ran
        // the last column off the edge. The chosen letter is what the board
        // shows; the rack tile stays '_' so the engine still scores the blank
        // as zero.
        MochiAlertDialog(
            onDismissRequest = { viewModel.cancelBlankPrompt() },
            title = stringResource(R.string.words_detail_blank_title),
            // The grid is the subject here, so the heading sits under it in weight.
            titleStyle = MaterialTheme.typography.titleMedium,
            content = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val rows = listOf(
                        listOf('A', 'B', 'C', 'D', 'E', 'F', 'G'),
                        listOf('H', 'I', 'J', 'K', 'L', 'M', 'N'),
                        listOf('O', 'P', 'Q', 'R', 'S', 'T', 'U'),
                        listOf('V', 'W', 'X', 'Y', 'Z'),
                    )
                    for ((rowIdx, row) in rows.withIndex()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { base ->
                                    if (rowIdx == 0) base
                                    else base.then(Modifier.padding(top = 4.dp))
                                },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (letter in row) {
                                MochiOutlinedButton(
                                    onClick = { viewModel.selectBlankLetter(letter) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = letter.toString(),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                            }
                            repeat(BLANK_GRID_COLUMNS - row.size) { _ ->
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

    if (state.showResignDialog) {
        MochiAlertDialog(
            onDismissRequest = { viewModel.dismissResignDialog() },
            title = stringResource(R.string.words_detail_resign_title),
            text = stringResource(R.string.words_detail_resign_message),
            confirmText = stringResource(R.string.words_detail_action_resign),
            onConfirm = { viewModel.confirmResign() },
            confirmLoading = state.isResigning,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailContent(
    state: WordsGameDetailUiState,
    game: Game,
    viewModel: WordsGameViewModel,
    header: WordsHeaderModel,
    showMobileChat: Boolean,
    onDismissMobileChat: () -> Unit,
    showDeleteDialog: Boolean,
    onDismissDeleteDialog: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenNotifications: () -> Unit,
) {
    val myIdentity = state.myIdentity
    val isActive = game.status == "active"
    val isMyTurn = isActive && game.current_turn == game.my_player_number

    // Derive board, draft, scores from the engine.
    val board = remember(game.board) { parseBoard(game.board) }
    val moveDraft: MoveDraft = remember(board, state.pendingPlacements) {
        deriveMoveDraft(board, state.pendingPlacements)
    }
    val draftWords: List<Pair<String, Int>> = remember(moveDraft) {
        if (moveDraft.status == DraftStatus.READY) {
            moveDraft.result!!.wordsFormed.map { it.word to it.score }
        } else emptyList()
    }
    val draftScore: Int = remember(moveDraft) {
        if (moveDraft.status == DraftStatus.READY) moveDraft.result!!.totalScore else 0
    }
    // Validation has to have finished. Web whitelists ready,
    // ready_with_invalid_words and validation_unavailable but never 'checking';
    // Android's enum has no checking state, so the flag carries it. Without
    // this, submit stayed live through the 350ms debounce and the round-trip.
    val canSubmit = isMyTurn &&
        !state.exchangeMode &&
        moveDraft.status == DraftStatus.READY &&
        !state.isValidationChecking &&
        !state.isSubmittingMove
    val canRecallMove = isMyTurn && state.pendingPlacements.isNotEmpty() && !state.isSubmittingMove


    // Continuous drag-and-drop: board and rack report their bounds in root
    // coordinates, and release dispatches on whichever target rect holds the
    // pointer.
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

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxHeight(),
            ) {
                GameStatusBar(
                    status = header.status,
                    myTurn = if (isActive) isMyTurn else null,
                ) {
                    for (player in header.players) {
                        GameHeaderStat(
                            label = player.label,
                            value = player.score.toString(),
                            isHighlighted = player.isCurrentTurn,
                            isMe = player.isMe,
                        )
                    }
                    GameHeaderStat(label = header.tilesLeftLabel)
                }

                Box(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WordsBoard(
                        modifier = Modifier.widthIn(max = BOARD_MAX_WIDTH),
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
                            MoveFeedback(
                                pendingPlacements = state.pendingPlacements.size,
                                exchangeMode = state.exchangeMode,
                                moveDraft = moveDraft,
                                draftWords = draftWords,
                                wordValidationState = state.wordValidationState,
                                validationUnavailable = state.validationUnavailable,
                            )

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

                            MoveActions(
                                pendingPlacements = state.pendingPlacements.size,
                                exchangeMode = state.exchangeMode,
                                exchangeSelected = state.exchangeSelected.size,
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
                        hasMore = state.hasMoreMessages,
                        isLoadingMore = state.isLoadingMoreMessages,
                        onLoadMore = { viewModel.loadMoreMessages() },
                        onSend = { body, done -> viewModel.sendChatMessage(body, onFinished = done) },
                        composerWindowInsets = ComposeBarDefaults.WindowInsets,
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
            MochiBottomSheet(
                onDismissRequest = onDismissMobileChat,
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
                        hasMore = state.hasMoreMessages,
                        isLoadingMore = state.isLoadingMoreMessages,
                        onLoadMore = { viewModel.loadMoreMessages() },
                        onSend = { body, done -> viewModel.sendChatMessage(body, onFinished = done) },
                    )
                }
            }
        }

        if (showDeleteDialog) {
            MochiAlertDialog(
                onDismissRequest = onDismissDeleteDialog,
                title = stringResource(R.string.words_detail_delete_title),
                text = stringResource(R.string.words_detail_delete_message),
                confirmText = stringResource(R.string.words_detail_delete_confirm),
                onConfirm = {
                    onDismissDeleteDialog()
                    viewModel.deleteGame()
                },
                destructive = true,
                dismissText = stringResource(MochiR.string.common_cancel),
            )
        }
    }
}

private val GHOST_TILE_BG = Color(0xFFFBBF24)
private val GHOST_TILE_BORDER = Color(0xFFD97706)

/**
 * Overflow menu for the detail top bar: the moves left open to the viewer,
 * which depends on whose turn it is and whether the game is still running.
 */
@Composable
private fun WordsActionsMenu(
    isActive: Boolean,
    isMyTurn: Boolean,
    exchangeMode: Boolean,
    hasPendingTiles: Boolean,
    rematching: Boolean,
    onShuffle: () -> Unit,
    onPass: () -> Unit,
    onToggleExchange: () -> Unit,
    onResign: () -> Unit,
    onRematch: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MochiIconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = stringResource(R.string.words_detail_action_more),
            )
        }
        MochiDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (isActive) {
                if (isMyTurn && !exchangeMode) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.words_detail_action_shuffle)) },
                        onClick = {
                            expanded = false
                            onShuffle()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Shuffle, contentDescription = null) },
                    )
                }
                if (isMyTurn && !hasPendingTiles) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.words_detail_action_pass)) },
                        onClick = {
                            expanded = false
                            onPass()
                        },
                        leadingIcon = { Icon(Icons.Outlined.SkipNext, contentDescription = null) },
                    )
                }
                if (isMyTurn) {
                    MochiDropdownMenuItem(
                        text = {
                            val label = if (exchangeMode) {
                                stringResource(R.string.words_detail_action_cancel_exchange)
                            } else {
                                stringResource(R.string.words_detail_action_exchange)
                            }
                            Text(label)
                        },
                        onClick = {
                            expanded = false
                            onToggleExchange()
                        },
                        leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                    )
                }
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.words_detail_action_resign)) },
                    onClick = {
                        expanded = false
                        onResign()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                )
            } else {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.words_detail_action_rematch)) },
                    onClick = {
                        expanded = false
                        onRematch()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    enabled = !rematching,
                )
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.words_detail_action_delete)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun GameChatColumn(
    messages: List<GameMessage>,
    myIdentity: String,
    isLoading: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onSend: (String, (Boolean) -> Unit) -> Unit,
    // Which host is showing this column decides who lifts the composer for the
    // keyboard. The default suits the phone's sheet, which lifts its own
    // content; the tablet's side panel sits in the screen body and has to ask.
    composerWindowInsets: WindowInsets = ComposeBarDefaults.NoWindowInsets,
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
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                onLoadMore = onLoadMore,
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
        ComposeBar(
            value = chatDraft,
            onValueChange = { chatDraft = it },
            onSend = {
                if (chatDraft.isNotBlank() && !isSending) {
                    isSending = true
                    onSend(chatDraft) { sent ->
                        isSending = false
                        if (sent) chatDraft = ""
                    }
                }
            },
            isSending = isSending,
            placeholder = stringResource(MochiR.string.game_chat_input_placeholder),
            sendLabel = stringResource(MochiR.string.game_chat_send),
            // One line, and the keyboard's action key sends: a game chat
            // message is a sentence, not a comment body.
            maxLines = 1,
            sendOnImeAction = true,
            windowInsets = composerWindowInsets,
        )
    }
}

@Composable
private fun WordsMoveRow(msg: GameChatMessage, isSent: Boolean) {
    // Passes and exchanges are also type "move", and the server stores the body
    // as a finished sentence, so only the event marker distinguishes them.
    // Markerless rows are legacy and keep the played rendering.
    val actor = if (isSent) stringResource(R.string.words_detail_label_you) else msg.name
    val marker = msg.event
    val text = when {
        marker == "pass" -> stringResource(R.string.words_detail_chat_passed, actor)
        marker == "pass:over" -> stringResource(R.string.words_detail_chat_passed_over, actor)
        // The marker carries the tile count, but rendering it needs a
        // count-inflected noun in every locale's plural categories, so the
        // sentence omits it — the same call the web made, for the same reason.
        marker.startsWith("exchange:") -> stringResource(R.string.words_detail_chat_exchanged, actor)
        isSent -> stringResource(R.string.words_detail_chat_you_played, msg.body)
        else -> stringResource(R.string.words_detail_chat_player_played, msg.name, msg.body)
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
 * `myIdentity`; rendered by [WordsGameHeader]. [opponentId] and
 * [opponentName] carry the first other player, for the top bar's avatar.
 */
data class WordsHeaderModel(
    val title: String,
    val status: String,
    val players: List<WordsHeaderPlayer>,
    val tilesLeftLabel: String,
    val opponentId: String,
    val opponentName: String,
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

    val opponentSlot = (1..game.player_count).firstOrNull { num ->
        !isMeForPlayer(num) && playerIdentity(num).isNotEmpty()
    }

    return WordsHeaderModel(
        title = title,
        status = status,
        players = players,
        tilesLeftLabel = context.getString(R.string.words_detail_label_tiles_left, game.bag_count),
        opponentId = opponentSlot?.let { num -> playerIdentity(num) }.orEmpty(),
        opponentName = opponentSlot?.let { num -> playerName(num) }.orEmpty(),
    )
}

/** Board width cap, matching chess and go so every board reads the same size. */
private val BOARD_MAX_WIDTH = 560.dp

/** Letters per row in the blank-tile picker. */
private const val BLANK_GRID_COLUMNS = 7
