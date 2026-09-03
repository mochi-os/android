// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.ErrorState
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
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.StoneColor
import org.mochios.android.ws.rememberGameWebSocket
import org.mochios.go.R
import org.mochios.go.engine.Score
import org.mochios.go.engine.Stone
import org.mochios.go.model.Game
import org.mochios.go.model.GameMessage
import org.mochios.go.navigation.GoApp
import org.mochios.go.ui.detail.board.GoBoard
import org.mochios.go.ui.router.GO_FEATURE
import org.mochios.android.R as MochiR

/**
 * Game-detail screen: header, board, chat (side panel on tablets, bottom sheet
 * on phones) and the pass / resign / delete confirmations. Mirrors
 * `apps/go/web/src/features/go/index.tsx`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoGameDetailScreen(
    navController: NavController,
    onOpenNotifications: () -> Unit = {},
    onOpenDrawer: () -> Unit,
    viewModel: GoGameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showPassDialog by remember { mutableStateOf(false) }
    var showResignDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMobileChat by remember { mutableStateOf(false) }

    // Resolve the failure-message resources up here so they're stable on
    // every menu-item lambda; this avoids the compose anti-pattern of
    // reading stringResource() inside a callback.
    val errMove = stringResource(R.string.go_error_move_failed)
    val errPass = stringResource(R.string.go_error_pass_failed)
    val errResign = stringResource(R.string.go_error_resign_failed)
    val errDrawOffer = stringResource(R.string.go_error_draw_offer_failed)
    val errDrawAccept = stringResource(R.string.go_error_draw_accept_failed)
    val errDrawDecline = stringResource(R.string.go_error_draw_decline_failed)
    val errRematch = stringResource(R.string.go_error_rematch_failed)
    val errDelete = stringResource(R.string.go_error_delete_failed)
    val errSend = stringResource(R.string.go_error_send_failed)
    val msgDeleted = stringResource(R.string.go_game_deleted)

    // Toasts, navigation events. Collected once for the lifetime of the screen.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GoGameDetailEvent.Toast ->
                    snackbarHostState.showSnackbar(event.message)
                is GoGameDetailEvent.OpenGame ->
                    navController.navigate(GoApp.gameDetail(event.gameId)) {
                        // Rematch replaces the finished game in the back stack.
                        popUpTo(GoApp.HOME)
                    }
                is GoGameDetailEvent.NavigateBack -> {
                    LastViewedStore.clear(context, GO_FEATURE)
                    navController.navigate(GoApp.gameDetail(LastViewedStore.ALL)) {
                        popUpTo(GoApp.GAME) { inclusive = true }
                    }
                }
            }
        }
    }

    // Open a fresh WebSocket once the game has loaded (we need the key).
    val game = state.game
    val controller = rememberGameWebSocket(game?.key)
    DisposableEffect(controller) {
        val job = controller?.let {
            scope.launch {
                it.events.collect { ev ->
                    // Synthesise a GameMessage row for chat/move/system events
                    // so the chat panel updates without waiting for the
                    // round-trip refresh that applyWsEvent kicks off.
                    val msg = synthesiseMessage(ev.type, ev.body, ev.member, ev.name, ev.event, ev.created)
                    viewModel.applyWsEvent(ev.type, msg)
                }
            }
        }
        onDispose { job?.cancel() }
    }

    val composer = remember { mutableStateOf("") }

    val opponentName = game?.opponentName(state.myIdentity).orEmpty()
    val opponentFingerprint = when {
        game == null -> ""
        game.identity == state.myIdentity -> game.opponent
        else -> game.identity
    }
    val myColor: Stone = if (game?.black == state.myIdentity) Stone.BLACK else Stone.WHITE
    val isActive = game?.status == "active"
    val canPass = isActive && state.isMyTurn
    val title = if (game != null && game.boardSize != 19) {
        stringResource(R.string.go_detail_title_with_size, opponentName, game.boardSize)
    } else {
        opponentName
    }
    val statusLabel = if (game != null) {
        goStatusText(game, state.myIdentity, state.isMyTurn, state.score)
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
                            title = title,
                            opponentFingerprint = opponentFingerprint.takeIf { it.isNotBlank() },
                            opponentName = opponentName,
                            avatarUrl = opponentFingerprint
                                .takeIf { it.isNotBlank() }
                                ?.let { id -> "/people/$id/-/avatar" },
                        )
                    } else {
                        Text(stringResource(R.string.go_app_title))
                    }
                },
                navigationIcon = {
                    MochiIconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.go_open_sidebar),
                        )
                    }
                },
                actions = {
                    NotificationBell(onClick = onOpenNotifications)
                    if (game != null) {
                        if (!twoPane) {
                            MochiIconButton(onClick = { showMobileChat = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                    contentDescription = stringResource(
                                        R.string.go_action_open_chat,
                                    ),
                                )
                            }
                        }
                        GoActionsMenu(
                            isActive = isActive,
                            canPass = canPass,
                            canOfferDraw = game.drawOffer != state.myIdentity,
                            passing = state.isPassing,
                            drawOffering = state.isDrawOffering,
                            rematching = state.isCreatingRematch,
                            onPass = { showPassDialog = true },
                            onOfferDraw = { viewModel.offerDraw(errDrawOffer) },
                            onResign = { showResignDialog = true },
                            onRematch = { viewModel.rematch(errRematch) },
                            onDelete = { showDeleteDialog = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && game == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            state.error != null && game == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    ErrorState(
                        error = state.error ?: MochiError.Unknown(),
                        onRetry = { viewModel.loadGame() },
                    )
                }
            }
            game != null -> {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        // The wide layout's chat bar takes the navigation-bar
                        // inset itself; consume what the Scaffold handed down
                        // so it is not counted twice.
                        .consumeWindowInsets(padding)
                        .padding(padding),
                ) {
                    val isWide = maxWidth >= 600.dp
                    Row(modifier = Modifier.fillMaxSize()) {
                        // ---- LEFT: header + board ----
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp),
                        ) {
                            GameStatusBar(
                                status = statusLabel,
                                myTurn = if (isActive) state.isMyTurn else null,
                            ) {
                                val storeColor = if (myColor == Stone.BLACK) {
                                    StoneColor.BLACK
                                } else {
                                    StoneColor.WHITE
                                }
                                GameHeaderStat(
                                    icon = { GameHeaderStoneDot(storeColor) },
                                    label = if (myColor == Stone.BLACK) {
                                        stringResource(R.string.go_color_black)
                                    } else {
                                        stringResource(R.string.go_color_white)
                                    },
                                    isMe = true,
                                )
                                if (isActive) {
                                    GameHeaderStat(
                                        icon = { GameHeaderStoneDot(StoneColor.BLACK) },
                                        label = game.capturesBlack.toString(),
                                        srLabel = stringResource(R.string.go_captures_black_sr),
                                    )
                                    GameHeaderStat(
                                        icon = { GameHeaderStoneDot(StoneColor.WHITE) },
                                        label = game.capturesWhite.toString(),
                                        srLabel = stringResource(R.string.go_captures_white_sr),
                                    )
                                }
                            }

                            val banner = drawOfferBanner(
                                game = game,
                                myIdentity = state.myIdentity,
                                opponentName = opponentName,
                                isAccepting = state.isDrawAccepting,
                                isDeclining = state.isDrawDeclining,
                                onAccept = { viewModel.acceptDraw(errDrawAccept) },
                                onDecline = { viewModel.declineDraw(errDrawDecline) },
                            )
                            banner?.invoke()

                            // Board sits below the header. Cap the width so
                            // the board doesn't stretch past a comfortable
                            // tap-target size on tablets — 560 dp matches
                            // the web cap.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                GoBoard(
                                    fen = game.fen,
                                    previousFen = game.previousFen,
                                    boardSize = game.boardSize,
                                    myColor = myColor,
                                    isMyTurn = state.isMyTurn,
                                    gameStatus = game.status,
                                    onPlace = { row, col ->
                                        viewModel.place(row, col, errMove)
                                    },
                                    lastMove = state.lastMove,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 560.dp),
                                )
                            }
                        }

                        // ---- RIGHT: chat (tablets only) ----
                        if (isWide) {
                            VerticalDivider(thickness = 1.dp)
                            Column(
                                modifier = Modifier
                                    .width(320.dp)
                                    .fillMaxHeight(),
                            ) {
                                ChatHeader()
                                ChatBody(
                                    state = state,
                                    onLoadMore = { viewModel.loadMoreMessages() },
                                    onRetry = { viewModel.loadMessages() },
                                    modifier = Modifier.weight(1f),
                                )
                                ComposeBar(
                                    // The wide-layout side panel sits in the
                                    // screen body, not the sheet, so this one
                                    // consumes the keyboard inset itself.
                                    windowInsets = ComposeBarDefaults.WindowInsets,
                                    value = composer.value,
                                    onValueChange = { composer.value = it },
                                    onSend = {
                                        val body = composer.value
                                        if (body.isNotBlank()) {
                                            viewModel.sendChat(body, errSend)
                                            composer.value = ""
                                        }
                                    },
                                    isSending = state.isSendingMessage,
                                    placeholder = stringResource(MochiR.string.game_chat_input_placeholder),
                                    sendLabel = stringResource(MochiR.string.game_chat_send),
                                    // One line, and the keyboard's action key sends: a game chat
                                    // message is a sentence, not a comment body.
                                    maxLines = 1,
                                    sendOnImeAction = true,
                                )
                            }
                        }
                    }
                }

                // ---- DIALOGS ----

                if (showPassDialog) {
                    val isEndGame = state.goGame?.consecutivePasses == 1
                    MochiAlertDialog(
                        onDismissRequest = { showPassDialog = false },
                        title = stringResource(
                            if (isEndGame) R.string.go_pass_end_title else R.string.go_pass_title,
                        ),
                        text = if (isEndGame) {
                            stringResource(R.string.go_pass_end_message, opponentName)
                        } else {
                            stringResource(R.string.go_pass_message)
                        },
                        confirmText = stringResource(
                            if (isEndGame) R.string.go_pass_end_confirm else R.string.go_pass_confirm,
                        ),
                        onConfirm = {
                            showPassDialog = false
                            viewModel.passTurn(errPass)
                        },
                        destructive = isEndGame,
                        dismissText = stringResource(MochiR.string.common_cancel),
                    )
                }

                if (showResignDialog) {
                    MochiAlertDialog(
                        onDismissRequest = { showResignDialog = false },
                        title = stringResource(R.string.go_resign_title),
                        text = stringResource(R.string.go_resign_message, opponentName),
                        confirmText = stringResource(R.string.go_resign_confirm),
                        onConfirm = {
                            showResignDialog = false
                            viewModel.resign(errResign)
                        },
                        destructive = true,
                        dismissText = stringResource(MochiR.string.common_cancel),
                    )
                }

                if (showDeleteDialog) {
                    MochiAlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = stringResource(R.string.go_delete_title),
                        text = stringResource(R.string.go_delete_message),
                        confirmText = stringResource(R.string.go_delete_confirm),
                        onConfirm = {
                            showDeleteDialog = false
                            viewModel.deleteGame(errDelete, msgDeleted)
                        },
                        destructive = true,
                        dismissText = stringResource(MochiR.string.common_cancel),
                    )
                }

                // ---- MOBILE CHAT SHEET ----
                // Only the phone-width header shows the chat button, so
                // [showMobileChat] flips only on layouts where the side
                // panel isn't visible.
                if (showMobileChat) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    MochiBottomSheet(
                        onDismissRequest = { showMobileChat = false },
                        sheetState = sheetState,
                    ) {
                        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
                            ChatHeader()
                            ChatBody(
                                state = state,
                                onLoadMore = { viewModel.loadMoreMessages() },
                                onRetry = { viewModel.loadMessages() },
                                modifier = Modifier.weight(1f),
                            )
                            ComposeBar(
                                showDivider = false,
                                value = composer.value,
                                onValueChange = { composer.value = it },
                                onSend = {
                                    val body = composer.value
                                    if (body.isNotBlank()) {
                                        viewModel.sendChat(body, errSend)
                                        composer.value = ""
                                    }
                                },
                                isSending = state.isSendingMessage,
                                placeholder = stringResource(MochiR.string.game_chat_input_placeholder),
                                sendLabel = stringResource(MochiR.string.game_chat_send),
                                // One line, and the keyboard's action key sends: a game chat
                                // message is a sentence, not a comment body.
                                maxLines = 1,
                                sendOnImeAction = true,
                                windowInsets = ComposeBarDefaults.NoWindowInsets,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// goStatusText — pure function the screen calls each composition
// ------------------------------------------------------------------

/**
 * Status line for [GameHeader]; mirrors web's `useGoStatusText`.
 */
