// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.newgame

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.words.R
import org.mochios.words.model.NewGameFriend
import org.mochios.android.R as MochiR

/**
 * "New game" screen ported from the dialog, which in turn came from
 * `apps/words/web/src/features/words/components/new-game.tsx`.
 *
 * Composition (top to bottom):
 *   - "Choose opponents (1-3)" label.
 *   - Multi-select checkbox list over the user's friends (the lib's
 *     `PersonPicker` is single-select, so the list is inlined here).
 *   - "(N+1 players)" preview line when at least one friend is selected.
 *   - Language toggle: English (UK) default / English (US).
 *   - Start game in the bottom bar, back button in the top bar.
 *
 * The empty-friends state mirrors the web: an icon + helper text + an
 * "Add friends" button that links to the People app via [onAddFriends].
 *
 * @param onBack leaves the screen without starting a game.
 * @param onCreated receives the new game's id so the host can open it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWordsGameScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onAddFriends: () -> Unit,
    viewModel: NewWordsGameViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedFriends by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var language by rememberSaveable { mutableStateOf("en_UK") }

    LaunchedEffect(uiState.createdGameId) {
        uiState.createdGameId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedGame()
        }
    }

    val isCreating = uiState.isCreating
    val canSubmit = selectedFriends.size in 1..3 && !isCreating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.words_new_game_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isCreating) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    uiState.createError?.let { error ->
                        Text(
                            text = error.userMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { viewModel.createGame(selectedFriends.toList(), language) },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                        } else {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                        Text(stringResource(R.string.words_new_game_start))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.words_new_game_choose_opponents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.words_new_game_opponents_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.size(8.dp))

            when {
                uiState.isLoadingFriends -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.friendsError != null -> {
                    Text(
                        text = uiState.friendsError?.userMessage()
                            ?: stringResource(MochiR.string.error_unexpected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                uiState.friends.isEmpty() -> {
                    EmptyFriendsBlock(onAddFriends = onAddFriends)
                }

                else -> {
                    FriendsList(
                        friends = uiState.friends,
                        selectedIds = selectedFriends,
                        onToggle = { id ->
                            selectedFriends = selectedFriends.toMutableSet().apply {
                                if (contains(id)) {
                                    remove(id)
                                } else if (size < 3) {
                                    add(id)
                                }
                            }
                        },
                    )
                }
            }

            if (selectedFriends.isNotEmpty()) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        R.string.words_new_game_player_count,
                        selectedFriends.size + 1,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.words_new_game_language),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageButton(
                    label = stringResource(R.string.words_new_game_language_uk),
                    selected = language == "en_UK",
                    onClick = { language = "en_UK" },
                    modifier = Modifier.weight(1f),
                )
                LanguageButton(
                    label = stringResource(R.string.words_new_game_language_us),
                    selected = language == "en_US",
                    onClick = { language = "en_US" },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FriendsList(
    friends: List<NewGameFriend>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    // A plain Column, not a LazyColumn: the page already scrolls, and
    // nesting a second vertical scroller inside it crashes.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        friends.forEach { friend ->
            val selected = friend.id in selectedIds
            val disabled = !selected && selectedIds.size >= 3
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !disabled) { onToggle(friend.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { if (!disabled) onToggle(friend.id) },
                    enabled = !disabled,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EmptyFriendsBlock(onAddFriends: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.PersonAddAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.words_new_game_no_friends_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.words_new_game_no_friends_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Button(onClick = onAddFriends) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.words_new_game_add_friends))
        }
    }
}

@Composable
private fun LanguageButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}
