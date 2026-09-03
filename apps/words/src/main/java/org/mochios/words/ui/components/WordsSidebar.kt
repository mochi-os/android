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
        for (game in active) add(game.toDrawerItem(myIdentity, activeLabel))
        for (game in completed) add(game.toDrawerItem(myIdentity, completedLabel))
    }
}

private fun GameListItem.toDrawerItem(myIdentity: String, section: String): DrawerItem {
    val names = getPlayerNames(this, myIdentity).ifBlank { "$player_count players" }
    val scores = buildString {
        for (i in 1..player_count) {
            if (i > 1) append(" · ")
            append(playerScore(this@toDrawerItem, i))
        }
    }
    return DrawerItem(
        id = fingerprint?.ifBlank { null } ?: id,
        title = "$names ($scores)",
        section = section,
    )
}
