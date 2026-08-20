// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.list

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.NotificationBell
import org.mochios.go.R
import org.mochios.go.model.Game
import org.mochios.go.navigation.GoApp
import org.mochios.go.ui.components.GoSidebarFilter
import org.mochios.go.ui.components.goDrawerFilter
import org.mochios.go.ui.components.goDrawerItems
import org.mochios.android.R as MochiR

/**
 * Go landing list — the surface the launcher icon (and Mochi menu shortcut)
 * opens. Mirrors `apps/go/web/src/routes/_authenticated/index.tsx`
 * `GamesListPage`:
 *
 *  - Top bar: hamburger opens [GoSidebar] (Active / Completed filter +
 *    New game), notification bell, no overflow menu.
 *  - Body splits the user's games into Active vs Completed; the sidebar
 *    filter picks which section is rendered. Each card shows the opponent
 *    name, board size and last-updated hint; cards are tappable to open
 *    the detail screen.
 *  - Empty state matches the web "No games yet — start one" prompt.
 *  - Pull-to-refresh re-fetches the list.
 *  - The New-game button on the sidebar (and an empty-state CTA) opens the
 *    new-game screen ([GoApp.NEW_GAME]), which starts the game and navigates
 *    into it. The list reloads on resume so the game is there when the user
 *    backs out of the board.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoGameListScreen(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    viewModel: GoGameListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var showOverflow by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(GoSidebarFilter.ACTIVE) }

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
        items = goDrawerItems(),
        selectedId = currentFilter.name,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            currentFilter = goDrawerFilter(item.id)
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.go_sidebar_new_game),
                icon = Icons.Default.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    navController.navigate(GoApp.NEW_GAME)
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.go_app_title)) },
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.go_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        NotificationBell(onClick = onOpenNotifications)
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(MochiR.string.common_more_options))
                            }
                            MochiDropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(MochiR.string.about_label)) },
                                    onClick = {
                                        showOverflow = false
                                        showAbout = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                )
                            }
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
                        onRetry = { viewModel.loadGames() },
                    )
                    else -> {
                        val (active, completed) = remember(uiState.games) {
                            val a = uiState.games.filter { it.status == "active" }
                            val c = uiState.games.filterNot { it.status == "active" }
                            a to c
                        }
                        val visibleGames = when (currentFilter) {
                            GoSidebarFilter.ACTIVE -> active
                            GoSidebarFilter.COMPLETED -> completed
                        }
                        if (visibleGames.isEmpty()) {
                            EmptyState(
                                filter = currentFilter,
                                onNewGame = { navController.navigate(GoApp.NEW_GAME) },
                            )
                        } else {
                            GameList(
                                games = visibleGames,
                                myIdentity = uiState.identity.orEmpty(),
                                onOpen = { game ->
                                    navController.navigate(
                                        GoApp.gameDetail(game.id),
                                    )
                                },
                            )
                        }
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
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(MochiR.string.common_retry))
            }
        }
    }
}

@Composable
private fun EmptyState(filter: GoSidebarFilter, onNewGame: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (filter) {
                    GoSidebarFilter.ACTIVE -> stringResource(R.string.go_empty_active)
                    GoSidebarFilter.COMPLETED -> stringResource(R.string.go_empty_completed)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filter == GoSidebarFilter.ACTIVE) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onNewGame) {
                    Text(stringResource(R.string.go_sidebar_new_game))
                }
            }
        }
    }
}

@Composable
private fun GameList(games: List<Game>, myIdentity: String, onOpen: (Game) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(games, key = { it.id }) { game ->
            GameCard(game = game, myIdentity = myIdentity, onOpen = { onOpen(game) })
        }
    }
}

@Composable
private fun GameCard(game: Game, myIdentity: String, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // opponent_name holds the INVITEE's name on both peers, so
                    // the raw field names the user back to themselves on any
                    // game they did not create. The helper picks the other side.
                    text = stringResource(R.string.go_card_vs, game.opponentName(myIdentity)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.go_card_meta,
                        game.boardSize,
                        game.boardSize,
                        statusLabel(game.status),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    "active" -> stringResource(R.string.go_status_active)
    "finished" -> stringResource(R.string.go_status_finished)
    "draw" -> stringResource(R.string.go_status_draw)
    "resigned" -> stringResource(R.string.go_status_resigned)
    else -> status
}
