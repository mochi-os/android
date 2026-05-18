package org.mochios.words.repository

import org.mochios.android.api.unwrap
import org.mochios.words.api.WordsApi
import org.mochios.words.model.CreateGameRequest
import org.mochios.words.model.ExchangeRequest
import org.mochios.words.model.GameListItem
import org.mochios.words.model.GameViewResponse
import org.mochios.words.model.GetMessagesResponse
import org.mochios.words.model.MoveRequest
import org.mochios.words.model.NewGameFriend
import org.mochios.words.model.SendMessageRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin convenience wrapper around [WordsApi]. Each method calls `.unwrap()`
 * on the `Response<ApiResponse<T>>` to surface either the inner payload
 * (deserialised T) or a typed `MochiError` for the ViewModels to handle.
 *
 * The shape mirrors `apps/words/web/src/api/games.ts`. No caching layer —
 * the calling ViewModels lean on stale-while-revalidate via the websocket
 * push + an on-resume refetch rather than pre-warming here.
 */
@Singleton
class WordsRepository @Inject constructor(
    private val api: WordsApi,
) {

    // ---- Listing + lifecycle ----

    suspend fun listGames(): List<GameListItem> =
        api.listGames().unwrap()

    suspend fun getNewGameFriends(): List<NewGameFriend> =
        api.getNewGameFriends().unwrap().friends

    /**
     * Open a new game. [opponents] must be 1-3 friends' entity IDs.
     * [language] is `"en_US"` or `"en_UK"` (server rejects anything else
     * with a 400). Sends the request as JSON with `opponents` as a
     * comma-joined string (the server splits on `,` in `action_create`).
     */
    suspend fun createGame(opponents: List<String>, language: String): String =
        api.createGame(
            CreateGameRequest(
                opponents = opponents.joinToString(","),
                language = language,
            )
        ).unwrap().id

    // ---- Per-game read ----

    suspend fun getGame(gameId: String): GameViewResponse =
        api.viewGame(gameId).unwrap()

    suspend fun getMessages(
        gameId: String,
        before: Long? = null,
        limit: Int? = null,
    ): GetMessagesResponse =
        api.getMessages(gameId, before, limit).unwrap()

    // ---- Per-game write ----

    suspend fun sendMessage(gameId: String, body: String): String =
        api.sendMessage(gameId, SendMessageRequest(body)).unwrap().id

    suspend fun move(gameId: String, request: MoveRequest): String =
        api.move(gameId, request).unwrap().id

    suspend fun pass(gameId: String): String =
        api.pass(gameId).unwrap().id

    suspend fun exchange(gameId: String, tiles: String): String =
        api.exchange(gameId, ExchangeRequest(tiles)).unwrap().id

    suspend fun resign(gameId: String): Boolean =
        api.resign(gameId).unwrap().success

    suspend fun deleteGame(gameId: String): Boolean =
        api.delete(gameId).unwrap().success

    // ---- Dictionary lookup ----

    /**
     * Hit the server's dictionary for a single word. Used by the move
     * composer's debounced check; returns false for words shorter than
     * 2 or longer than 15 chars without touching the DB.
     */
    suspend fun validateWord(word: String, language: String): Boolean =
        api.validateWord(word, language).unwrap().valid
}
