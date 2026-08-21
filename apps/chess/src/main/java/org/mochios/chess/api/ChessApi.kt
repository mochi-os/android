// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.api

import org.mochios.android.api.ApiResponse
import org.mochios.chess.model.CreateGameResponse
import org.mochios.chess.model.DeleteResponse
import org.mochios.chess.model.DrawOfferResponse
import org.mochios.chess.model.Game
import org.mochios.chess.model.GameViewResponse
import org.mochios.chess.model.GetMessagesResponse
import org.mochios.chess.model.GetNewGameResponse
import org.mochios.chess.model.MoveResponse
import org.mochios.chess.model.ResignResponse
import org.mochios.chess.model.SendMessageResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Chess HTTP actions; paths mirror `apps/chess/app.json`, payloads arrive in
 * the `{"data": ...}` envelope and are read with `unwrap`.
 */
interface ChessApi {

    // ---- Class-level ----

    @GET("-/list")
    suspend fun listGames(): Response<ApiResponse<List<Game>>>

    @GET("-/new")
    suspend fun getNewGameFriends(): Response<ApiResponse<GetNewGameResponse>>

    @FormUrlEncoded
    @POST("-/create")
    suspend fun createGame(
        @Field("opponent") opponent: String,
    ): Response<ApiResponse<CreateGameResponse>>

    // ---- Entity-context: {game} is an entity id or fingerprint ----

    @GET("{game}/-/view")
    suspend fun viewGame(
        @Path("game") game: String,
    ): Response<ApiResponse<GameViewResponse>>

    @GET("{game}/-/messages")
    suspend fun getMessages(
        @Path("game") game: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<GetMessagesResponse>>

    @FormUrlEncoded
    @POST("{game}/-/send")
    suspend fun sendMessage(
        @Path("game") game: String,
        @Field("body") body: String,
    ): Response<ApiResponse<SendMessageResponse>>

    /**
     * JSON body rather than form fields, matching the web client's move POST.
     */
    @POST("{game}/-/move")
    suspend fun move(
        @Path("game") game: String,
        @Body request: org.mochios.chess.model.MoveRequest,
    ): Response<ApiResponse<MoveResponse>>

    @POST("{game}/-/resign")
    suspend fun resign(
        @Path("game") game: String,
    ): Response<ApiResponse<ResignResponse>>

    @POST("{game}/-/draw-offer")
    suspend fun drawOffer(
        @Path("game") game: String,
    ): Response<ApiResponse<DrawOfferResponse>>

    @POST("{game}/-/draw-accept")
    suspend fun drawAccept(
        @Path("game") game: String,
    ): Response<ApiResponse<DrawOfferResponse>>

    @POST("{game}/-/draw-decline")
    suspend fun drawDecline(
        @Path("game") game: String,
    ): Response<ApiResponse<DrawOfferResponse>>

    @POST("{game}/-/delete")
    suspend fun deleteGame(
        @Path("game") game: String,
    ): Response<ApiResponse<DeleteResponse>>
}