/**
 * Overflow menu for the detail top bar: the moves left open to the viewer,
 * which depends on whose turn it is and whether the game is still running.
 */
@Composable
private fun GoActionsMenu(
    isActive: Boolean,
    canPass: Boolean,
    canOfferDraw: Boolean,
    passing: Boolean,
    drawOffering: Boolean,
    rematching: Boolean,
    onPass: () -> Unit,
    onOfferDraw: () -> Unit,
    onResign: () -> Unit,
    onRematch: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MochiIconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = stringResource(R.string.go_action_more),
            )
        }
        MochiDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (isActive) {
                if (canPass) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.go_action_pass)) },
                        onClick = {
                            expanded = false
                            onPass()
                        },
                        leadingIcon = { Icon(Icons.Outlined.SkipNext, contentDescription = null) },
                        enabled = !passing,
                    )
                }
                if (canOfferDraw) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.go_action_offer_draw)) },
                        onClick = {
                            expanded = false
                            onOfferDraw()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Handshake, contentDescription = null) },
                        enabled = !drawOffering,
                    )
                }
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.go_action_resign)) },
                    onClick = {
                        expanded = false
                        onResign()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                )
            } else {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.go_action_rematch)) },
                    onClick = {
                        expanded = false
                        onRematch()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    enabled = !rematching,
                )
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.go_action_delete)) },
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
private fun goStatusText(
    game: Game,
    myIdentity: String,
    isMyTurn: Boolean,
    score: Score?,
): String {
    val opponentName = game.opponentName(myIdentity)
    return when (game.status) {
        "finished" -> when {
            score?.winner != null -> stringResource(
                R.string.go_status_score,
                if (score.winner == Stone.BLACK) {
                    stringResource(R.string.go_color_black)
                } else {
                    stringResource(R.string.go_color_white)
                },
                formatScore(score.black),
                formatScore(score.white),
            )
            // A tie now records status "draw", but a peer on an older build —
            // or the web until its engine is fixed — still writes "finished"
            // with a winner nobody earned. Read the score rather than repeat it.
            score != null -> stringResource(R.string.go_status_draw_text)
            game.winner == myIdentity -> stringResource(R.string.go_status_you_win)
            !game.winner.isNullOrBlank() -> stringResource(
                R.string.go_status_opponent_wins,
                opponentName,
            )
            else -> stringResource(R.string.go_status_game_over)
        }
        "draw" -> stringResource(R.string.go_status_draw_text)
        "resigned" -> if (game.winner == myIdentity) {
            stringResource(R.string.go_status_resigned_opponent, opponentName)
        } else {
            stringResource(R.string.go_status_resigned_self, opponentName)
        }
        else -> if (isMyTurn) {
            stringResource(R.string.go_status_your_move)
        } else {
            stringResource(R.string.go_status_opponent_move, opponentName)
        }
    }
}

