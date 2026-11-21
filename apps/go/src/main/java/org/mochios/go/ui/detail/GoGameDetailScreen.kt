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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.push.VisibleEntityEffect
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.GameChatColumn
import org.mochios.android.ui.components.GameChatMessage
import org.mochios.android.ui.components.GameChatState
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
import org.mochios.android.ui.components.MissingGame
import org.mochios.android.ui.components.MochiSheetHeader
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.StoneColor
import org.mochios.android.ui.components.frameRowId
import org.mochios.android.ui.components.rememberReopened
import org.mochios.android.websocket.StreamWsEvent
import org.mochios.android.websocket.rememberStreamWebSocket
import org.mochios.go.R
import org.mochios.android.i18n.LocalFormat
import org.mochios.go.engine.Score
import org.mochios.go.engine.Stone
import org.mochios.go.model.Game
import org.mochios.go.model.GameMessage
import org.mochios.go.model.opponentAvatarUrl
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
    onOpenNotifications: () -> Unit,
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
    val errScoreAccept = stringResource(R.string.go_error_score_accept_failed)
    val errScoreResume = stringResource(R.string.go_error_score_resume_failed)
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
                        // The router pops HOME on the way in, so popping to it
                        // here matched nothing and stacked the new game on top.
                        popUpTo(GoApp.GAME) { inclusive = true }
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

    // Frames that land while the app is backgrounded are gone by the time it
    // comes back — the socket reconnects but replays nothing — so refetch on
    // resume rather than leaving the board on the position it had.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Open a fresh WebSocket once the game has loaded (we need the key).
    val game = state.game
    val controller = rememberStreamWebSocket(game?.key, app = "go")

    // The board's stream is what makes dropping the tray row honest, and it
    // subscribes with the game record's key, not the id in the link.
    VisibleEntityEffect(
        "go",
        viewModel.gameId,
        socketKey = game?.key.orEmpty(),
        marksRead = true,
    )
    DisposableEffect(controller) {
        val job = controller?.let {
            scope.launch {
                it.events.collect { ev ->
                    // Synthesise a GameMessage row for chat/move/system events
                    // so the chat panel updates without waiting for the
                    // round-trip refresh that applyWsEvent kicks off.
                    val msg = frameMessage(ev)
                    viewModel.applyWsEvent(ev.type, msg)
                }
            }
        }
        onDispose { job?.cancel() }
    }

    // One draft for the side panel and the phone's sheet, kept across both.
    val composer = rememberSaveable { mutableStateOf("") }
    val chat by viewModel.chat.state.collectAsState()
    val reopened = rememberReopened(GO_FEATURE, viewModel.gameId)

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
        stringResource(R.string.go_detail_title_with_size, opponentName, game.boardSize.toString())
    } else {
        opponentName
    }
    // The server does not reliably record a winner on a resignation, and the
    // status would then tell whoever did not resign that they had. The system
    // row names the player who did, so it decides when it is loaded.
    val resignedBy = chat.messages.lastOrNull { message ->
        message.type == "system" && message.event == "resign"
    }?.member
    val statusLabel = if (game != null) {
        goStatusText(game, state.myIdentity, state.isMyTurn, state.score, resignedBy)
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
                            // The game's own proxy: a cross-app request to
                            // the people app carries this app's token and is
                            // refused, so it never resolved a picture.
                            avatarUrl = game.opponentId(state.myIdentity)
                                .takeIf { it.isNotBlank() }
                                ?.let { id -> opponentAvatarUrl(game.id, id) },
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
                            game = game,
                            myIdentity = state.myIdentity,
                            canPass = canPass,
                            passing = state.isPassing,
                            drawOffering = state.isDrawOffering,
                            resuming = state.isScoreResuming,
                            rematching = state.isCreatingRematch,
                            onPass = { showPassDialog = true },
                            onOfferDraw = { viewModel.offerDraw(errDrawOffer) },
                            onResign = { showResignDialog = true },
                            onResume = { viewModel.resumeScoring(errScoreResume) },
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
            state.error is MochiError.NotFoundError && game == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    MissingGame(
                        feature = GO_FEATURE,
                        id = viewModel.gameId,
                        reopened = reopened,
                        onEmpty = {
                            navController.navigate(GoApp.gameDetail(LastViewedStore.ALL)) {
                                popUpTo(GoApp.GAME) { inclusive = true }
                            }
                        },
                    )
                }
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
                                .fillMaxHeight(),
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

                            val scoring = scoringBanner(
                                game = game,
                                myIdentity = state.myIdentity,
                                opponentName = opponentName,
                                isAccepting = state.isScoreAccepting,
                                isResuming = state.isScoreResuming,
                                onAccept = { viewModel.acceptScore(errScoreAccept) },
                                onResume = { viewModel.resumeScoring(errScoreResume) },
                            )
                            if (scoring != null) {
                                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    scoring()
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
                            if (banner != null) {
                                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    banner()
                                }
                            }

                            // Board sits below the header. Cap the width so
                            // the board doesn't stretch past a comfortable
                            // tap-target size on tablets — 560 dp matches
                            // the web cap.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(start = 8.dp, end = 8.dp, top = 8.dp),
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
                                ChatColumn(
                                    chat = chat,
                                    myIdentity = state.myIdentity,
                                    draft = composer,
                                    onLoadMore = { viewModel.loadMoreMessages() },
                                    onRetry = { viewModel.loadMessages() },
                                    onSend = { body, done -> viewModel.sendChat(body, errSend, done) },
                                    modifier = Modifier.weight(1f),
                                    // The wide-layout side panel sits in the
                                    // screen body, not the sheet, so this one
                                    // consumes the keyboard inset itself.
                                    composerWindowInsets = ComposeBarDefaults.WindowInsets,
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
                            MochiSheetHeader(
                                title = stringResource(R.string.go_chat_title),
                            )
                            ChatColumn(
                                chat = chat,
                                myIdentity = state.myIdentity,
                                draft = composer,
                                onLoadMore = { viewModel.loadMoreMessages() },
                                onRetry = { viewModel.loadMessages() },
                                onSend = { body, done -> viewModel.sendChat(body, errSend, done) },
                                modifier = Modifier.weight(1f),
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
 * While the score is being agreed the game is neither playable nor over. The
 * player still deciding answers in the scoring banner and has no menu; the
 * one who has accepted gets Resume play, so an opponent who never answers
 * cannot strand the game. Rematch and Delete wait until the game has ended.
 */
@Composable
internal fun GoActionsMenu(
    game: Game,
    myIdentity: String,
    canPass: Boolean,
    passing: Boolean,
    drawOffering: Boolean,
    resuming: Boolean,
    rematching: Boolean,
    onPass: () -> Unit,
    onOfferDraw: () -> Unit,
    onResign: () -> Unit,
    onResume: () -> Unit,
    onRematch: () -> Unit,
    onDelete: () -> Unit,
) {
    if (game.status == "scoring" && game.scoring != myIdentity) return
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
            if (game.status == "scoring") {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.go_score_resume)) },
                    onClick = {
                        expanded = false
                        onResume()
                    },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    enabled = !resuming,
                )
            } else if (game.status == "active") {
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
                if (game.canOfferDraw) {
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
    resignedBy: String?,
): String {
    val opponentName = game.opponentName(myIdentity)
    val format = LocalFormat.current
    return when (game.status) {
        "finished" -> when {
            score?.winner != null -> stringResource(
                R.string.go_status_score,
                if (score.winner == Stone.BLACK) {
                    stringResource(R.string.go_color_black)
                } else {
                    stringResource(R.string.go_color_white)
                },
                format.formatNumber(score.black),
                format.formatNumber(score.white),
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
        "scoring" -> stringResource(R.string.go_status_scoring)
        "resigned" -> when {
            resignedBy == myIdentity ->
                stringResource(R.string.go_status_resigned_self, opponentName)
            !resignedBy.isNullOrBlank() ->
                stringResource(R.string.go_status_resigned_opponent, opponentName)
            game.winner == myIdentity ->
                stringResource(R.string.go_status_resigned_opponent, opponentName)
            else -> stringResource(R.string.go_status_resigned_self, opponentName)
        }
        else -> if (isMyTurn) {
            stringResource(R.string.go_status_your_move)
        } else {
            stringResource(R.string.go_status_opponent_move, opponentName)
        }
    }
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

/** The shared game chat column with go's move rows; system rows are the library's. */
@Composable
private fun ChatColumn(
    chat: GameChatState<GameMessage>,
    myIdentity: String,
    draft: MutableState<String>,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSend: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    composerWindowInsets: WindowInsets = ComposeBarDefaults.NoWindowInsets,
) {
    GameChatColumn(
        chat = chat,
        message = GameMessage::toChatMessage,
        currentUserIdentity = myIdentity,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        onSend = onSend,
        modifier = modifier,
        draft = draft,
        composerWindowInsets = composerWindowInsets,
        moveMessageRenderer = { msg, isSent -> { GoMoveMessageRow(msg, isSent) } },
    )
}

@Composable
internal fun GoMoveMessageRow(message: GameChatMessage, isSent: Boolean) {
    // A pass is its own sentence, and "You" its own string, since in many
    // languages the verb agrees with its subject. The server marks a pass
    // with its event; rows stored before the marker carry only the body
    // "Pass".
    val pass = message.event == "pass" || message.body == "Pass"
    // The lib's shared move sentences, so all games (chess / go / words)
    // speak in the same voice.
    val text = when {
        pass && isSent -> stringResource(R.string.go_chat_you_passed)
        pass -> stringResource(R.string.go_chat_player_passed, message.name)
        isSent -> stringResource(MochiR.string.game_chat_move_played_you, message.body)
        else -> stringResource(MochiR.string.game_chat_move_played, message.name, message.body)
    }
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

// ------------------------------------------------------------------
// Draw-offer banner
// ------------------------------------------------------------------

/**
 * Banner while the game is being scored: a waiting note once I have accepted,
 * otherwise the proposed score with Accept and Resume. Null unless the game is
 * in `scoring`.
 */
@Composable
private fun scoringBanner(
    game: Game,
    myIdentity: String,
    opponentName: String,
    isAccepting: Boolean,
    isResuming: Boolean,
    onAccept: () -> Unit,
    onResume: () -> Unit,
): (@Composable () -> Unit)? {
    if (game.status != "scoring") return null
    val format = LocalFormat.current
    val black = format.formatNumber(game.scoreBlack ?: 0.0)
    val white = format.formatNumber(game.scoreWhite ?: 0.0)
    return {
        if (game.scoring == myIdentity) {
            Text(
                text = stringResource(R.string.go_score_waiting, opponentName),
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
                    // The opponent having accepted already changes what this
                    // player's own Accept does: it ends the game.
                    text = if (game.scoring.isNullOrBlank()) {
                        stringResource(R.string.go_score_proposed, black, white)
                    } else {
                        stringResource(R.string.go_score_opponent_accepted, black, white)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                MochiOutlinedButton(
                    onClick = onResume,
                    enabled = !isAccepting && !isResuming,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    if (isResuming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Text(stringResource(R.string.go_score_resume))
                    }
                }
                MochiButton(
                    onClick = onAccept,
                    enabled = !isAccepting && !isResuming,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    if (isAccepting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Text(stringResource(R.string.go_score_accept))
                    }
                }
            }
        }
    }
}

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
    // The server leaves draw_offer set on a game that ended some other way,
    // so a resignation would otherwise keep offering a draw nobody can take.
    if (game.status != "active") return null
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

/**
 * The chat row a chat, move or system frame adds. Its id is the stored row's,
 * which the frame carries; an older server's frame gets one unique on this
 * device ([frameRowId]), never one made of the time and sender, which two
 * messages in one second share.
 */
internal fun frameMessage(event: StreamWsEvent): GameMessage? {
    if (event.type != "message" && event.type != "move" && event.type != "system") return null
    val body = event.body ?: return null
    return GameMessage(
        id = frameRowId(event.raw["id"]),
        game = "",
        member = event.member.orEmpty(),
        name = event.name.orEmpty(),
        body = body,
        type = event.type,
        event = event.event.orEmpty(),
        created = event.created,
    )
}
