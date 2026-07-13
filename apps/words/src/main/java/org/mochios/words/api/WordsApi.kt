// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.api

import okhttp3.ResponseBody
import org.mochios.android.api.ApiResponse
import org.mochios.words.model.CreateGameRequest
import org.mochios.words.model.CreateGameResponse
import org.mochios.words.model.DeleteResponse
import org.mochios.words.model.ExchangeRequest
import org.mochios.words.model.ExchangeResponse
import org.mochios.words.model.Game
import org.mochios.words.model.GameListItem
import org.mochios.words.model.GameViewResponse
import org.mochios.words.model.GetMessagesResponse
import org.mochios.words.model.GetNewGameResponse
import org.mochios.words.model.MoveRequest
import org.mochios.words.model.MoveResponse
import org.mochios.words.model.ResignResponse
import org.mochios.words.model.SendMessageRequest
import org.mochios.words.model.SendMessageResponse
import org.mochios.words.model.ValidateWordResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit binding for every action declared in `apps/words/app.json`.
 * Mirrors the web `gamesApi` shape one-for-one
 * (`apps/words/web/src/api/games.ts`).
 *
 * All endpoints follow the standard Mochi `{"data": ...}` envelope, so
 * the return types are `Response<ApiResponse<T>>` and the repository
 * unwraps one envelope layer to surface either the inner payload or a
 * typed `MochiError`.
 *
 * Move / exchange / send / create use JSON request bodies via `@Body`
 * to match the web `client.post(payload)` callers — the Starlark side
 * reads inputs via `a.input(...)` which is content-type agnostic.
 *
 * The class-context list endpoint returns the games array as the `data`
 * value directly (`{"data": [game, ...]}`), so `listGames` unwraps to a
 * `List<GameListItem>` rather than a wrapper struct.
 */
interface WordsApi {

    // ---- Class-level actions ----

    @GET("-/list")
    suspend fun listGames(): Response<ApiResponse<List<GameListItem>>>

    @GET("-/new")
    suspend fun getNewGameFriends(): Response<ApiResponse<GetNewGameResponse>>

    @POST("-/create")
    suspend fun createGame(@Body body: CreateGameRequest): Response<ApiResponse<CreateGameResponse>>

    /**
     * Dictionary lookup used by the move composer to flag invalid words
     * before submission. `language` is `"en_US"` or `"en_UK"`; the server
     * returns `{"valid": true/false}` regardless of language (short words
     * <2 / >15 chars always return false without touching the dictionary).
     */
    @GET("-/validate")
    suspend fun validateWord(
        @Query("word") word: String,
        @Query("language") language: String,
    ): Response<ApiResponse<ValidateWordResponse>>

    // ---- Entity-level actions (game-scoped) ----

    @GET("{gameId}/-/view")
    suspend fun viewGame(@Path("gameId") gameId: String): Response<ApiResponse<GameViewResponse>>

    @GET("{gameId}/-/messages")
    suspend fun getMessages(
        @Path("gameId") gameId: String,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<GetMessagesResponse>>

    @POST("{gameId}/-/send")
    suspend fun sendMessage(
        @Path("gameId") gameId: String,
        @Body body: SendMessageRequest,
    ): Response<ApiResponse<SendMessageResponse>>

    @POST("{gameId}/-/move")
    suspend fun move(
        @Path("gameId") gameId: String,
        @Body body: MoveRequest,
    ): Response<ApiResponse<MoveResponse>>

    /** No body — the server uses the caller's identity + current turn state. */
    @POST("{gameId}/-/pass")
    suspend fun pass(@Path("gameId") gameId: String): Response<ApiResponse<MoveResponse>>

    @POST("{gameId}/-/exchange")
    suspend fun exchange(
        @Path("gameId") gameId: String,
        @Body body: ExchangeRequest,
    ): Response<ApiResponse<ExchangeResponse>>

    @POST("{gameId}/-/resign")
    suspend fun resign(@Path("gameId") gameId: String): Response<ApiResponse<ResignResponse>>

    @POST("{gameId}/-/delete")
    suspend fun delete(@Path("gameId") gameId: String): Response<ApiResponse<DeleteResponse>>

    /**
     * Stream a player avatar / banner / favicon for the chat panel.
     * The server proxies the asset through to the person's owning peer
     * via `mochi.remote.stream`, so this is location-transparent — the
     * caller doesn't need to know where the player's identity lives.
     *
     * Exposed as `Response<ResponseBody>` (no envelope) because the
     * action streams raw bytes with a `Content-Type` header rather than
     * a JSON payload. Coil can also hit this URL directly via `model = "$serverUrl/words/$gameId/-/user/$user/asset/$asset"`
     * with the standard Bearer interceptor — this helper is here for
     * one-off byte fetches that don't go through Coil.
     */
    @Streaming
    @GET("{gameId}/-/user/{user}/asset/{asset}")
    suspend fun getUserAsset(
        @Path("gameId") gameId: String,
        @Path("user") user: String,
        @Path("asset") asset: String,
    ): Response<ResponseBody>
}
