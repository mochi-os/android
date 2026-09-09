// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

data class GameListItem(
    val id: String = "",
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
    // Identity of the last player to change the game. On a RESIGNED game that
    // is the resigner: every play action refuses a game that is not active and
    // a terminal status dominates the ordering tuple, so nothing writes after
    // the resignation. It is what tells the resigner apart from the other
    // players who simply did not win.
    val writer: String? = null,
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

/** Tiles the bag must still hold for `action_exchange` to accept a swap. */
const val EXCHANGE_MINIMUM = 7

/**
 * Whether a swap would be accepted. The server refuses one once the bag is
 * below [EXCHANGE_MINIMUM], so the control is not offered there.
 */
fun canExchange(game: Game): Boolean = game.bag_count >= EXCHANGE_MINIMUM

/**
 * Seat of the player who resigned, or null when the game names no writer or
 * the writer holds no seat.
 */
fun resignerSlot(game: Game): Int? {
    val writer = game.writer
    if (writer.isNullOrEmpty()) return null
    for (slot in 1..game.player_count) {
        if (playerId(game, slot) == writer) return slot
    }
    return null
}

/**
 * Whether the viewer is the player who resigned. With up to four seats a
 * resignation leaves players who neither resigned nor won, so this has to be
 * asked separately from "did I win" - they used to be told they had resigned.
 *
 * @param game the game to read.
 * @param myIdentity the viewer's identity, empty when it has not loaded.
 */
fun isResigner(game: Game, myIdentity: String): Boolean {
    val writer = game.writer
    if (writer.isNullOrEmpty()) return false
    if (myIdentity.isNotEmpty()) return writer == myIdentity
    val slot = resignerSlot(game) ?: return false
    return slot == game.my_player_number
}

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

