// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.newgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.model.User
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.PersonPicker
import org.mochios.android.ui.components.MochiTextField
import org.mochios.go.R
import org.mochios.go.model.NewGameFriend
import org.mochios.android.R as MochiR

/**
 * Screen for starting a go game: opponent picker, board-size and komi presets,
 * Start in the bottom bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGoGameScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onAddFriends: () -> Unit,
    viewModel: NewGoGameViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedOpponent by remember { mutableStateOf<NewGameFriend?>(null) }
    var boardSize by remember { mutableStateOf(19) }
    var komiText by remember { mutableStateOf("6.5") }

    // Pre-select the only friend the moment the list lands, so a user with a
    // single friend isn't left with an unusable Start button.
    LaunchedEffect(uiState.friends) {
        if (selectedOpponent == null && uiState.friends.size == 1) {
            selectedOpponent = uiState.friends.first()
        }
    }

    LaunchedEffect(uiState.createdGameId) {
        uiState.createdGameId?.let { newId ->
            onCreated(newId)
            viewModel.consumeCreatedGame()
        }
    }

    val komiValue = komiText.toDoubleOrNull()
    val komiValid = komiValue != null && komiValue in 0.0..10.0
    val isPending = uiState.isCreating
    val canStart = !isPending && selectedOpponent != null && komiValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.go_new_game_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack, enabled = !isPending) {
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
                        .imePadding()
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
                        onClick = {
                            val opponent = selectedOpponent ?: return@MochiButton
                            val komi = komiValue ?: return@MochiButton
                            viewModel.createGame(opponent.id, boardSize, komi)
                        },
                        enabled = canStart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isPending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.go_new_game_start))
                        }
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
            Text(
                text = stringResource(R.string.go_new_game_friend_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                uiState.friendsLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                uiState.friendsError != null -> {
                    InlineErrorState(
                        message = uiState.friendsError?.userMessage()
                            ?: stringResource(MochiR.string.error_unexpected),
                        onRetry = { viewModel.loadFriends() }.takeIf { !isPending },
                    )
                }

                uiState.friends.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.go_new_game_no_friends),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MochiOutlinedButton(onClick = onAddFriends, enabled = !isPending) {
                            Text(stringResource(R.string.go_new_game_add_friends))
                        }
                    }
                }

                else -> {
                    val people = remember(uiState.friends) {
                        uiState.friends.mapIndexed { index, friend ->
                            User(id = index, name = friend.name, fingerprint = friend.id)
                        }
                    }
                    PersonPicker(
                        selectedId = selectedOpponent?.id,
                        selectedName = selectedOpponent?.name,
                        members = people,
                        onSelect = { user ->
                            selectedOpponent = uiState.friends.firstOrNull { friend ->
                                friend.id == user.fingerprint
                            }
                        },
                        onClear = { selectedOpponent = null },
                        // Go plays with the friends the server listed; there is
                        // no directory lookup behind this picker.
                        onSearch = { emptyList() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.go_new_game_board_size_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            BoardSizeRow(
                boardSize = boardSize,
                enabled = !isPending,
                onSelect = { size -> boardSize = size },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.go_new_game_komi_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            KomiPresetRow(
                komiText = komiText,
                enabled = !isPending,
                onSelect = { preset -> komiText = preset },
            )
            Spacer(modifier = Modifier.height(8.dp))
            MochiTextField(
                value = komiText,
                onValueChange = { value -> komiText = value },
                label = { Text(stringResource(R.string.go_new_game_komi_field_label)) },
                singleLine = true,
                enabled = !isPending,
                isError = !komiValid,
                supportingText = {
                    if (!komiValid) {
                        Text(stringResource(R.string.go_new_game_komi_range_error))
                    } else {
                        Text(stringResource(R.string.go_new_game_komi_hint))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BoardSizeRow(
    boardSize: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(9, 13, 19).forEach { size ->
            val isSelected = boardSize == size
            if (isSelected) {
                MochiButton(
                    onClick = { onSelect(size) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.go_new_game_board_size_value, size, size)) }
            } else {
                MochiOutlinedButton(
                    onClick = { onSelect(size) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.go_new_game_board_size_value, size, size)) }
            }
        }
    }
}

@Composable
private fun KomiPresetRow(
    komiText: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Presets cover the two common ratings (6.5 / 7.5) plus a no-komi
        // variant for handicap-style games. Selection is by text match so
        // the field-driven control state stays the single source of truth.
        listOf("6.5", "7.5", "0").forEach { preset ->
            val isSelected = komiText == preset
            if (isSelected) {
                MochiButton(
                    onClick = { onSelect(preset) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(preset) }
            } else {
                MochiOutlinedButton(
                    onClick = { onSelect(preset) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(preset) }
            }
        }
    }
}
