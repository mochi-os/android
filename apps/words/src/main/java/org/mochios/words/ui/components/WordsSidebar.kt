// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.words.R
import org.mochios.words.model.GameListItem
import org.mochios.words.model.getPlayerNames
import org.mochios.words.model.playerScore

/**
 * Slide-in drawer for the Words app's game list, rendered when the user
 * taps the hamburger from either the list or the detail screen.
 *
 * Two sections in the same scroll: "Active games" first, then "Completed"
 * for any game whose status is `"finished"` or `"resigned"`. Each row
 * shows the other players' names and the running player1 / player2 …
 * scores so the user can spot which game is theirs to play next without
 * opening it. A "New game" affordance pinned at the bottom opens the
 * NewWordsGameDialog via the host screen's callback.
 *
 * The web app puts active/completed inside a single sidebar with section
 * headers (`apps/words/web/src/components/layout/words-layout.tsx`); we
 * mirror that here so the Android drawer reads the same way.
 */
@Composable
fun WordsSidebar(
    games: List<GameListItem>,
    myIdentity: String,
    selectedGameId: String?,
    onSelectGame: (GameListItem) -> Unit,
    onNewGame: () -> Unit,
) {
    val active = games.filter { it.status == "active" }
    val completed = games.filter { it.status == "finished" || it.status == "resigned" }

    ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = stringResource(R.string.words_sidebar_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (active.isNotEmpty()) {
                    item("__active_header__") {
                        SectionHeader(stringResource(R.string.words_sidebar_active))
                    }
                    items(active, key = { "a_${it.id}" }) { game ->
                        GameRow(
                            game = game,
                            myIdentity = myIdentity,
                            isSelected = isSelected(game, selectedGameId),
                            onClick = { onSelectGame(game) },
                        )
                    }
                }
                if (completed.isNotEmpty()) {
                    item("__completed_header__") {
                        SectionHeader(stringResource(R.string.words_sidebar_completed))
                    }
                    items(completed, key = { "c_${it.id}" }) { game ->
                        GameRow(
                            game = game,
                            myIdentity = myIdentity,
                            isSelected = isSelected(game, selectedGameId),
                            onClick = { onSelectGame(game) },
                        )
                    }
                }
                if (active.isEmpty() && completed.isEmpty()) {
                    item("__empty__") {
                        Text(
                            text = stringResource(R.string.words_sidebar_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }
            }

            HorizontalDivider()
            TextButton(
                onClick = onNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.words_sidebar_new_game))
            }
        }
    }
}

private fun isSelected(game: GameListItem, selectedGameId: String?): Boolean {
    if (selectedGameId.isNullOrBlank()) return false
    return game.id == selectedGameId || game.fingerprint == selectedGameId
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun GameRow(
    game: GameListItem,
    myIdentity: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surface

    val names = getPlayerNames(game, myIdentity).ifBlank {
        // Fallback: if the names aren't populated yet, hold space with
        // the player count rather than rendering a blank row.
        "${game.player_count} players"
    }

    val scoresLine = buildString {
        for (i in 1..game.player_count) {
            if (i > 1) append(" · ")
            append(playerScore(game, i))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = names,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = scoresLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
