// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.NotificationBell
import org.mochios.go.R
import org.mochios.go.navigation.GoApp
import org.mochios.go.ui.detail.GoGameDetailScreen
import org.mochios.go.ui.components.goDrawerItems
import org.mochios.go.ui.router.GO_FEATURE
import org.mochios.android.R as MochiR

/**
 * Go shell: the drawer holds every game, the pane beside it holds the
 * selected one. A [gameId] of [LastViewedStore.ALL] — or none at all — leaves
 * the empty state in that pane. The open game is remembered, so the next
 * launch reopens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoGameListScreen(
    navController: NavController,
    gameId: String,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    viewModel: GoGameListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val context = LocalContext.current
    val selectedGameId = gameId.takeUnless { id -> id == LastViewedStore.ALL }.orEmpty()
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(selectedGameId) {
        if (selectedGameId.isNotEmpty()) {
            LastViewedStore.set(context, GO_FEATURE, selectedGameId)
        }
    }

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

    // Side-effect events from the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GoGameListEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is GoGameListEvent.OpenGame -> {
                    navController.navigate(GoApp.gameDetail(event.gameId))
                }
            }
        }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.go_app_title)) },
        items = goDrawerItems(
            games = uiState.games,
            myIdentity = uiState.identity.orEmpty(),
        ),
        selectedId = selectedGameId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            if (item.id != selectedGameId) {
                navController.navigate(GoApp.gameDetail(item.id)) {
                    popUpTo(GoApp.GAME) { inclusive = true }
                    launchSingleTop = true
                }
            }
        },
        emptyState = {
            Text(
                text = stringResource(R.string.go_empty_active),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.go_sidebar_new_game),
                icon = Icons.Outlined.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    navController.navigate(GoApp.NEW_GAME)
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
        if (selectedGameId.isNotEmpty()) {
            GoGameDetailScreen(
                navController = navController,
                onOpenNotifications = onOpenNotifications,
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.go_app_title)) },
                        navigationIcon = {
                            MochiIconButton(
                                onClick = { drawerScope.launch { drawerState.open() } },
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.go_open_sidebar),
                                )
                            }
                        },
                        actions = {
                            NotificationBell(onClick = onOpenNotifications)
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
                            onRetry = { viewModel.loadGames() },
                        )
                        else -> GamesEmptyPane(
                            hasGames = uiState.games.isNotEmpty(),
                            onNewGame = { navController.navigate(GoApp.NEW_GAME) },
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


/**
 * The pane beside the drawer while no game is open: an invitation to pick one
 * once there are games to pick, and the first-run prompt before that. Both
 * offer the new-game action.
 */
@Composable
private fun GamesEmptyPane(hasGames: Boolean, onNewGame: () -> Unit) {
    EmptyState(
        icon = Icons.Default.GridOn,
        title = if (hasGames) {
            stringResource(MochiR.string.game_select_title)
        } else {
            stringResource(MochiR.string.game_empty_title)
        },
        subtitle = if (hasGames) stringResource(MochiR.string.game_select_subtitle) else null,
        modifier = Modifier.padding(32.dp),
        action = {
            MochiButton(onClick = onNewGame) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.go_sidebar_new_game))
            }
        },
    )
}
