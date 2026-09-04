// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.GameChatMessage
import org.mochios.android.ui.components.GameChatPanel
import org.mochios.android.ui.components.GameStatusBar
import org.mochios.android.ui.components.GameHeaderStat
import org.mochios.android.ui.components.GameTopBarTitle
import org.mochios.android.ui.components.GameHeaderStoneDot
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.StoneColor
import org.mochios.android.ws.GameWsEvent
import org.mochios.android.ws.GameWsStatus
import org.mochios.android.ws.rememberGameWebSocket
import org.mochios.chess.R
import org.mochios.chess.model.Game
import org.mochios.chess.model.GameMessage
import org.mochios.chess.navigation.ChessApp
import org.mochios.chess.ui.detail.board.CapturedPiecesStrip
import org.mochios.chess.ui.detail.board.ChessBoard
import org.mochios.chess.ui.detail.board.capturedPiecesFromFen
import org.mochios.chess.ui.router.CHESS_FEATURE
import org.mochios.android.R as MochiR

/**
 * Game detail for `chess/{gameId}`: two-pane (board and chat) from 600 dp, chat
 * in a bottom sheet below that. WebSocket events trigger a full refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessGameDetailScreen(
    navController: NavController,
    onOpenDrawer: () -> Unit,
    viewModel: ChessGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showResignDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showMobileChat by rememberSaveable { mutableStateOf(false) }
    val mobileChatSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ---- WebSocket ----

    val wsKey = state.game?.key?.takeIf { it.isNotBlank() }
    val controller = rememberGameWebSocket(wsKey)
    LaunchedEffect(controller) {
        if (controller != null) {
            controller.events.collect { _: GameWsEvent ->
                // Trade per-event surgery for a single refresh: chess
                // events arrive at human pace (~1/min in an active game)
                // so the round-trip overhead is negligible and we always
                // converge on the server's view.
                viewModel.onWebsocketEvent()
            }
        }
    }

    // ---- Side-effect events from the ViewModel ----

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChessGameEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is ChessGameEvent.OpenGame -> {
                    // Pop the current game and push the new one — same
                    // shape as the web's `navigate({to: '/$gameId'})`.
                    navController.navigate(ChessApp.gameDetail(event.gameId)) {
                        popUpTo(ChessApp.HOME)
                        launchSingleTop = true
                    }
                }
                is ChessGameEvent.NavigateUp -> {
                    LastViewedStore.clear(context, CHESS_FEATURE)
                    navController.navigate(ChessApp.gameDetail(LastViewedStore.ALL)) {
                        popUpTo(ChessApp.GAME) { inclusive = true }
                    }
                }
            }
        }
    }

    val game = state.game
    val myIdentity = state.identity
    val myColor = if (game?.white == myIdentity) 'w' else 'b'
    // Derive turn / check from the FEN every render — chesslib is cheap
    // and the FEN is the single source of truth.
    val turnState = remember(game?.fen, myIdentity, game?.white) {
        try {
            val board = Board()
            board.loadFromFen(game?.fen.orEmpty())
            val mySide = if (myColor == 'w') Side.WHITE else Side.BLACK
            (board.sideToMove == mySide) to board.isKingAttacked
        } catch (_: Exception) {
            false to false
        }
    }
    val isMyTurn = turnState.first
    val opponentName = game?.opponentName(myIdentity).orEmpty()
    val opponentId = game?.opponentId(myIdentity).orEmpty()
    // The server does not reliably record a winner on a resignation, and the
    // status would then tell whoever did not resign that they had. The system
    // row names the player who did, so it decides when it is loaded.
    val resignedBy = state.messages.lastOrNull { message ->
        message.type == "system" && message.event == "resign"
    }?.member
    val statusText = if (game != null) {
        chessStatusText(game, myIdentity, isMyTurn, turnState.second, resignedBy)
    } else {
        ""
    }
    // The board pane keeps its own BoxWithConstraints for layout; the top bar
    // needs the same answer a composition earlier, so it asks the window.
    val twoPane = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (game != null) {
                        GameTopBarTitle(
                            title = opponentName,
                            opponentFingerprint = opponentId.takeIf { it.isNotBlank() },
                            opponentName = opponentName,
                            avatarUrl = opponentId
                                .takeIf { it.isNotBlank() }
                                ?.let { id -> "/people/$id/-/avatar" },
                        )
                    } else {
                        Text(stringResource(R.string.chess_app_title))
                    }
                },
                navigationIcon = {
                    MochiIconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.chess_open_sidebar),
                        )
                    }
                },
                actions = {
                    if (game != null) {
                        if (!twoPane) {
                            MochiIconButton(onClick = { showMobileChat = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                    contentDescription = stringResource(R.string.chess_open_chat),
                                )
                            }
                        }
                        GameActionsMenu(
                            game = game,
                            myIdentity = myIdentity,
                            drawOffering = state.isDrawOffering,
                            resigning = state.isResigning,
                            rematching = state.isRematching,
                            deleting = state.isDeleting,
                            onOfferDraw = viewModel::offerDraw,
                            onResign = { showResignDialog = true },
                            onRematch = viewModel::rematch,
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
                state.isLoading && game == null -> LoadingState()
                state.error != null && game == null -> ErrorState(
                    message = state.error?.userMessage()
                        ?: stringResource(MochiR.string.error_unexpected),
                    onRetry = { viewModel.load() },
                )
                game == null -> EmptyState()
                else -> {
                    GameContent(
                        state = state,
                        game = game,
                        myIdentity = myIdentity,
                        myColor = myColor,
                        isMyTurn = isMyTurn,
                        statusText = statusText,
                        onMove = viewModel::submitMove,
                        onAcceptDraw = viewModel::acceptDraw,
                        onDeclineDraw = viewModel::declineDraw,
                        onSendChat = viewModel::sendChat,
                        onLoadMoreChat = viewModel::loadMoreOlder,
                        wsStatus = controller?.status?.collectAsState(initial = GameWsStatus.CONNECTING)?.value,
                    )
                }
            }
        }
    }

    // ---- Dialogs ----

    if (showResignDialog) {
        val opponentName = state.game?.opponentName(state.identity).orEmpty()
        MochiAlertDialog(
            onDismissRequest = { showResignDialog = false },
            title = stringResource(R.string.chess_resign_title),
            text = stringResource(R.string.chess_resign_message, opponentName),
            confirmText = stringResource(R.string.chess_resign_confirm),
            onConfirm = {
                showResignDialog = false
                viewModel.resign()
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

    if (showDeleteDialog) {
        MochiAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.chess_delete_title),
            text = stringResource(R.string.chess_delete_message),
            confirmText = stringResource(R.string.chess_delete_confirm),
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteGame()
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

    if (showMobileChat) {
        MochiBottomSheet(
            sheetState = mobileChatSheetState,
            onDismissRequest = { showMobileChat = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().height(480.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chess_chat_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider()
                ChatPanel(
                    state = state,
                    myIdentity = state.identity,
                    onSend = { body ->
                        viewModel.sendChat(body)
                    },
                    onLoadMore = viewModel::loadMoreOlder,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------- Layout ----------

@Composable
private fun GameContent(
    state: ChessGameUiState,
    game: Game,
    myIdentity: String,
    myColor: Char,
    isMyTurn: Boolean,
    statusText: String,
    onMove: (String, String, String?) -> Unit,
    onAcceptDraw: () -> Unit,
    onDeclineDraw: () -> Unit,
    onSendChat: (String) -> Unit,
    onLoadMoreChat: () -> Unit,
    wsStatus: GameWsStatus?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 600.dp

        if (twoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    BoardPane(
                        state = state,
                        game = game,
                        myIdentity = myIdentity,
                        myColor = myColor,
                        isMyTurn = isMyTurn,
                        statusText = statusText,
                        onMove = onMove,
                        onAcceptDraw = onAcceptDraw,
                        onDeclineDraw = onDeclineDraw,
                    )
                }
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        ),
                ) {
                    ChatHeader(wsStatus = wsStatus)
                    ChatPanel(
                        state = state,
                        myIdentity = myIdentity,
                        onSend = onSendChat,
                        onLoadMore = onLoadMoreChat,
                        modifier = Modifier.weight(1f),
                        composerWindowInsets = ComposeBarDefaults.WindowInsets,
                        composerShowDivider = true,
                    )
                }
            }
        } else {
            // Phone: board only; chat lives behind the top bar's chat icon.
            BoardPane(
                state = state,
                game = game,
                myIdentity = myIdentity,
                myColor = myColor,
                isMyTurn = isMyTurn,
                statusText = statusText,
                onMove = onMove,
                onAcceptDraw = onAcceptDraw,
                onDeclineDraw = onDeclineDraw,
            )
        }
    }
}

@Composable
private fun BoardPane(
    state: ChessGameUiState,
    game: Game,
    myIdentity: String,
    myColor: Char,
    isMyTurn: Boolean,
    statusText: String,
    onMove: (String, String, String?) -> Unit,
    onAcceptDraw: () -> Unit,
    onDeclineDraw: () -> Unit,
) {
    val opponentName = game.opponentName(myIdentity)

    val capturedPair = remember(game.fen) { capturedPiecesFromFen(game.fen) }
    val (capturedByWhite, capturedByBlack) = capturedPair
    val capturedByMe = if (myColor == 'w') capturedByWhite else capturedByBlack
    val capturedByOpponent = if (myColor == 'w') capturedByBlack else capturedByWhite

    Column(modifier = Modifier.fillMaxSize()) {
        GameStatusBar(
            status = statusText,
            myTurn = if (game.status == "active") isMyTurn else null,
        ) {
            GameHeaderStat(
                label = if (myColor == 'w') stringResource(R.string.chess_side_white)
                else stringResource(R.string.chess_side_black),
                icon = {
                    GameHeaderStoneDot(
                        color = if (myColor == 'w') StoneColor.WHITE else StoneColor.BLACK,
                    )
                },
            )
        }

        val banner = drawBanner(
            game = game,
            myIdentity = myIdentity,
            opponentName = opponentName,
            onAccept = onAcceptDraw,
            onDecline = onDeclineDraw,
            acceptInFlight = state.isDrawAccepting,
            declineInFlight = state.isDrawDeclining,
        )
        if (banner != null) {
            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                banner()
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // The strips belong to the board, so the three sit as one group under
        // the status strip, capped and centred across the pane.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = BOARD_MAX_WIDTH)) {
                // Opponent captures (= pieces taken by the local player) above.
                CapturedPiecesStrip(
                    capturedByColor = myColor,
                    pieces = capturedByMe,
                )
                Spacer(modifier = Modifier.height(8.dp))

                ChessBoard(
                    fen = game.fen,
                    myColor = myColor,
                    isMyTurn = isMyTurn,
                    gameStatus = game.status,
                    onMove = onMove,
                    lastMove = state.lastMove,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // My captures (= pieces opponent has taken) below — symmetric
                // with the top strip so the visual hierarchy stays consistent
                // with the board's orientation.
                CapturedPiecesStrip(
                    capturedByColor = if (myColor == 'w') 'b' else 'w',
                    pieces = capturedByOpponent,
                )
            }
        }
    }
}

// ---------- Header pieces ----------

@Composable
private fun GameActionsMenu(
    game: Game,
    myIdentity: String,
    drawOffering: Boolean,
    resigning: Boolean,
    rematching: Boolean,
    deleting: Boolean,
    onOfferDraw: () -> Unit,
    onResign: () -> Unit,
    onRematch: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MochiIconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = stringResource(R.string.chess_open_actions),
                modifier = Modifier.size(20.dp),
            )
        }
        MochiDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (game.status == "active") {
                if (game.drawOffer != myIdentity) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.chess_offer_draw)) },
                        onClick = {
                            expanded = false
                            onOfferDraw()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Handshake, contentDescription = null) },
                        enabled = !drawOffering,
                    )
                }
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.chess_resign)) },
                    onClick = {
                        expanded = false
                        onResign()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                    enabled = !resigning,
                )
            } else {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.chess_rematch)) },
                    onClick = {
                        expanded = false
                        onRematch()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    enabled = !rematching,
                )
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.chess_delete_game)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    enabled = !deleting,
                )
            }
        }
    }
}

@Composable
private fun drawBanner(
    game: Game,
    myIdentity: String,
    opponentName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    acceptInFlight: Boolean,
    declineInFlight: Boolean,
): (@Composable () -> Unit)? {
    // The server leaves draw_offer set on a game that ended some other way,
    // so a resignation would otherwise keep offering a draw nobody can take —
    // Accept came back "Game is not active".
    if (game.status != "active") return null
    val drawOffer = game.drawOffer ?: return null
    return {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (drawOffer == myIdentity) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chess_draw_waiting, opponentName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chess_draw_offered, opponentName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MochiOutlinedButton(
                        onClick = onDecline,
                        enabled = !declineInFlight,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 4.dp,
                        ),
                    ) {
                        Text(stringResource(R.string.chess_draw_decline))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    MochiButton(
                        onClick = onAccept,
                        enabled = !acceptInFlight,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 4.dp,
                        ),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.chess_draw_accept))
                    }
                }
            }
        }
    }
}

// ---------- Chat ----------

@Composable
private fun ChatHeader(wsStatus: GameWsStatus?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.chess_chat_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        if (wsStatus != null && wsStatus != GameWsStatus.CONNECTED) {
            Text(
                text = when (wsStatus) {
                    GameWsStatus.CONNECTING -> stringResource(R.string.chess_ws_connecting)
                    GameWsStatus.CONNECTED -> ""
                    GameWsStatus.DISCONNECTED -> stringResource(R.string.chess_ws_disconnected)
                    GameWsStatus.RECONNECTING -> stringResource(R.string.chess_ws_reconnecting)
                    GameWsStatus.FAILED -> stringResource(R.string.chess_ws_failed)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun ChatPanel(
    state: ChessGameUiState,
    myIdentity: String,
    onSend: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    // Which host is showing this panel decides who lifts the composer for the
    // keyboard. The default suits the phone's sheet, which lifts its own
    // content; the tablet's side panel sits in the screen body and has to ask.
    composerWindowInsets: WindowInsets = ComposeBarDefaults.NoWindowInsets,
    composerShowDivider: Boolean = false,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        val mapped = remember(state.messages) {
            state.messages.map { m ->
                GameChatMessage(
                    id = m.id,
                    game = m.game,
                    member = m.member,
                    name = m.name,
                    body = m.body,
                    type = m.type,
                    event = m.event,
                    created = m.created,
                )
            }
        }
        GameChatPanel(
            messages = mapped,
            currentUserIdentity = myIdentity,
            isLoading = state.isLoading,
            hasMore = state.hasMore,
            isLoadingMore = state.isLoadingMore,
            onLoadMore = onLoadMore,
            modifier = Modifier.weight(1f),
            moveMessageRenderer = { msg, isSent -> { ChessMoveRow(msg, isSent) } },
            systemMessageRenderer = { msg -> { ChessSystemRow(msg) } },
        )
        ComposeBar(
            value = draft,
            showDivider = composerShowDivider,
            onValueChange = { draft = it },
            onSend = {
                val toSend = draft.trim()
                if (toSend.isNotEmpty()) {
                    onSend(toSend)
                    draft = ""
                }
            },
            isSending = state.isSendingChat,
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
private fun ChessMoveRow(message: GameChatMessage, isSent: Boolean) {
    val san = message.body
    val isCapture = san.contains('x')
    val subject = if (isSent) stringResource(R.string.chess_move_subject_you) else message.name
    val text = if (isCapture) {
        stringResource(R.string.chess_move_capture, subject, san)
    } else {
        stringResource(R.string.chess_move_played, subject, san)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
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
private fun ChessSystemRow(message: GameChatMessage) {
    // Localise per viewer from the structured event kind; legacy rows (no
    // event) fall back to the server-stored English body. Mirrors web's
    // chat-message-list system branch.
    val text = when (message.event) {
        "resign" -> stringResource(MochiR.string.game_system_resign, message.name)
        "draw_offer" -> stringResource(MochiR.string.game_system_draw_offer, message.name)
        "draw_accept" -> stringResource(MochiR.string.game_system_draw_accept)
        "draw_decline" -> stringResource(MochiR.string.game_system_draw_decline, message.name)
        else -> message.body
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------- Status text ----------

/**
 * Status line; mirrors the web's `useChessStatusText`.
 */
