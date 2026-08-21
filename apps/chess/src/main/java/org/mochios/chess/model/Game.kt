// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.model

import com.google.gson.annotations.SerializedName

/**
 * A game row, mirroring `Game` in `apps/chess/web/src/api/types/games.ts`.
 * [status] is one of `active | checkmate | stalemate | draw | resigned`;
 * [drawOffer] is the offering player's entity id; [key] is the WebSocket
 * subscription key; timestamps are unix seconds.
 */
data class Game(
    val id: String = "",
    val fingerprint: String? = null,
    val identity: String = "",
    @SerializedName("identity_name") val identityName: String = "",
    val opponent: String = "",
    @SerializedName("opponent_name") val opponentName: String = "",
    val white: String = "",
    val status: String = "active",
    val winner: String? = null,
    @SerializedName("draw_offer") val drawOffer: String? = null,
    val fen: String = "",
    val pgn: String = "",
    val key: String = "",
    val updated: Long = 0,
    val created: Long = 0,
) {
    /**
     * Display name of the opponent, given the caller's identity.
     */
    fun opponentName(myIdentity: String): String =
        if (identity == myIdentity) opponentName else identityName

    /** Entity ID of the opponent, given the caller's identity. */
    fun opponentId(myIdentity: String): String =
        if (identity == myIdentity) opponent else identity
}

/** Server-facing reply for `:game/-/view` (single-game detail). */
data class GameViewResponse(
    val game: Game = Game(),
    val identity: String = "",
)

/** A single chat / move / system message attached to a game. */
data class GameMessage(
    val id: String = "",
    val game: String = "",
    val member: String = "",
    val name: String = "",
    val body: String = "",
    /** One of `"message" | "move" | "system"`. */
    val type: String = "message",
    /**
     * For `system` rows: `resign | draw_offer | draw_accept | draw_decline`,
     * used to localise the notice; empty rows fall back to [body].
     */
    val event: String = "",
    val created: Long = 0,
)

/** Cursor-paginated reply for `:game/-/messages`. */
data class GetMessagesResponse(
    val messages: List<GameMessage> = emptyList(),
    val hasMore: Boolean = false,
    // "<created>:<id>", not a bare timestamp — created alone is not unique and
    // paginating on it drops every row sharing the page boundary's second.
    val nextCursor: String? = null,
)

/**
 * Reply for `-/create`: the new game's id and the entity playing white.
 */
data class CreateGameResponse(
    val id: String = "",
    val white: String = "",
)

/**
 * A candidate opponent from `-/new`; same shape as the friends service's
 * `list`.
 */
data class NewGameFriend(
    @SerializedName("class") val klass: String = "",
    val id: String = "",
    val identity: String = "",
    val name: String = "",
)

/** Reply for `-/new` — the list of eligible opponents for the picker. */
data class GetNewGameResponse(
    val friends: List<NewGameFriend> = emptyList(),
)

/**
 * Body for `:game/-/move`, mirroring the web's `MoveRequest`. [fen] / [pgn] /
 * [san] are the client-computed post-move position the server stores as-is;
 * [promotion] is `q | r | b | n`; [status] / [winner] are optional
 * terminal-state hints the server validates.
 */
data class MoveRequest(
    val from: String,
    val to: String,
    val promotion: String? = null,
    val fen: String,
    val pgn: String,
    val san: String,
    val status: String? = null,
    val winner: String? = null,
)

/** Reply for `:game/-/move` — UID of the move message row. */
data class MoveResponse(val id: String = "")

/** Reply for `:game/-/resign` (and the matching `draw-*` actions). */
data class SuccessResponse(val success: Boolean = false)

/** Convenience aliases so call-sites name the response after the action. */
typealias ResignResponse = SuccessResponse
typealias DeleteResponse = SuccessResponse
typealias DrawOfferResponse = SuccessResponse

/** Reply for `:game/-/send` — UID of the new chat message row. */
data class SendMessageResponse(val id: String = "")
