// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.repository

import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.chess.api.ChessApi
import org.mochios.chess.model.CreateGameResponse
import org.mochios.chess.model.Game
import org.mochios.chess.model.GameMessage
import org.mochios.chess.model.GameViewResponse
import org.mochios.chess.model.GetMessagesResponse
import org.mochios.chess.model.MoveRequest
import org.mochios.chess.model.NewGameFriend
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unwraps the `{"data": ...}` envelope and converts failures to
 * [org.mochios.android.api.MochiError]; no cache.
 */
@Singleton
class ChessRepository @Inject constructor(
    private val api: ChessApi,
) {

    // ---- Class-level ----

    /** Every game the caller is a player in, ordered by `updated` desc server-side. */
    suspend fun listGames(): List<Game> =
        try { api.listGames().unwrap() } catch (e: Exception) { throw e.toMochiError() }

    /** Friends eligible to be picked as a new-game opponent. */
    suspend fun getNewGameFriends(): List<NewGameFriend> =
        try { api.getNewGameFriends().unwrap().friends } catch (e: Exception) { throw e.toMochiError() }

    /**
     * Start a game against [opponent] (entity id); returns the new game's id
     * and who plays white.
     */
    suspend fun createGame(opponent: String): CreateGameResponse =
        try { api.createGame(opponent).unwrap() } catch (e: Exception) { throw e.toMochiError() }

    // ---- Entity-context ----

    /**
     * Full state of one game, by id or fingerprint, plus the caller's identity.
     */
    suspend fun getGame(game: String): GameViewResponse =
        try { api.viewGame(game).unwrap() } catch (e: Exception) { throw e.toMochiError() }

    /**
     * Messages page ending before [before] (null for the newest); the server
     * clamps [limit] to 1-100, default 30.
     */
    suspend fun getMessages(
        game: String,
        before: String? = null,
        limit: Int? = null,
    ): GetMessagesResponse =
        try { api.getMessages(game, before, limit).unwrap() } catch (e: Exception) { throw e.toMochiError() }

    /** Append a chat message to the game. Returns the row UID of the new message. */
    suspend fun sendMessage(game: String, body: String): GameMessage {
        try {
            val response = api.sendMessage(game, body).unwrap()
            // Server returns just the id; the caller usually optimistically
            // pre-populated the row, so we hand back a minimal stub for any
            // callers that want one without doing a follow-up list call.
            return GameMessage(id = response.id, game = game, body = body, type = "message")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Submit a move; returns the id of the move message row.
     */
    suspend fun move(game: String, request: MoveRequest): String =
        try { api.move(game, request).unwrap().id } catch (e: Exception) { throw e.toMochiError() }

    /** Resign the active game; the opponent becomes the winner. */
    suspend fun resign(game: String): Boolean =
        try { api.resign(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }

    /** Open a draw offer to the opponent. No-op if an offer is already standing. */
    suspend fun drawOffer(game: String): Boolean =
        try { api.drawOffer(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }

    /** Accept the opponent's standing draw offer. Game ends as a draw. */
    suspend fun drawAccept(game: String): Boolean =
        try { api.drawAccept(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }

    /** Decline the opponent's standing draw offer. Clears the `draw_offer` field. */
    suspend fun drawDecline(game: String): Boolean =
        try { api.drawDecline(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }

    /**
     * Delete the game locally; the server rejects this on active games.
     */
    suspend fun deleteGame(game: String): Boolean =
        try { api.deleteGame(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }
}
