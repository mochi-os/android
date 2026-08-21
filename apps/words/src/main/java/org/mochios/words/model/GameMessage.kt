// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

/**
 * A chat or log row. [type] is "message" (user chat), "move" (played words and
 * score in [body]) or "system" (resign, game over).
 */
data class GameMessage(
    val id: String = "",
    val game: String = "",
    val member: String = "",
    val name: String = "",
    val body: String = "",
    val type: String = "message",
    /**
     * Structured kind of a `system` row (currently "resign"), used to localise
     * it. Empty on chat, move and legacy rows, where the renderer falls back to
     * [body].
     */
    val event: String = "",
    val created: Long = 0,
)
