// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.chess.R
import org.mochios.chess.navigation.ChessApp
import org.mochios.chess.ui.components.ChessSidebarGame
import org.mochios.chess.ui.components.chessDrawerItems
import org.mochios.android.R as MochiR

/**
 * Landing screen: drawer, pull-to-refresh Active / Completed cards, empty and
 * error states. Reloads on foreground so a game started on [ChessApp.NEW_GAME]
 * appears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessGameListScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") onLogout: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenLink: (String) -> Unit,
    viewModel: ChessGameListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }

    // Reload when the screen returns to the foreground, most importantly after
    // starting a game on the new-game screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChessGameListEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is ChessGameListEvent.OpenGame -> {
                    navController.navigate(ChessApp.gameDetail(event.gameId))
                }
            }
        }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.chess_sidebar_header)) },
        items = chessDrawerItems(
            activeGames = uiState.activeSidebar,
            completedGames = uiState.completedSidebar,
        ),
        selectedId = null,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            navController.navigate(ChessApp.gameDetail(item.id))
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.chess_sidebar_new_game),
                icon = Icons.Outlined.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    navController.navigate(ChessApp.NEW_GAME)
                },
            )
            DrawerActionRow(
                title = stringResource(MochiR.string.about_label),
                icon = Icons.Outlined.Info,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    showAbout = true
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.chess_app_title)) },
                    navigationIcon = {
                        MochiIconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.chess_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        MochiIconButton(onClick = onOpenNotifications) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = stringResource(MochiR.string.notifications_open),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    uiState.isLoading && uiState.games.isEmpty() -> LoadingState()
                    uiState.error != null && uiState.games.isEmpty() -> ErrorState(
                        message = uiState.error?.userMessage()
                            ?: stringResource(MochiR.string.error_unexpected),
                        onRetry = { viewModel.load() },
                    )
                    uiState.games.isEmpty() -> EmptyState(
                        onNewGame = { navController.navigate(ChessApp.NEW_GAME) },
                    )
                    else -> GameCardGrid(
                        activeGames = uiState.activeSidebar,
                        completedGames = uiState.completedSidebar,
                        onOpenGame = { gameId ->
                            navController.navigate(ChessApp.gameDetail(gameId))
                        },
                        onNewGame = { navController.navigate(ChessApp.NEW_GAME) },
                    )
                }
            }
        }
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}


@Composable
private fun EmptyState(onNewGame: () -> Unit) {
    // Mirrors the web `GameEmptyState` for the no-games-yet branch: large
    // icon, primary "Start your first game" button. Sized for a fresh
    // install — the user has no context yet, so this is the only CTA.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.SportsKabaddi,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.chess_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chess_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        MochiButton(
            onClick = onNewGame,
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.chess_empty_start_button))
        }
    }
}

@Composable
private fun GameCardGrid(
    activeGames: List<ChessSidebarGame>,
    completedGames: List<ChessSidebarGame>,
    onOpenGame: (String) -> Unit,
    onNewGame: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (activeGames.isNotEmpty()) {
            item("active-header") {
                SectionHeader(stringResource(R.string.chess_section_active))
            }
            items(activeGames, key = { "active-${it.id}" }) { game ->
                GameCard(
                    game = game,
                    onClick = { onOpenGame(game.id) },
                )
            }
        }
        if (completedGames.isNotEmpty()) {
            item("completed-header") {
                SectionHeader(stringResource(R.string.chess_section_completed))
            }
            items(completedGames, key = { "completed-${it.id}" }) { game ->
                GameCard(
                    game = game,
                    onClick = { onOpenGame(game.id) },
                )
            }
        }
        item("new-game-footer") {
            Spacer(modifier = Modifier.height(12.dp))
            MochiButton(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.chess_new_game))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun GameCard(
    game: ChessSidebarGame,
    onClick: () -> Unit,
) {
    val avatarUrl = if (game.opponentId.isNotBlank()) {
        "/people/${game.opponentId}/-/avatar"
    } else null

    MochiCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EntityAvatar(
                name = game.opponentName,
                src = avatarUrl,
                seed = game.opponentId,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.opponentName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
