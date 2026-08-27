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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.words.R
import org.mochios.words.model.GameListItem
import org.mochios.words.model.getPlayerNames
import org.mochios.words.model.playerScore
import org.mochios.words.ui.components.wordsDrawerItems
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsGameListScreen(
    onGameClick: (String) -> Unit,
    onNewGame: () -> Unit,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    viewModel: WordsGameListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    var showOverflow by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.words_sidebar_header)) },
        items = wordsDrawerItems(
            games = uiState.games,
            myIdentity = uiState.myIdentity,
        ),
        selectedId = null,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            onGameClick(item.id)
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.words_sidebar_new_game),
                icon = Icons.Default.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onNewGame()
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
        emptyState = {
            Text(
                text = stringResource(R.string.words_sidebar_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.words_list_title)) },
                    navigationIcon = {
                        MochiIconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.words_list_menu))
                        }
                    },
                    actions = {
                        Box {
                            MochiIconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(MochiR.string.common_more_options))
                            }
                            MochiDropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(R.string.words_list_logout)) },
                                    onClick = {
                                        showOverflow = false
                                        onLogout()
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onNewGame) {
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
                        ErrorState(
                            error = uiState.error!!,
                            onRetry = viewModel::load,
                        )
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
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

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

    MochiCard(
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
