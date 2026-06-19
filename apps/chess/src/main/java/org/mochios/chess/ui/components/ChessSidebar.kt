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
 * Drawer body for the chess app's left rail. Mirrors the web's `ChessLayout`
 * sidebar (`apps/chess/web/src/components/layout/chess-layout.tsx`):
 *
 *  - Two grouped sections — Active Games (`status == "active"`) and
 *    Completed (everything else) — both ordered by `updated` desc so the
 *    most-recently-touched game is on top, matching the web's
 *    `[...games].sort((a, b) => b.updated - a.updated)` and per-section
 *    filter.
 *  - Each row carries the opponent's avatar (loaded from the People app's
 *    avatar action) + their display name. Completed-game rows append a
 *    one-word status badge (`(checkmate)`, `(stalemate)`, etc.) the same
 *    way the web sidebar does.
 *  - A "New game" footer-style row at the bottom, separated by a divider,
 *    that fires [onOpenNewGame] (the host opens the dialog).
 *  - An optional [websocketStatusLabel] / [websocketStatusColor] pair drives
 *    a tiny status row underneath the new-game button when a game is open
 *    (the parallel game-detail agent wires this when its WS connects). Null
 *    by default — the empty list state has no socket to report on.
 *
 * Rows on selectable games invoke [onOpenGame] with the game id /
 * fingerprint; the host navigates to the detail screen.
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
                            statusSuffix = null,
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
                            statusSuffix = row.statusLabel,
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
    statusSuffix: String?,
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
            text = if (statusSuffix != null) "${game.opponentName} ($statusSuffix)" else game.opponentName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Sidebar row projection. Built by [org.mochios.chess.ui.list.ChessGameListViewModel]
 * from each [org.mochios.chess.model.Game] + the caller's identity. Kept
 * deliberately flat so the sidebar composable doesn't have to know about
 * the caller's identity or where opponent-name resolution happens.
 *
 *  - [id] is the game's fingerprint when present, else the row UID — used as
 *    the URL path segment when navigating to the detail screen.
 *  - [opponentId] is the entity ID of the other player (the side that isn't
 *    the caller).
 *  - [opponentName] is the resolved display name of [opponentId].
 *  - [statusLabel] is the lower-cased status word (`"checkmate"`,
 *    `"stalemate"`, `"draw"`, `"resigned"`) on completed games, null for
 *    active games. Web's `(${game.status})` translates here.
 *  - [updated] is the source row's `updated` timestamp, retained for the
 *    sort-stable secondary key.
 */
data class ChessSidebarGame(
    val id: String,
    val opponentId: String,
    val opponentName: String,
    val statusLabel: String? = null,
    val updated: Long = 0,
)

