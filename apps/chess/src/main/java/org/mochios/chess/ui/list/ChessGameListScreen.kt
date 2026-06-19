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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.chess.R
import org.mochios.chess.navigation.ChessApp
import org.mochios.chess.ui.components.ChessSidebar
import org.mochios.chess.ui.components.ChessSidebarGame
import org.mochios.chess.ui.dialog.NewChessGameDialog
import org.mochios.android.R as MochiR

/**
 * Chess app landing screen. Mirrors the web's `ChessLayout` + the empty-state
 * branch of `ChessGame` (`apps/chess/web/src/features/chess/index.tsx`):
 *
 *  - Top bar with hamburger that opens [ChessSidebar] and a notifications
 *    icon (host-wired to the lib inbox).
 *  - Pull-to-refresh card list grouped by Active / Completed. Each card
 *    carries the opponent's avatar, name, and the lower-cased status
 *    badge on completed rows. Tapping a card navigates to
 *    [ChessApp.gameDetail].
 *  - Empty state when the user has no games — `GameEmptyState` parity:
 *    icon + "No games yet" + a primary "New game" button that opens the
 *    new-game dialog.
 *  - Error state with retry when the initial fetch fails.
 *
 * The new-game dialog (when [ChessGameListUiState.newGameDialogOpen] is
 * true) is rendered alongside the screen and routes its success path
 * through the ViewModel's [ChessGameListEvent.OpenGame] event so this
 * screen owns the navigation away to the detail.
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChessSidebar(
                activeGames = uiState.activeSidebar,
                completedGames = uiState.completedSidebar,
                onOpenGame = { gameId ->
                    drawerScope.launch { drawerState.close() }
                    navController.navigate(ChessApp.gameDetail(gameId))
                },
                onOpenNewGame = {
                    drawerScope.launch { drawerState.close() }
                    viewModel.openNewGameDialog()
                },
                // websocketStatusLabel is null here — only the detail screen
                // (a parallel agent's work) has an open WS to report on.
                websocketStatusLabel = null,
                websocketStatusColor = null,
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.chess_app_title)) },
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.chess_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenNotifications) {
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
                        onNewGame = { viewModel.openNewGameDialog() },
                    )
                    else -> GameCardGrid(
                        activeGames = uiState.activeSidebar,
                        completedGames = uiState.completedSidebar,
                        onOpenGame = { gameId ->
                            navController.navigate(ChessApp.gameDetail(gameId))
                        },
                        onNewGame = { viewModel.openNewGameDialog() },
                    )
                }
            }
        }
    }

    if (uiState.newGameDialogOpen) {
        NewChessGameDialog(
            onDismiss = { viewModel.closeNewGameDialog() },
            onCreated = { gameId -> viewModel.onGameCreated(gameId) },
            onAddFriends = { onOpenLink("people?action=add") },
            onToast = { message -> viewModel.onToast(message) },
        )
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
        Button(
            onClick = onNewGame,
            colors = ButtonDefaults.buttonColors(),
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
                    statusSuffix = null,
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
                    statusSuffix = game.statusLabel,
                    onClick = { onOpenGame(game.id) },
                )
            }
        }
        item("new-game-footer") {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
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
    statusSuffix: String?,
    onClick: () -> Unit,
) {
    val avatarUrl = if (game.opponentId.isNotBlank()) {
        "/people/${game.opponentId}/-/avatar"
    } else null

    Card(
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
                if (statusSuffix != null) {
                    Text(
                        text = statusSuffix,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
