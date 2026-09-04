// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

data class GameListItem(
    val id: String = "",
    val fingerprint: String? = null,
    val language: String = "en_US",
    val player_count: Int = 2,
    val player1: String = "",
    val player1_name: String = "",
    val player1_score: Int = 0,
    val player2: String = "",
    val player2_name: String = "",
    val player2_score: Int = 0,
    val player3: String? = null,
    val player3_name: String? = null,
    val player3_score: Int = 0,
    val player4: String? = null,
    val player4_name: String? = null,
    val player4_score: Int = 0,
    val current_turn: Int = 1,
    val status: String = "active",
    val winner: String? = null,
    val board: String = "",
    val my_player_number: Int = 0,
    val move_count: Int = 0,
    val consecutive_passes: Int = 0,
    val updated: Long = 0,
    val created: Long = 0,
)

/**
 * Full game from `:game/-/view`. `my_rack` holds only the calling player's
 * rack, `bag_count` the tiles left in the bag, `key` the websocket key.
 */
data class Game(
    val id: String = "",
    val fingerprint: String? = null,
    val language: String = "en_US",
    val player_count: Int = 2,
    val player1: String = "",
    val player1_name: String = "",
    val player1_score: Int = 0,
    val player2: String = "",
    val player2_name: String = "",
    val player2_score: Int = 0,
    val player3: String? = null,
    val player3_name: String? = null,
    val player3_score: Int = 0,
    val player4: String? = null,
    val player4_name: String? = null,
    val player4_score: Int = 0,
    val current_turn: Int = 1,
    val status: String = "active",
    val winner: String? = null,
    val board: String = "",
    val my_rack: String = "",
    val my_player_number: Int = 0,
    val bag_count: Int = 0,
    val move_count: Int = 0,
    val consecutive_passes: Int = 0,
    val key: String = "",
    val updated: Long = 0,
    val created: Long = 0,
)

fun getPlayerNames(game: GameListItem, myIdentity: String): String {
    val names = mutableListOf<String>()
    for (i in 1..game.player_count) {
        val id = playerId(game, i)
        val name = playerName(game, i)
        if (!id.isNullOrEmpty() && id != myIdentity && !name.isNullOrEmpty()) {
            names.add(name)
        }
    }
    return names.joinToString(", ")
}

/**
 * Identity of the first player who is not the viewer, used for the avatar on a
 * game row. Falls back to the player slot when the viewer's identity is not
 * known yet; empty when the game has no other player.
 *
 * @param game the game to read.
 * @param myIdentity the viewer's identity, empty when it has not loaded.
 * @return the opponent's identity, or an empty string.
 */
fun getOpponentId(game: GameListItem, myIdentity: String): String {
    for (slot in 1..game.player_count) {
        val id = playerId(game, slot).orEmpty()
        if (id.isEmpty()) continue
        val isMe = if (myIdentity.isNotEmpty()) id == myIdentity else slot == game.my_player_number
        if (!isMe) return id
    }
    return ""
}

private fun playerId(game: GameListItem, slot: Int): String? = when (slot) {
    1 -> game.player1
    2 -> game.player2
    3 -> game.player3
    4 -> game.player4
    else -> null
}

private fun playerId(game: Game, slot: Int): String? = when (slot) {
    1 -> game.player1
    2 -> game.player2
    3 -> game.player3
    4 -> game.player4
    else -> null
}

private fun playerName(game: GameListItem, slot: Int): String? = when (slot) {
    1 -> game.player1_name
    2 -> game.player2_name
    3 -> game.player3_name
    4 -> game.player4_name
    else -> null
}

private fun playerName(game: Game, slot: Int): String? = when (slot) {
    1 -> game.player1_name
    2 -> game.player2_name
    3 -> game.player3_name
    4 -> game.player4_name
    else -> null
}

fun playerScore(game: GameListItem, slot: Int): Int = when (slot) {
    1 -> game.player1_score
    2 -> game.player2_score
    3 -> game.player3_score
    4 -> game.player4_score
    else -> 0
}

