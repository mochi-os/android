package org.mochios.words.model

/**
 * Response wrappers for the Words app's HTTP actions. Each one corresponds
 * to the inner payload of the `{"data": ...}` envelope unwrapped by
 * `Response<ApiResponse<T>>.unwrap()`. Mirrors `apps/words/web/src/api/types/games.ts`.
 */

data class GameViewResponse(
    val game: Game = Game(),
    val identity: String = "",
)

data class GetMessagesResponse(
    val messages: List<GameMessage> = emptyList(),
    val hasMore: Boolean? = null,
    val nextCursor: Long? = null,
)

data class CreateGameResponse(
    val id: String = "",
)

/**
 * Friend candidate returned by `-/new` for opening a new game. Mirrors the
 * `NewGameFriend` interface in the web types module. `class` is escaped
 * with backticks because it's a Kotlin reserved word; Gson maps the raw
 * `class` JSON key through the property name.
 */
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
 * Request body for `:game/-/move`. Mirrors `MoveRequest` in the web types.
 * `tiles_used` is the rack-tile string consumed (blanks as `_`); `words_formed`
 * is a comma-separated list of the words formed by the play (server runs
 * the validate-word check against each).
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
 * Request body for `:game/-/exchange`. `tiles` is a rack-tile string of the
 * tiles being returned to the bag — same alphabet as `tiles_used` (`_` for
 * blanks, uppercase letters otherwise).
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
