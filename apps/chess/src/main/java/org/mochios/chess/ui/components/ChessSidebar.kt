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
 * The player's games as drawer rows, grouped into Active and Completed by
 * [DrawerItem.section].
 *
 * [org.mochios.chess.ui.list.ChessGameListScreen] wraps its body in
 * [org.mochios.android.ui.components.MochiListDrawer] and passes this list,
 * so chess's drawer matches every other app's. "New game" is not here — it
 * pushes a route rather than opening a game, so it belongs in the drawer's
 * bottom actions slot.
 *
 * A section whose list is empty contributes no heading. Item ids are game
 * ids, which the host navigates to directly; [activeGames] and
 * [completedGames] are a partition of one list (status active / not) so the
 * ids stay unique across both.
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
 * One row's worth of game data, pre-flattened by
 * [org.mochios.chess.ui.list.ChessGameListViewModel] so the drawer doesn't
 * have to know the wire [org.mochios.chess.model.Game] shape.
 */
data class ChessSidebarGame(
    val id: String,
    val opponentId: String,
    val opponentName: String,
    val updated: Long = 0,
)
