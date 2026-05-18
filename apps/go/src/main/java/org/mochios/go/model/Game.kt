package org.mochios.go.model

import com.google.gson.annotations.SerializedName

/**
 * A Go game record. Mirrors the `Game` type in
 * `apps/go/web/src/api/types/games.ts`. Snake-case wire keys are mapped to
 * camel-case Kotlin via [SerializedName]; fields the server stores in
 * snake_case shape (`board_size`, `identity_name`, …) stay snake-cased in
 * Kotlin too because that's how the rest of the codebase refers to them.
 *
 * `status` is one of `"active"`, `"finished"`, `"draw"`, `"resigned"`.
 * `winner` is the identity id of the winning player, or `null` while the
 * game is still active (or for a draw).
 */
data class Game(
    val id: String = "",
    val fingerprint: String? = null,
    val identity: String = "",
    @SerializedName("identity_name")
    val identityName: String = "",
    val opponent: String = "",
    @SerializedName("opponent_name")
    val opponentName: String = "",
    val black: String = "",
    @SerializedName("board_size")
    val boardSize: Int = 19,
    val komi: Double = 6.5,
    val status: String = "active",
    val winner: String? = null,
    @SerializedName("draw_offer")
    val drawOffer: String? = null,
    val fen: String = "",
    @SerializedName("previous_fen")
    val previousFen: String? = null,
    val sgf: String = "",
    @SerializedName("captures_black")
    val capturesBlack: Int = 0,
    @SerializedName("captures_white")
    val capturesWhite: Int = 0,
    val key: String = "",
    val updated: Long = 0,
    val created: Long = 0,
) {
    val isFinished: Boolean
        get() = status == "finished" || status == "resigned" || status == "draw"

    /** Returns the opponent's display name relative to [myIdentity]. */
    fun opponentName(myIdentity: String): String =
        if (identity == myIdentity) opponentName else identityName
}
