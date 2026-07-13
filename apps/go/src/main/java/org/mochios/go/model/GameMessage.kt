// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.model

/**
 * A row in the in-game chat / move log. Mirrors the `GameMessage` interface
 * in `apps/go/web/src/api/types/games.ts`.
 *
 *  - `type == "message"` — user-typed chat
 *  - `type == "move"`    — server-generated move log entry (`body` is the
 *                          short SGF-style move label)
 *  - `type == "system"`  — server-generated event (resign, game over)
 */
data class GameMessage(
    val id: String = "",
    val game: String = "",
    val member: String = "",
    val name: String = "",
    val body: String = "",
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
