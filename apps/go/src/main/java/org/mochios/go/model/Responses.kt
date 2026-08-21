// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

import com.google.gson.annotations.SerializedName

/**
 * Payloads of the Go app's HTTP actions, mirroring
 * `apps/go/web/src/api/types/games.ts`.
 */

data class GameViewResponse(
    val game: Game = Game(),
    val identity: String = "",
)


data class GetMessagesResponse(
    val messages: List<GameMessage> = emptyList(),
    val hasMore: Boolean? = null,
    // "<created>:<id>", not a bare timestamp — created alone is not unique and
    // paginating on it drops every row sharing the page boundary's second.
    val nextCursor: String? = null,
)

data class CreateGameResponse(
    val id: String = "",
    val black: String = "",
)

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
 * Pass body; when the second consecutive pass ends the game the caller also
 * sends `status="finished"`, the winner and both scores.
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
