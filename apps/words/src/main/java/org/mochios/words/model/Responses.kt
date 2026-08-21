// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.model

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

data class SendMessageRequest(
    val body: String = "",
)

data class SendMessageResponse(
    val id: String = "",
)

/**
 * Request body for `:game/-/move`. `tiles_used` is the rack string spent (`_`
 * for blanks); `words_formed` is comma-separated.
 */
data class MoveRequest(
    val board: String = "",
    val score: Int = 0,
    val tiles_used: String = "",
    val words_formed: String = "",
)

data class MoveResponse(
    val id: String = "",
)

/**
 * Request body for `:game/-/exchange`. `tiles` is the rack string returned to
 * the bag (`_` for blanks).
 */
data class ExchangeRequest(
    val tiles: String = "",
)

data class ExchangeResponse(
    val id: String = "",
)

data class ResignResponse(
    val success: Boolean = true,
)

data class DeleteResponse(
    val success: Boolean = true,
)

data class ValidateWordResponse(
    val valid: Boolean = false,
)
