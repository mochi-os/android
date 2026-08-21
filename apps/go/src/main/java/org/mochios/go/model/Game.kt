// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

import com.google.gson.annotations.SerializedName

/**
 * A Go game record, mirroring `Game` in `apps/go/web/src/api/types/games.ts`.
 * `status` is `active`, `finished`, `draw` or `resigned`; `winner` is the
 * winning identity id, null while active or on a draw.
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
}
