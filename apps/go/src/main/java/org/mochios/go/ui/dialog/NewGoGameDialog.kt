package org.mochios.go.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import org.mochios.go.R
import org.mochios.go.model.NewGameFriend

/**
 * New Go game dialog. Mirrors the web `NewGameDialog`
 * (`apps/go/web/src/components/NewGameDialog.tsx`):
 *
 *  - single-select friend picker from the caller-supplied [friends] list
 *    (`repo.getNewGameFriends()`); empty state offers an Add Friends action
 *    that routes to the people app
 *  - board size selector: three preset buttons (9x9 / 13x13 / 19x19),
 *    19x19 is the default
 *  - komi selector: three preset buttons (6.5 / 7.5 / 0) plus a numeric
 *    `OutlinedTextField` for a free-form value clamped to 0–10
 *
 * The dialog is pure-input: it returns the chosen opponent id, board size,
 * and komi via [onStart]; the caller (game-list view model) wires this up
 * to `GoRepository.createGame`. [isPending] disables the controls while the
 * request is in flight.
 */
@Composable
fun NewGoGameDialog(
    open: Boolean,
    friends: List<NewGameFriend>,
    friendsLoading: Boolean,
    isPending: Boolean,
    onDismiss: () -> Unit,
    onStart: (opponent: String, boardSize: Int, komi: Double) -> Unit,
    onAddFriends: () -> Unit,
) {
    if (!open) return

    var selectedOpponent by remember { mutableStateOf<NewGameFriend?>(null) }
    var boardSize by remember { mutableStateOf(19) }
    var komiText by remember { mutableStateOf("6.5") }

    // Pre-select the first friend the moment the list lands so the dialog
    // doesn't open with an unselectable Start button when the user only has
    // one friend (web uses the same nudge via the radio's default value).
    LaunchedEffect(friends) {
        if (selectedOpponent == null && friends.size == 1) {
            selectedOpponent = friends.first()
        }
    }

    val komiValue = komiText.toDoubleOrNull()
    val komiValid = komiValue != null && komiValue in 0.0..10.0
    val canStart = !isPending && selectedOpponent != null && komiValid

    AlertDialog(
        onDismissRequest = { if (!isPending) onDismiss() },
        title = { Text(stringResource(R.string.go_new_game_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.go_new_game_friend_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    friendsLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 96.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    friends.isEmpty() -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.go_new_game_no_friends),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onAddFriends,
                                enabled = !isPending,
                            ) {
                                Text(stringResource(R.string.go_new_game_add_friends))
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                        ) {
                            items(friends, key = { it.id }) { friend ->
                                FriendRow(
                                    friend = friend,
                                    selected = selectedOpponent?.id == friend.id,
                                    enabled = !isPending,
                                    onSelect = { selectedOpponent = friend },
                                )
                                HorizontalDivider()
                            }
                        }
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
                    onSelect = { boardSize = it },
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
                    onSelect = { komiText = it },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = komiText,
                    onValueChange = { komiText = it },
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val opponent = selectedOpponent ?: return@Button
                    val komi = komiValue ?: return@Button
                    onStart(opponent.id, boardSize, komi)
                },
                enabled = canStart,
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.go_new_game_start))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPending) {
                Text(stringResource(R.string.go_new_game_cancel))
            }
        },
    )
}

@Composable
private fun FriendRow(
    friend: NewGameFriend,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Material 3 RadioButton would have implied "form" rules around
        // a/b/c selection group sizing; sticking to a clickable Row keeps
        // the look closer to the web dialog where the row itself is the
        // hit target.
        Text(
            text = if (selected) "•" else " ",
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = friend.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
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
                Button(
                    onClick = { onSelect(size) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.go_new_game_board_size_value, size, size)) }
            } else {
                OutlinedButton(
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
                Button(
                    onClick = { onSelect(preset) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(preset) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(preset) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(preset) }
            }
        }
    }
}
