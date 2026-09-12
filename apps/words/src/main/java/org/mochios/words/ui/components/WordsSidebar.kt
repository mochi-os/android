// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.DrawerItem
import org.mochios.words.R
import org.mochios.words.model.GameListItem
import org.mochios.words.model.getOpponentId
import org.mochios.words.model.getPlayerNames
import org.mochios.words.model.playerScore

@Composable
fun wordsDrawerItems(
    games: List<GameListItem>,
    myIdentity: String,
): List<DrawerItem> {
    val activeLabel = stringResource(R.string.words_sidebar_active)
    val completedLabel = stringResource(R.string.words_sidebar_completed)
    val active = games.filter { it.status == "active" }
    val completed = games.filter { it.status == "finished" || it.status == "resigned" }
    return buildList {
        for (game in active) {
            add(game.toDrawerItem(myIdentity, activeLabel, game.playersLabel()))
        }
        for (game in completed) {
            add(game.toDrawerItem(myIdentity, completedLabel, game.playersLabel()))
        }
    }
}

/**
 * "N players", for a game whose seats have no names yet. A game always seats
 * two to four, so the singular form never arises and the plain string - which
 * every catalogue already carries - is enough.
 */
@Composable
private fun GameListItem.playersLabel(): String =
    stringResource(R.string.words_detail_player_count, player_count)

private fun GameListItem.toDrawerItem(
    myIdentity: String,
    section: String,
    playersLabel: String,
): DrawerItem {
    val names = getPlayerNames(this, myIdentity).ifBlank { playersLabel }
    val scores = buildString {
        for (i in 1..player_count) {
            if (i > 1) append(" · ")
            append(playerScore(this@toDrawerItem, i))
        }
    }
    val opponentId = getOpponentId(this, myIdentity)
    return DrawerItem(
        id = id,
        title = "$names ($scores)",
        // The game-bound proxy, as web uses: a cross-app request to the people
        // app carries this app's token and is refused.
        avatarUrl = if (opponentId.isNotEmpty()) {
            "/words/$id/-/user/$opponentId/asset/avatar"
        } else {
            null
        },
        seed = opponentId,
        section = section,
    )
}
