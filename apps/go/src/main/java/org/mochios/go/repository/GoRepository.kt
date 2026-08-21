// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.repository

import org.mochios.android.api.unwrap
import org.mochios.go.api.GoApi
import org.mochios.go.model.CreateGameRequest
import org.mochios.go.model.CreateGameResponse
import org.mochios.go.model.Game
import org.mochios.go.model.GameMessage
import org.mochios.go.model.GameViewResponse
import org.mochios.go.model.GetMessagesResponse
import org.mochios.go.model.MoveRequest
import org.mochios.go.model.NewGameFriend
import org.mochios.go.model.PassRequest
import org.mochios.go.model.SendMessageRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [GoApi]; each call unwraps the response into the payload
 * or a typed `MochiError`.
 */
@Singleton
class GoRepository @Inject constructor(
    private val api: GoApi,
) {

    /** All games the user is a player in, sorted server-side by `updated DESC`. */
    suspend fun listGames(): List<Game> =
        api.listGames().unwrap()

    /** Full state for a single game, including the asking user's identity id. */
    suspend fun getGame(gameId: String): GameViewResponse =
        api.viewGame(gameId).unwrap()

    /**
     * Chat + move log. [before] is the previous response's `nextCursor`, a
     * `"<created>:<id>"` pair - paging on `created` alone drops rows sharing
     * the boundary second. The server caps [limit] at 100.
     */
    suspend fun getMessages(
        gameId: String,
        before: String? = null,
        limit: Int? = null,
    ): GetMessagesResponse =
        api.getMessages(gameId, before, limit).unwrap()

    suspend fun sendMessage(gameId: String, body: String): String =
        api.sendMessage(gameId, SendMessageRequest(body = body)).unwrap().id

    /**
     * Create a game. The server assigns colours randomly and returns the new id
     * and who plays Black.
     */
    suspend fun createGame(
        opponent: String,
        boardSize: Int = 19,
        komi: Double = 6.5,
    ): CreateGameResponse =
        api.createGame(
            CreateGameRequest(
                opponent = opponent,
                boardSize = boardSize,
                komi = komi,
            ),
        ).unwrap()

    /** Friends that are valid candidates for a new game (the local friends list). */
    suspend fun getNewGameFriends(): List<NewGameFriend> =
        api.getNewGameFriends().unwrap().friends

    suspend fun move(gameId: String, request: MoveRequest): String =
        api.move(gameId, request).unwrap().id

    /**
     * Pass. On the second consecutive pass the caller sends `status =
     * "finished"` with winner and scores so the server records the end in one
     * round trip.
     */
    suspend fun pass(gameId: String, request: PassRequest): String =
        api.pass(gameId, request).unwrap().id

    suspend fun resign(gameId: String) {
        api.resign(gameId).unwrap()
    }

    suspend fun drawOffer(gameId: String) {
        api.drawOffer(gameId).unwrap()
    }

    suspend fun drawAccept(gameId: String) {
        api.drawAccept(gameId).unwrap()
    }

    suspend fun drawDecline(gameId: String) {
        api.drawDecline(gameId).unwrap()
    }

    suspend fun deleteGame(gameId: String) {
        api.delete(gameId).unwrap()
    }
}

