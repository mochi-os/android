// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.words.R
import org.mochios.words.model.GameListItem
import org.mochios.words.model.getPlayerNames
import org.mochios.words.model.playerScore
import org.mochios.words.ui.components.WordsSidebar
import org.mochios.words.ui.dialog.NewWordsGameDialog
import org.mochios.android.R as MochiR

/**
 * Landing screen for the Words app. Lists every game the user is in as
 * a card carrying the other players' names, the running score line, and
 * a small "your turn" badge if `current_turn` matches the caller's
 * player number.
 *
 * The hamburger opens [WordsSidebar] (same composable used by the
 * detail screen) so the user has a single navigation surface across
 * the app. The FAB and the sidebar's New Game button both open the
 * [NewWordsGameDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsGameListScreen(
    onGameClick: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    viewModel: WordsGameListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var showOverflow by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.createdGameId) {
        val id = uiState.createdGameId ?: return@LaunchedEffect
        viewModel.consumeCreatedGame()
        showNewGameDialog = false
        onGameClick(id)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            WordsSidebar(
                games = uiState.games,
                myIdentity = uiState.myIdentity,
                selectedGameId = null,
                onSelectGame = { game ->
                    drawerScope.launch { drawerState.close() }
                    onGameClick(game.fingerprint?.ifBlank { null } ?: game.id)
                },
                onNewGame = {
                    drawerScope.launch { drawerState.close() }
                    showNewGameDialog = true
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.words_list_title)) },
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.words_list_menu))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(MochiR.string.common_more_options))
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.words_list_logout)) },
                                    onClick = {
                                        showOverflow = false
                                        onLogout()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                    },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showNewGameDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.words_list_new))
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    uiState.isLoading && uiState.games.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.error != null && uiState.games.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = uiState.error!!.userMessage(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    uiState.games.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.words_list_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = stringResource(R.string.words_list_empty_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.games, key = { it.id }) { game ->
                                GameListCard(
                                    game = game,
                                    myIdentity = uiState.myIdentity,
                                    onClick = {
                                        onGameClick(game.fingerprint?.ifBlank { null } ?: game.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewGameDialog) {
        NewWordsGameDialog(
            isOpen = true,
            onDismiss = { showNewGameDialog = false },
            friends = uiState.newGameFriends,
            isLoadingFriends = uiState.isLoadingFriends,
            friendsError = uiState.friendsError,
            isCreating = uiState.isCreatingGame,
            onLoadFriends = { viewModel.loadNewGameFriends() },
            onCreate = { opponents, language -> viewModel.createGame(opponents, language) },
            onAddFriends = {
                showNewGameDialog = false
                onOpenLink("people")
            },
        )
    }
}

/**
 * One card per game in the landing list. Shows opponents' names as the
 * headline + score-line below. A "Your turn" badge tags games where it's
 * the caller's player slot to move.
 */
@Composable
private fun GameListCard(
    game: GameListItem,
    myIdentity: String,
    onClick: () -> Unit,
) {
    val names = getPlayerNames(game, myIdentity).ifBlank { "${game.player_count} players" }
    val scores = buildString {
        for (i in 1..game.player_count) {
            if (i > 1) append(" · ")
            append(playerScore(game, i))
        }
    }
    val myTurn = game.status == "active" &&
        game.my_player_number != 0 &&
        game.current_turn == game.my_player_number
    val statusLabel: String? = when (game.status) {
        "finished" -> stringResource(R.string.words_list_status_finished)
        "resigned" -> stringResource(R.string.words_list_status_resigned)
        else -> null
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = names,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scores,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (myTurn) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = stringResource(R.string.words_list_your_turn),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
