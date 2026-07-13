// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

/**
 * Lightweight game record returned by `-/list`. Mirrors the `GameListItem`
 * interface in `apps/words/web/src/api/types/games.ts` — same fields, same
 * snake_case wire keys, but without the per-player rack info (`my_rack`,
 * `bag_count`) that only the `:game/-/view` endpoint exposes.
 *
 * The list response is `{"data": [game, ...]}`; `Response<ApiResponse<List<GameListItem>>>`
 * unwraps the envelope and the list itself flows up the call chain.
 */
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
 * Full game record returned by `:game/-/view`. Adds the per-viewer fields
 * the server strips out of the list response: `my_rack` is the caller's
 * private rack contents (only filled for the player whose token is in the
 * Authorization header) and `bag_count` is the size of the remaining bag
 * (so the UI can show a "tiles left" indicator without ever seeing the bag
 * itself). `key` is the websocket key for real-time updates.
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

/**
 * Return the comma-joined display names of all players *other than* the
 * caller. Mirrors the `getPlayerNames` helper in the web types module
 * (`apps/words/web/src/api/types/games.ts`). The web version is generic
 * over `Game | GameListItem`; Kotlin's `data class` lookup goes through a
 * small reflection-free helper that reads each indexed player slot.
 */
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

fun getPlayerNames(game: Game, myIdentity: String): String {
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
 * Return true iff [myIdentity] is the player whose turn it is right now.
 * `current_turn` is 1-based and counts up to `player_count`; players who
 * aren't in the game (or finished/resigned games) always read false. Same
 * shape as the web `isMyTurn` helper.
 */
fun isMyTurn(game: GameListItem, myIdentity: String): Boolean {
    if (game.status != "active") return false
    val myNum = getMyPlayerNumber(game, myIdentity)
    return game.current_turn == myNum
}

fun isMyTurn(game: Game, myIdentity: String): Boolean {
    if (game.status != "active") return false
    val myNum = getMyPlayerNumber(game, myIdentity)
    return game.current_turn == myNum
}

private fun getMyPlayerNumber(game: GameListItem, myIdentity: String): Int {
    for (i in 1..game.player_count) {
        if (playerId(game, i) == myIdentity) return i
    }
    return 0
}

private fun getMyPlayerNumber(game: Game, myIdentity: String): Int {
    for (i in 1..game.player_count) {
        if (playerId(game, i) == myIdentity) return i
    }
    return 0
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

fun playerScore(game: Game, slot: Int): Int = when (slot) {
    1 -> game.player1_score
    2 -> game.player2_score
    3 -> game.player3_score
    4 -> game.player4_score
    else -> 0
}
