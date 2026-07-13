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
 * Thin convenience wrapper around [GoApi]. Mirrors the
 * `PeopleRepository` / `ChatRepository` pattern: each method calls
 * `.unwrap()` on the wrapped `ApiResponse<T>` to surface either the inner
 * payload or a typed `MochiError` for the ViewModels to render. No caching
 * layer — Go games are small in number and the next-wave game-detail screen
 * pulls via TanStack-style stale-while-revalidate inside the ViewModel.
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
     * In-game chat + move log. [before] is the cursor (`created` timestamp of
     * the oldest message already loaded) for paging older messages; [limit] is
     * an optional page-size override (the server caps at 100).
     */
    suspend fun getMessages(
        gameId: String,
        before: Long? = null,
        limit: Int? = null,
    ): GetMessagesResponse =
        api.getMessages(gameId, before, limit).unwrap()

    suspend fun sendMessage(gameId: String, body: String): String =
        api.sendMessage(gameId, SendMessageRequest(body = body)).unwrap().id

    /**
     * Create a new game against [opponent] on a [boardSize]x[boardSize] board
     * with the given [komi] (compensation for White). Server assigns colours
     * randomly; the response includes the new game id and the identity of the
     * player chosen to play Black.
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

    /**
     * Submit a move. [request] is composed by the caller (typically the game-
     * detail screen) — it has already validated legality with the local
     * [org.mochios.go.engine.GoGame] engine and serialised the new board into
     * a FEN-like string. The repository only forwards the payload.
     */
    suspend fun move(gameId: String, request: MoveRequest): String =
        api.move(gameId, request).unwrap().id

    /**
     * Pass turn. When the second consecutive pass ends the game, the caller
     * sets [PassRequest.status] = `"finished"` along with the computed winner
     * and area scores so the server records the final state in one round trip.
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

// Convenience extension so screens can do `messages.toList()` without a
// `GetMessagesResponse.messages` access — kept here rather than in the model
// file so it stays grouped with the consumer.
fun GetMessagesResponse.toList(): List<GameMessage> = messages
