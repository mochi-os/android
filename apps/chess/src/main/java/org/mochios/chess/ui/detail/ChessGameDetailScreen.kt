// Copyright © 2026 Mochi OÜ
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.GameChatInput
import org.mochios.android.ui.components.GameChatMessage
import org.mochios.android.ui.components.GameChatPanel
import org.mochios.android.ui.components.GameHeader
import org.mochios.android.ui.components.GameHeaderStat
import org.mochios.android.ui.components.GameHeaderStoneDot
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
import org.mochios.android.R as MochiR

/**
 * Per-game detail surface for `chess/{gameId}`. Mirrors the web's
 * `ChessGame` (apps/chess/web/src/features/chess/index.tsx) layout body:
 *
 *  - Two-pane on >= 600 dp: board on the left, chat panel on the right.
 *  - One-pane on phones: board fills the screen; chat lives behind a
 *    ModalBottomSheet reached via a Message icon in the [GameHeader]
 *    actions slot.
 *  - GameHeader strip — opponent name, turn-state status text, side dot
 *    stat, dropdown menu with offer-draw / resign (active) or rematch /
 *    delete (finished).
 *  - Draw-offer banner — muted "waiting for ..." when we offered, an
 *    Accept / Decline pair when the opponent offered to us.
 *  - CapturedPiecesStrip above (opponent's takes) and below (our takes)
 *    the board.
 *  - ChessBoard composable as the main play area.
 *
 * WebSocket events trigger a refresh — chess moves are infrequent enough
 * that a single `/-/view` + `/-/messages` round-trip per event keeps the
 * UI in sync without trying to surgically apply each frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessGameDetailScreen(
    navController: NavController,
    viewModel: ChessGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
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
                    navController.popBackStack()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chess_app_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                state.isLoading && state.game == null -> LoadingState()
                state.error != null && state.game == null -> ErrorState(
                    message = state.error?.userMessage()
                        ?: stringResource(MochiR.string.error_unexpected),
                    onRetry = { viewModel.load() },
                )
                state.game == null -> EmptyState()
                else -> {
                    val game = state.game!!
                    val myIdentity = state.identity
                    val myColor = if (game.white == myIdentity) 'w' else 'b'
                    GameContent(
                        state = state,
                        game = game,
                        myIdentity = myIdentity,
                        myColor = myColor,
                        onMove = viewModel::submitMove,
                        onOpenResign = { showResignDialog = true },
                        onOpenDelete = { showDeleteDialog = true },
                        onOfferDraw = viewModel::offerDraw,
                        onAcceptDraw = viewModel::acceptDraw,
                        onDeclineDraw = viewModel::declineDraw,
                        onRematch = viewModel::rematch,
                        onSendChat = viewModel::sendChat,
                        onLoadMoreChat = viewModel::loadMoreOlder,
                        onOpenMobileChat = { showMobileChat = true },
                        wsStatus = controller?.status?.collectAsState(initial = GameWsStatus.CONNECTING)?.value,
                    )
                }
            }
        }
    }

    // ---- Dialogs ----

    if (showResignDialog) {
        val opponentName = state.game?.opponentName(state.identity).orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.chess_resign_title),
            message = stringResource(R.string.chess_resign_message, opponentName),
            confirmLabel = stringResource(R.string.chess_resign_confirm),
            isDestructive = true,
            onConfirm = {
                showResignDialog = false
                viewModel.resign()
            },
            onDismiss = { showResignDialog = false },
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.chess_delete_title),
            message = stringResource(R.string.chess_delete_message),
            confirmLabel = stringResource(R.string.chess_delete_confirm),
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteGame()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showMobileChat) {
        ModalBottomSheet(
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
    onMove: (String, String, String?) -> Unit,
    onOpenResign: () -> Unit,
    onOpenDelete: () -> Unit,
    onOfferDraw: () -> Unit,
    onAcceptDraw: () -> Unit,
    onDeclineDraw: () -> Unit,
    onRematch: () -> Unit,
    onSendChat: (String) -> Unit,
    onLoadMoreChat: () -> Unit,
    onOpenMobileChat: () -> Unit,
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
                        onMove = onMove,
                        onOpenResign = onOpenResign,
                        onOpenDelete = onOpenDelete,
                        onOfferDraw = onOfferDraw,
                        onAcceptDraw = onAcceptDraw,
                        onDeclineDraw = onDeclineDraw,
                        onRematch = onRematch,
                        onOpenMobileChat = null, // chat is visible; no toggle needed
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
                    )
                }
            }
        } else {
            // Phone: board only; chat lives behind the chat icon in the header.
            BoardPane(
                state = state,
                game = game,
                myIdentity = myIdentity,
                myColor = myColor,
                onMove = onMove,
                onOpenResign = onOpenResign,
                onOpenDelete = onOpenDelete,
                onOfferDraw = onOfferDraw,
                onAcceptDraw = onAcceptDraw,
                onDeclineDraw = onDeclineDraw,
                onRematch = onRematch,
                onOpenMobileChat = onOpenMobileChat,
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
    onMove: (String, String, String?) -> Unit,
    onOpenResign: () -> Unit,
    onOpenDelete: () -> Unit,
    onOfferDraw: () -> Unit,
    onAcceptDraw: () -> Unit,
    onDeclineDraw: () -> Unit,
    onRematch: () -> Unit,
    onOpenMobileChat: (() -> Unit)?,
) {
    // Derive turn / check from the FEN every render — chesslib is cheap
    // and the FEN is the single source of truth.
    val turnState = remember(game.fen, myIdentity, game.white) {
        try {
            val board = Board()
            board.loadFromFen(game.fen)
            val mySide = if (myColor == 'w') Side.WHITE else Side.BLACK
            val turn = board.sideToMove == mySide
            val check = board.isKingAttacked
            turn to check
        } catch (_: Exception) {
            false to false
        }
    }
    val isMyTurn = turnState.first
    val isCheck = turnState.second

    val opponentName = game.opponentName(myIdentity)
    val statusText = chessStatusText(game, myIdentity, isMyTurn, isCheck)

    val capturedPair = remember(game.fen) { capturedPiecesFromFen(game.fen) }
    val (capturedByWhite, capturedByBlack) = capturedPair
    val capturedByMe = if (myColor == 'w') capturedByWhite else capturedByBlack
    val capturedByOpponent = if (myColor == 'w') capturedByBlack else capturedByWhite

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        GameHeader(
            title = opponentName,
            status = statusText,
            myTurn = if (game.status == "active") isMyTurn else null,
            opponentFingerprint = game.opponentId(myIdentity).takeIf { it.isNotBlank() },
            opponentName = opponentName,
            stats = {
                GameHeaderStat(
                    label = if (myColor == 'w') stringResource(R.string.chess_side_white)
                    else stringResource(R.string.chess_side_black),
                    icon = {
                        GameHeaderStoneDot(
                            color = if (myColor == 'w') StoneColor.WHITE else StoneColor.BLACK,
                        )
                    },
                )
            },
            actions = {
                if (onOpenMobileChat != null) {
                    IconButton(onClick = onOpenMobileChat) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = stringResource(R.string.chess_open_chat),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                GameActionsMenu(
                    game = game,
                    myIdentity = myIdentity,
                    onOfferDraw = onOfferDraw,
                    onResign = onOpenResign,
                    onRematch = onRematch,
                    onDelete = onOpenDelete,
                )
            },
            banner = drawBanner(
                game = game,
                myIdentity = myIdentity,
                opponentName = opponentName,
                onAccept = onAcceptDraw,
                onDecline = onDeclineDraw,
                acceptInFlight = state.isDrawAccepting,
                declineInFlight = state.isDrawDeclining,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

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

        // My captures (= pieces opponent has taken) below — symmetric with the
        // top strip so the visual hierarchy stays consistent with the board's
        // orientation.
        CapturedPiecesStrip(
            capturedByColor = if (myColor == 'w') 'b' else 'w',
            pieces = capturedByOpponent,
        )
    }
}

// ---------- Header pieces ----------

@Composable
private fun GameActionsMenu(
    game: Game,
    myIdentity: String,
    onOfferDraw: () -> Unit,
    onResign: () -> Unit,
    onRematch: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = stringResource(R.string.chess_open_actions),
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (game.status == "active") {
                if (game.drawOffer != myIdentity) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Handshake,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = { Text(stringResource(R.string.chess_offer_draw)) },
                        onClick = {
                            expanded = false
                            onOfferDraw()
                        },
                    )
                }
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = { Text(stringResource(R.string.chess_resign)) },
                    onClick = {
                        expanded = false
                        onResign()
                    },
                )
            } else {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = { Text(stringResource(R.string.chess_rematch)) },
                    onClick = {
                        expanded = false
                        onRematch()
                    },
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = { Text(stringResource(R.string.chess_delete_game)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
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
                    OutlinedButton(
                        onClick = onDecline,
                        enabled = !declineInFlight,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 4.dp,
                        ),
                    ) {
                        Text(stringResource(R.string.chess_draw_decline))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onAccept,
                        enabled = !acceptInFlight,
                        colors = ButtonDefaults.buttonColors(),
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
        HorizontalDivider()
        GameChatInput(
            text = draft,
            onTextChange = { draft = it },
            onSend = {
                val toSend = draft.trim()
                if (toSend.isNotEmpty()) {
                    onSend(toSend)
                    draft = ""
                }
            },
            isSending = state.isSendingChat,
        )
    }
}

/**
 * Renders a chess move chat message centred and muted. Web variant: see
 * `apps/chess/web/src/features/chess/components/chat-message-list.tsx`'s
 * move-row branch — "You played e4" / "Alice played e4", or "You took
 * Nxe5" / "Alice took Nxe5" when the SAN has a capture marker.
 */
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
 * Localised game-status string. Mirrors `useChessStatusText` in
 * `apps/chess/web/src/features/chess/index.tsx`. We pick from the
 * `chess_status_*` string resources; the screen call-site supplies the
 * caller context so [stringResource] resolves to the right locale.
 */
@Composable
private fun chessStatusText(
    game: Game,
    myIdentity: String,
    isMyTurn: Boolean,
    isCheck: Boolean,
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
        "resigned" -> if (game.winner == myIdentity) {
            stringResource(R.string.chess_status_resigned_opponent, opponentName)
        } else {
            stringResource(R.string.chess_status_resigned_you, opponentName)
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
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(MochiR.string.common_retry))
            }
        }
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