@Composable
private fun chessStatusText(
    game: Game,
    myIdentity: String,
    isMyTurn: Boolean,
    isCheck: Boolean,
    resignedBy: String?,
): String {
    val opponentName = game.opponentName(myIdentity)
    return when (game.status) {
        "checkmate" -> if (game.winner == myIdentity) {
            stringResource(R.string.chess_status_checkmate_you_win)
        } else {
            stringResource(R.string.chess_status_checkmate_opponent_wins, opponentName)
        }
        "stalemate" -> stringResource(R.string.chess_status_stalemate)
        "draw" -> stringResource(R.string.chess_status_draw)
        "resigned" -> when {
            resignedBy == myIdentity ->
                stringResource(R.string.chess_status_resigned_you, opponentName)
            !resignedBy.isNullOrBlank() ->
                stringResource(R.string.chess_status_resigned_opponent, opponentName)
            game.winner == myIdentity ->
                stringResource(R.string.chess_status_resigned_opponent, opponentName)
            else -> stringResource(R.string.chess_status_resigned_you, opponentName)
        }
        else -> {
            if (isCheck) {
                if (isMyTurn) stringResource(R.string.chess_status_check_your_move)
                else stringResource(R.string.chess_status_check_opponent_move, opponentName)
            } else {
                if (isMyTurn) stringResource(R.string.chess_status_your_move)
                else stringResource(R.string.chess_status_opponent_move, opponentName)
            }
        }
    }
}

// ---------- Loading / error / empty ----------

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}


@Composable
private fun EmptyState() {
    // Should never happen — the loading branch above keeps the screen busy
    // until the first `view` response arrives. Render something neutral so
    // a malformed gameId path at least doesn't crash.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.chess_detail_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Board width cap, matching go and words so every board reads the same size. */
private val BOARD_MAX_WIDTH = 560.dp
