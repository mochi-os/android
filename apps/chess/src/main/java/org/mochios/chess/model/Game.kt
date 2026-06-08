package org.mochios.chess.model

import com.google.gson.annotations.SerializedName

/**
 * A chess game row. Mirrors `Game` in
 * `apps/chess/web/src/api/types/games.ts` and the `games` table in
 * `apps/chess/chess.star` (`action_list` selects every column on this record).
 *
 *  - [id] is the row UID (always present); [fingerprint] is the 9-char alias
 *    that fronts the per-entity URL — peers see and link to the fingerprint.
 *  - [identity] / [opponent] are the two players' entity IDs; the matching
 *    `_name` fields are denormalised display names captured at creation time
 *    so we can render the sidebar without an extra lookup per row.
 *  - [white] is the entity ID of whichever side plays white (resolved by the
 *    server when the game is created — random first-move colour assignment).
 *  - [status] is the same five-state enum the web uses:
 *    `"active" | "checkmate" | "stalemate" | "draw" | "resigned"`.
 *  - [winner] is the entity ID of the winning player, or `null` for an
 *    in-progress / drawn / stalemate game.
 *  - [drawOffer] holds the entity ID of the player who currently has an open
 *    draw offer to the opponent (or `null` when no offer is pending).
 *  - [fen] is the standard FEN string for the current position; [pgn] is the
 *    accumulated PGN move text. [key] is the WebSocket subscription key for
 *    the game.
 *  - [updated] / [created] are unix-seconds timestamps.
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
     * Resolve the opponent's display name from the caller's identity. Mirrors
     * `getOpponentName` in `apps/chess/web/src/api/games.ts`. When the caller
     * isn't either player (shouldn't happen — the server filters on identity
     * / opponent), returns [opponentName] as a sensible fallback.
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
     * For `type == "system"` rows, the structured event kind
     * (`"resign" | "draw_offer" | "draw_accept" | "draw_decline"`) used to
     * localise the notice per viewer. Empty for legacy rows / chat / move,
     * in which case the renderer falls back to [body].
     */
    val event: String = "",
    val created: Long = 0,
)

/** Cursor-paginated reply for `:game/-/messages`. */
data class GetMessagesResponse(
    val messages: List<GameMessage> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long? = null,
)

/**
 * Reply for `-/create` — the new game's row UID and the entity ID assigned to
 * play white. The client uses the returned [id] to navigate straight into the
 * new game.
 */
data class CreateGameResponse(
    val id: String = "",
    val white: String = "",
)

/**
 * A friend offered as a possible opponent on `-/new`. The shape matches what
 * `mochi.service.call("friends", "list", ...)` returns, so the same struct
 * also appears in the People app's friends call.
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
 * Sent body for `:game/-/move`. Mirrors `MoveRequest` in
 * `apps/chess/web/src/api/types/games.ts`. The server validates each field
 * independently — see `action_move` in `chess.star`.
 *
 *  - [from] / [to] are 2-character square names (`"e2"` / `"e4"`).
 *  - [promotion] is optional and one of `"q" | "r" | "b" | "n"` when the move
 *    is a pawn promotion; otherwise empty/null.
 *  - [fen] / [pgn] / [san] reflect the post-move position the client
 *    computed locally (via chesslib) and resends so the server can stash
 *    them without re-deriving them from a starting position + move list.
 *  - [status] / [winner] are the optional terminal-state hints the client
 *    sets when chesslib reports check-mate / stale-mate / drawn-by-rule. The
 *    server validates them against the allowed values and clamps the rest.
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
