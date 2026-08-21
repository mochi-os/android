// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.chess.R

/**
 * Drawer for the chess app: active and completed games (newest `updated`
 * first), a New game row, and an optional socket status row.
 */
@Composable
fun ChessSidebar(
    activeGames: List<ChessSidebarGame>,
    completedGames: List<ChessSidebarGame>,
    onOpenGame: (gameId: String) -> Unit,
    onOpenNewGame: () -> Unit,
    websocketStatusLabel: String? = null,
    websocketStatusColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text = stringResource(R.string.chess_sidebar_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (activeGames.isNotEmpty()) {
                    item("active-header") {
                        SidebarSectionHeader(stringResource(R.string.chess_sidebar_active))
                    }
                    items(activeGames, key = { "active-${it.id}" }) { row ->
                        SidebarGameRow(
                            game = row,
                            onClick = { onOpenGame(row.id) },
                        )
                    }
                }
                if (completedGames.isNotEmpty()) {
                    item("completed-header") {
                        SidebarSectionHeader(stringResource(R.string.chess_sidebar_completed))
                    }
                    items(completedGames, key = { "completed-${it.id}" }) { row ->
                        SidebarGameRow(
                            game = row,
                            onClick = { onOpenGame(row.id) },
                        )
                    }
                }
            }

            HorizontalDivider()
            OutlinedButton(
                onClick = onOpenNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.chess_sidebar_new_game))
            }

            if (websocketStatusLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(websocketStatusColor ?: Color.Gray),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = websocketStatusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SidebarGameRow(
    game: ChessSidebarGame,
    onClick: () -> Unit,
) {
    val avatarUrl = if (game.opponentId.isNotBlank()) {
        "/people/${game.opponentId}/-/avatar"
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EntityAvatar(
            name = game.opponentName,
            src = avatarUrl,
            seed = game.opponentId,
            size = 28.dp,
        )
        Text(
            text = game.opponentName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Flat sidebar row; [id] is the fingerprint when present, else the row id, and
 * is the route segment.
 */
data class ChessSidebarGame(
    val id: String,
    val opponentId: String,
    val opponentName: String,
    val updated: Long = 0,
)

