package org.mochios.go.api

import org.mochios.android.api.ApiResponse
import org.mochios.go.model.CreateGameRequest
import org.mochios.go.model.CreateGameResponse
import org.mochios.go.model.DeleteResponse
import org.mochios.go.model.DrawOfferResponse
import org.mochios.go.model.Game
import org.mochios.go.model.GameViewResponse
import org.mochios.go.model.GetMessagesResponse
import org.mochios.go.model.GetNewGameResponse
import org.mochios.go.model.MoveRequest
import org.mochios.go.model.MoveResponse
import org.mochios.go.model.PassRequest
import org.mochios.go.model.ResignResponse
import org.mochios.go.model.SendMessageRequest
import org.mochios.go.model.SendMessageResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit binding for every action declared in `apps/go/app.json`. Mirrors the
 * shape of [org.mochios.go.web `gamesApi`][apps/go/web/src/api/games.ts]
 * one-for-one.
 *
 * All endpoints follow the standard Mochi `{"data": ...}` envelope, so the
 * return types are `Response<ApiResponse<T>>` and the repository unwraps
 * one envelope layer to surface either the inner payload or a typed
 * `MochiError`.
 *
 * Move / pass / send / create use JSON request bodies via `@Body` to match
 * the web `client.post(payload)` callers — the Starlark side reads inputs
 * via `a.input(...)` which is content-type agnostic.
 */
interface GoApi {

    @GET("-/list")
    suspend fun listGames(): Response<ApiResponse<List<Game>>>

    @GET("-/new")
    suspend fun getNewGameFriends(): Response<ApiResponse<GetNewGameResponse>>

    @POST("-/create")
    suspend fun createGame(@Body body: CreateGameRequest): Response<ApiResponse<CreateGameResponse>>

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

    @POST("{gameId}/-/pass")
    suspend fun pass(
        @Path("gameId") gameId: String,
        @Body body: PassRequest,
    ): Response<ApiResponse<MoveResponse>>

    @POST("{gameId}/-/resign")
    suspend fun resign(@Path("gameId") gameId: String): Response<ApiResponse<ResignResponse>>

    @POST("{gameId}/-/draw-offer")
    suspend fun drawOffer(@Path("gameId") gameId: String): Response<ApiResponse<DrawOfferResponse>>

    @POST("{gameId}/-/draw-accept")
    suspend fun drawAccept(@Path("gameId") gameId: String): Response<ApiResponse<DrawOfferResponse>>

    @POST("{gameId}/-/draw-decline")
    suspend fun drawDecline(@Path("gameId") gameId: String): Response<ApiResponse<DrawOfferResponse>>

    @POST("{gameId}/-/delete")
    suspend fun delete(@Path("gameId") gameId: String): Response<ApiResponse<DeleteResponse>>
}
