// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
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
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.chess.R
import org.mochios.chess.navigation.ChessApp
import org.mochios.chess.ui.detail.ChessGameDetailScreen
import org.mochios.chess.ui.components.chessDrawerItems
import org.mochios.android.R as MochiR

/**
 * Chess shell: the drawer holds every game, the pane beside it holds the
 * selected one. An empty [gameId] — nothing picked, or no games to pick —
 * leaves the empty state in that pane. Reloads on foreground so a game
 * started on [ChessApp.NEW_GAME] appears in the drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessGameListScreen(
    navController: NavController,
    gameId: String,
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
    val openSidebarLabel = stringResource(R.string.chess_open_sidebar)
    val notificationsLabel = stringResource(MochiR.string.notifications_open)

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
        selectedId = gameId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            if (item.id != gameId) {
                navController.navigate(ChessApp.gameDetail(item.id)) {
                    // Swap the open game rather than stack another one, so
                    // Back always lands on the empty pane, not on whichever
                    // games were opened before it.
                    popUpTo(ChessApp.HOME)
                    launchSingleTop = true
                }
            }
        },
        emptyState = {
            Text(
                text = stringResource(R.string.chess_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
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
        if (gameId.isNotEmpty()) {
            ChessGameDetailScreen(
                navController = navController,
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.chess_app_title)) },
                        navigationIcon = {
                            MochiIconButton(
                                onClick = { drawerScope.launch { drawerState.open() } },
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = openSidebarLabel,
                                )
                            }
                        },
                        actions = {
                            MochiIconButton(onClick = onOpenNotifications) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = notificationsLabel,
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
                        else -> EmptyState(
                            onNewGame = { navController.navigate(ChessApp.NEW_GAME) },
                        )
                    }
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
