// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.chess.R
import org.mochios.chess.model.NewGameFriend
import org.mochios.android.R as MochiR

/**
 * AlertDialog for "Start a new chess game". Mirrors the web `NewGame` dialog:
 *
 *  - Loading state — small spinner inside the dialog body.
 *  - Error state — short error + retry, the simplest viable equivalent of
 *    web's `<GeneralError minimal mode="inline" reset={refetch} />`.
 *  - Empty state — "No friends yet" message with an "Add friends" button
 *    that fires [onAddFriends] (the host navigates to the People app's
 *    add-friend route).
 *  - Populated state — single-select scrollable friends list (auto-focuses
 *    by opening with a max-height bounded list rather than a separate
 *    auto-opened combobox; this is the Compose-native equivalent of the
 *    web's PersonPicker, which itself appears as a combobox-as-list inside
 *    the dialog body once data lands).
 *
 * On submit, [onCreated] receives the new game UID; [onToast] reports any
 * create error as a snackbar through the parent screen.
 */
@Composable
fun NewChessGameDialog(
    onDismiss: () -> Unit,
    onCreated: (gameId: String) -> Unit,
    onAddFriends: () -> Unit,
    onToast: (String) -> Unit,
    viewModel: NewChessGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val toastFailedToCreate = stringResource(R.string.chess_new_failed)

    AlertDialog(
        onDismissRequest = { if (!state.isCreating) onDismiss() },
        title = { Text(stringResource(R.string.chess_new_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.chess_new_pick_opponent),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    state.isLoadingFriends -> LoadingBox()
                    state.friendsError != null -> ErrorBox(
                        message = state.friendsError?.userMessage()
                            ?: stringResource(MochiR.string.error_unexpected),
                        onRetry = { viewModel.loadFriends() },
                    )
                    state.friends.isEmpty() -> NoFriendsBox(onAddFriends = onAddFriends)
                    else -> FriendsPicker(
                        friends = state.friends,
                        selectedId = state.selectedId,
                        onSelect = viewModel::select,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.create(
                        onCreated = onCreated,
                        onError = { message ->
                            onToast(message.ifBlank { toastFailedToCreate })
                        },
                    )
                },
                enabled = state.selectedId.isNotBlank() && !state.isCreating,
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.chess_new_creating))
                } else {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.chess_new_start_button))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isCreating,
            ) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(MochiR.string.common_retry))
        }
    }
}

@Composable
private fun NoFriendsBox(onAddFriends: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text = stringResource(R.string.chess_new_no_friends_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.chess_new_no_friends_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onAddFriends) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.chess_new_add_friends))
            }
        }
    }
}

@Composable
private fun FriendsPicker(
    friends: List<NewGameFriend>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(friends, key = { it.id }) { friend ->
            FriendRow(
                friend = friend,
                selected = friend.id == selectedId,
                onClick = { onSelect(friend.id) },
            )
        }
    }
}

@Composable
private fun FriendRow(
    friend: NewGameFriend,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = friend.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
