// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

import com.google.gson.annotations.SerializedName

/**
 * A Go game record, mirroring `Game` in `apps/go/web/src/api/types/games.ts`.
 * `status` is `active`, `scoring`, `finished`, `draw` or `resigned`; `winner`
 * is the winning identity id, null while active or on a draw.
 */
data class Game(
    val id: String = "",
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
    // The scoring step after two passes. `scoring` holds the identity that has
    // accepted the proposal below; the game ends when the other one does too.
    val scoring: String? = null,
    @SerializedName("score_black")
    val scoreBlack: Double? = null,
    @SerializedName("score_white")
    val scoreWhite: Double? = null,
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

    /** Returns the opponent's display name relative to [myIdentity]. */
    fun opponentName(myIdentity: String): String =
        if (identity == myIdentity) opponentName else identityName

    /** Returns the opponent's entity id relative to [myIdentity]. */
    fun opponentId(myIdentity: String): String =
        if (identity == myIdentity) opponent else identity

    /**
     * Whether "Offer draw" applies: the game is live and no offer, ours or the
     * opponent's, is pending. The server refuses an offer placed over the
     * opponent's, and the accept/decline banner already answers that one.
     */
    val canOfferDraw: Boolean
        get() = status == "active" && drawOffer.isNullOrEmpty()
}
