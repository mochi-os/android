// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

/**
 * A row in the in-game chat / move log, mirroring web's `GameMessage`. `type`
 * is `message` (chat), `move` (server move log; `body` is the SGF-style label)
 * or `system` (resign, game over).
 */
data class GameMessage(
    val id: String = "",
    val game: String = "",
    val member: String = "",
    val name: String = "",
    val body: String = "",
    val type: String = "message",
    /**
     * For `system` rows: `resign | draw_offer | draw_accept | draw_decline`,
     * used to localise the notice; empty rows fall back to [body].
     */
    val event: String = "",
    val created: Long = 0,
)
