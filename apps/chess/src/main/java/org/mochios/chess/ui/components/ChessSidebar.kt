// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.chess.R

/**
 * Drawer for the chess app: active and completed games (newest `updated`
 * first), a New game row, and an optional socket status row.
 */
@Composable
fun chessDrawerItems(
    activeGames: List<ChessSidebarGame>,
    completedGames: List<ChessSidebarGame>,
): List<DrawerItem> {
    val activeLabel = stringResource(R.string.chess_sidebar_active)
    val completedLabel = stringResource(R.string.chess_sidebar_completed)
    return buildList {
        for (game in activeGames) add(game.toDrawerItem(activeLabel))
        for (game in completedGames) add(game.toDrawerItem(completedLabel))
    }
}

private fun ChessSidebarGame.toDrawerItem(section: String) = DrawerItem(
    id = id,
    title = opponentName,
    // Blank opponent id means no avatar asset path; the row still gets a
    // seeded initials circle from the drawer's seed-without-icon branch.
    avatarUrl = if (opponentId.isNotBlank()) "/people/$opponentId/-/avatar" else null,
    seed = opponentId,
    section = section,
)

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
