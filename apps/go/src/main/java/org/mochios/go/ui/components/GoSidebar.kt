// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.go.R
import org.mochios.go.model.Game

/**
 * Drawer rows for the Go app: one per game, active games first, each headed
 * by its section. [myIdentity] resolves the opponent, since `opponent_name`
 * names the invitee on both peers.
 */
@Composable
fun goDrawerItems(games: List<Game>, myIdentity: String): List<DrawerItem> {
    val activeLabel = stringResource(R.string.go_sidebar_active)
    val completedLabel = stringResource(R.string.go_sidebar_completed)
    val active = games.filter { game -> game.status == "active" }
    val completed = games.filterNot { game -> game.status == "active" }
    return buildList {
        for (game in active) add(game.toDrawerItem(myIdentity, activeLabel))
        for (game in completed) add(game.toDrawerItem(myIdentity, completedLabel))
    }
}

@Composable
private fun Game.toDrawerItem(myIdentity: String, section: String): DrawerItem {
    val opponentId = opponentId(myIdentity)
    return DrawerItem(
        id = id,
        title = opponentName(myIdentity),
        subtitle = stringResource(R.string.go_card_meta, boardSize, boardSize, statusLabel(status)),
        // Blank opponent id means no avatar asset path; the row still gets a
        // seeded initials circle from the drawer's seed-without-icon branch.
        avatarUrl = if (opponentId.isNotBlank()) "/people/$opponentId/-/avatar" else null,
        seed = opponentId,
        section = section,
    )
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    "active" -> stringResource(R.string.go_status_active)
    "finished" -> stringResource(R.string.go_status_finished)
    "draw" -> stringResource(R.string.go_status_draw)
    "resigned" -> stringResource(R.string.go_status_resigned)
    else -> status
}