private fun formatScore(value: Double): String {
    val whole = value.toLong()
    return if (whole.toDouble() == value) whole.toString() else value.toString()
}

// ------------------------------------------------------------------
// Chat panel helpers
// ------------------------------------------------------------------

@Composable
private fun ChatHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.go_chat_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    HorizontalDivider(thickness = 1.dp)
}

@Composable
private fun ChatBody(
    state: GoGameDetailUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mappedMessages = remember(state.messages) {
        state.messages.map { it.toChatMessage() }
    }
    GameChatPanel(
        messages = mappedMessages,
        currentUserIdentity = state.myIdentity,
        isLoading = state.isLoadingMessages,
        isError = state.messagesError != null,
        hasMore = state.hasMoreMessages,
        isLoadingMore = state.isLoadingMoreMessages,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        moveMessageRenderer = { msg, isSent -> { GoMoveMessageRow(msg, isSent) } },
        // System messages already arrive localised (or close to it) from
        // the Starlark layer, so the default renderer in [GameChatPanel]
        // is fine. We still register an explicit slot to keep the
        // moveMessageRenderer hook symmetric.
        systemMessageRenderer = { msg -> { DefaultSystemMessage(msg) } },
        modifier = modifier,
    )
}

@Composable
private fun GoMoveMessageRow(message: GameChatMessage, isSent: Boolean) {
    // Reuse the lib's shared "<subject> played <move>" pattern so all
    // games (chess / go / words) speak in the same voice. The subject is
    // "You" for the local player, the opponent's display name otherwise.
    val subject = if (isSent) {
        stringResource(MochiR.string.game_chat_subject_you)
    } else {
        message.name
    }
    val text = stringResource(MochiR.string.game_chat_move_played, subject, message.body)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun DefaultSystemMessage(message: GameChatMessage) {
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------------
// Draw-offer banner
// ------------------------------------------------------------------

/**
 * Banner below the status line: a waiting note when I offered, Accept / Decline
 * when the opponent did; null when there is no offer.
 */
@Composable
private fun drawOfferBanner(
    game: Game,
    myIdentity: String,
    opponentName: String,
    isAccepting: Boolean,
    isDeclining: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
): (@Composable () -> Unit)? {
    val drawOffer = game.drawOffer ?: return null
    if (drawOffer.isBlank()) return null
    return {
        if (drawOffer == myIdentity) {
            Text(
                text = stringResource(R.string.go_draw_offered_waiting, opponentName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.go_draw_offered_received, opponentName),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                MochiOutlinedButton(
                    onClick = onDecline,
                    enabled = !isAccepting && !isDeclining,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    if (isDeclining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Text(stringResource(R.string.go_draw_decline))
                    }
                }
                MochiButton(
                    onClick = onAccept,
                    enabled = !isAccepting && !isDeclining,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    if (isAccepting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Text(stringResource(R.string.go_draw_accept))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// GameMessage ↔ GameChatMessage glue
// ------------------------------------------------------------------

private fun GameMessage.toChatMessage(): GameChatMessage = GameChatMessage(
    id = id,
    game = game,
    member = member,
    name = name,
    body = body,
    type = type,
    event = event,
    created = created,
)

private fun synthesiseMessage(
    type: String,
    body: String?,
    member: String?,
    name: String?,
    event: String?,
    created: Long,
): GameMessage? {
    if (type != "message" && type != "move" && type != "system") return null
    val resolvedBody = body ?: return null
    return GameMessage(
        // The server's `mochi.uid()` IDs aren't echoed in the websocket
        // frame, so we synthesise a stable but unique id from the timestamp
        // + type + member. Collisions are vanishingly unlikely (two events
        // would need to share epoch second + type + sender).
        id = "ws-$type-$created-${member.orEmpty()}",
        game = "",
        member = member.orEmpty(),
        name = name.orEmpty(),
        body = resolvedBody,
        type = type,
        event = event.orEmpty(),
        created = created,
    )
}
