// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.words.R
import org.mochios.words.model.NewGameFriend
import org.mochios.android.R as MochiR

/**
 * "New game" dialog ported from `apps/words/web/src/features/words/components/new-game.tsx`.
 *
 * Composition (top to bottom):
 *   - Title "New game".
 *   - "Choose opponents (1-3)" label.
 *   - Multi-select PersonPicker over the user's friends (loaded lazily on
 *     dialog-open via the host's [onLoadFriends] callback). The web uses
 *     `apps/web`'s `PersonPicker mode="multiple"`; here we inline a
 *     checkbox list because the lib's `PersonPicker` is single-select.
 *   - "(N+1 players)" preview line when at least one friend is selected.
 *   - Language toggle: two buttons (English (UK) default / English (US)).
 *   - Cancel + Start game buttons in the footer.
 *
 * The empty-friends state mirrors the web: an icon + helper text + an
 * "Add friends" button that closes the dialog and links to the People
 * app via [onAddFriends].
 */
@Composable
fun NewWordsGameDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    friends: List<NewGameFriend>,
    isLoadingFriends: Boolean,
    friendsError: MochiError?,
    isCreating: Boolean,
    onLoadFriends: () -> Unit,
    onCreate: (opponents: List<String>, language: String) -> Unit,
    onAddFriends: () -> Unit,
) {
    if (!isOpen) return

    var selectedFriends by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var language by rememberSaveable { mutableStateOf("en_UK") }

    // Reset state on each open + lazy-load friends.
    LaunchedEffect(Unit) {
        selectedFriends = emptySet()
        language = "en_UK"
        onLoadFriends()
    }

    val canSubmit = selectedFriends.size in 1..3 && !isCreating

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.words_new_game_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    isLoadingFriends -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    friendsError != null -> {
                        Text(
                            text = friendsError.userMessage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    friends.isEmpty() -> {
                        EmptyFriendsBlock(onAddFriends = onAddFriends)
                    }
                    else -> {
                        FriendsList(
                            friends = friends,
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
                        text = stringResource(R.string.words_new_game_player_count, selectedFriends.size + 1),
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
        },
        confirmButton = {
            Button(
                onClick = { onCreate(selectedFriends.toList(), language) },
                enabled = canSubmit,
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(6.dp))
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                }
                Text(stringResource(R.string.words_new_game_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}

@Composable
private fun FriendsList(
    friends: List<NewGameFriend>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(friends, key = { it.id }) { friend ->
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
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
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
