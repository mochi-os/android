// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

import com.google.gson.annotations.SerializedName

/**
 * Response wrappers for the Go app's HTTP actions. Each one corresponds to
 * the inner payload of the `{"data": ...}` envelope unwrapped by
 * `Response<ApiResponse<T>>.unwrap()`. Mirrors `apps/go/web/src/api/types/games.ts`.
 */

data class GameViewResponse(
    val game: Game = Game(),
    val identity: String = "",
)

/**
 * The list endpoint returns the games array as the `data` value directly
 * (i.e. `{"data": [game, game, ...]}`), so the wrapper here mirrors that
 * shape — the `games` field comes from the repository wrapping the unwrapped
 * list, not from a server-side key.
 */
data class GetGamesResponse(
    val games: List<Game> = emptyList(),
)

data class GetMessagesResponse(
    val messages: List<GameMessage> = emptyList(),
    val hasMore: Boolean? = null,
    val nextCursor: Long? = null,
)

data class CreateGameResponse(
    val id: String = "",
    val black: String = "",
)

/**
 * Friend candidate returned by `-/new` for opening a new game. Mirrors the
 * `NewGameFriend` interface in the web types module. Note the field is
 * called `class` server-side; Kotlin requires the backtick escape.
 */
data class NewGameFriend(
    val `class`: String = "person",
    val id: String = "",
    val identity: String = "",
    val name: String = "",
)

data class GetNewGameResponse(
    val friends: List<NewGameFriend> = emptyList(),
)

data class SendMessageResponse(
    val id: String = "",
)

data class MoveResponse(
    val id: String = "",
)

data class ResignResponse(
    val success: Boolean = true,
)

data class DeleteResponse(
    val success: Boolean = true,
)

data class DrawOfferResponse(
    val success: Boolean = true,
)

/**
 * Request body for the move endpoint. Mirrors `MoveRequest` in the web types.
 * Snake-case wire fields are forwarded via [SerializedName] so the same
 * `Game` instance can be sent back to the server without translation.
 */
data class MoveRequest(
    val fen: String,
    @SerializedName("previous_fen")
    val previousFen: String? = null,
    val sgf: String = "",
    @SerializedName("captures_black")
    val capturesBlack: Int = 0,
    @SerializedName("captures_white")
    val capturesWhite: Int = 0,
    @SerializedName("move_label")
    val moveLabel: String = "",
    val status: String? = null,
    val winner: String? = null,
)

/**
 * Request body for the pass endpoint. When the second consecutive pass ends
 * the game the caller fills in `status="finished"`, the computed winner, and
 * both scores so the server can record the final state in a single round trip.
 */
data class PassRequest(
    val fen: String,
    val sgf: String = "",
    val status: String? = null,
    val winner: String? = null,
    @SerializedName("score_black")
    val scoreBlack: Double? = null,
    @SerializedName("score_white")
    val scoreWhite: Double? = null,
)

data class SendMessageRequest(
    val body: String,
)

data class CreateGameRequest(
    val opponent: String,
    @SerializedName("board_size")
    val boardSize: Int = 19,
    val komi: Double = 6.5,
)
