// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.newgame

import androidx.compose.foundation.border
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.model.User
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MultiPersonPicker
import org.mochios.words.R
import org.mochios.words.model.NewGameFriend
import org.mochios.android.R as MochiR

/** Opponents a words game can hold besides the player: four seats, minus one. */
private const val MAX_OPPONENTS = 3

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
    val canSubmit = selectedFriends.size in 1..MAX_OPPONENTS && !isCreating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.words_new_game_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack, enabled = !isCreating) {
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
                    MochiButton(
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
                    InlineErrorState(
                        message = uiState.friendsError?.userMessage()
                            ?: stringResource(MochiR.string.error_unexpected),
                    )
                }

                uiState.friends.isEmpty() -> {
                    EmptyFriendsBlock(onAddFriends = onAddFriends)
                }

                else -> {
                    val people = remember(uiState.friends) {
                        uiState.friends.mapIndexed { index, friend ->
                            User(id = index, name = friend.name, fingerprint = friend.id)
                        }
                    }
                    MultiPersonPicker(
                        selectedIds = selectedFriends,
                        members = people,
                        onToggle = { user ->
                            val id = user.fingerprint ?: return@MultiPersonPicker
                            selectedFriends = selectedFriends.toMutableSet().apply {
                                if (!remove(id)) add(id)
                            }
                        },
                        onClear = { selectedFriends = emptySet() },
                        // Words plays with friends the server already listed;
                        // there is no directory lookup behind this picker.
                        onSearch = { emptyList() },
                        maxSelection = MAX_OPPONENTS,
                        modifier = Modifier.fillMaxWidth(),
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
        MochiButton(onClick = onAddFriends) {
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
        MochiButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(label)
        }
    } else {
        MochiOutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}
