// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.repository

import org.mochios.android.api.unwrap
import org.mochios.words.api.WordsApi
import org.mochios.words.model.CreateGameRequest
import org.mochios.words.model.ExchangeRequest
import org.mochios.words.model.GameListItem
import org.mochios.words.model.GameViewResponse
import org.mochios.words.model.GetMessagesResponse
import org.mochios.words.model.MoveRequest
import org.mochios.words.model.NewGameFriend
import org.mochios.words.model.SendMessageRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordsRepository @Inject constructor(
    private val api: WordsApi,
) {

    // ---- Listing + lifecycle ----

    suspend fun listGames(): List<GameListItem> =
        api.listGames().unwrap()

    suspend fun getNewGameFriends(): List<NewGameFriend> =
        api.getNewGameFriends().unwrap().friends

    /**
     * [opponents] must be 1-3 friends' entity IDs, comma-joined for the server.
     * [language] is `"en_US"` or `"en_UK"`; anything else is a 400.
     */
    suspend fun createGame(opponents: List<String>, language: String): String =
        api.createGame(
            CreateGameRequest(
                opponents = opponents.joinToString(","),
                language = language,
            )
        ).unwrap().id

    // ---- Per-game read ----

    suspend fun getGame(gameId: String): GameViewResponse =
        api.viewGame(gameId).unwrap()

    suspend fun getMessages(
        gameId: String,
        before: String? = null,
        limit: Int? = null,
    ): GetMessagesResponse =
        api.getMessages(gameId, before, limit).unwrap()

    // ---- Per-game write ----

    suspend fun sendMessage(gameId: String, body: String): String =
        api.sendMessage(gameId, SendMessageRequest(body)).unwrap().id

    suspend fun move(gameId: String, request: MoveRequest): String =
        api.move(gameId, request).unwrap().id

    suspend fun pass(gameId: String): String =
        api.pass(gameId).unwrap().id

    suspend fun exchange(gameId: String, tiles: String): String =
        api.exchange(gameId, ExchangeRequest(tiles)).unwrap().id

    suspend fun resign(gameId: String): Boolean =
        api.resign(gameId).unwrap().success

    suspend fun deleteGame(gameId: String): Boolean =
        api.delete(gameId).unwrap().success

    // ---- Dictionary lookup ----

    /**
     * Dictionary lookup. Words under 2 or over 15 characters return false
     * without a DB hit.
     */
    suspend fun validateWord(word: String, language: String): Boolean =
        api.validateWord(word, language).unwrap().valid
}
