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
 * Retrofit binding for the actions declared in `apps/words/app.json`.
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
     * [language] is "en_US" or "en_UK". Words under 2 or over 15 characters are
     * always invalid, whatever the language.
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
        @Query("before") before: String? = null,
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
     * Raw bytes, no `{"data": ...}` envelope; the server proxies to the
     * player's owning peer. Coil can load the same URL directly.
     */
    @Streaming
    @GET("{gameId}/-/user/{user}/asset/{asset}")
    suspend fun getUserAsset(
        @Path("gameId") gameId: String,
        @Path("user") user: String,
        @Path("asset") asset: String,
    ): Response<ResponseBody>
}
