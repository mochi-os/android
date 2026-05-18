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
 * Retrofit interface for the chess app's HTTP actions. Endpoint paths mirror
 * `apps/chess/app.json` exactly:
 *
 *  - Class-level actions (`-/list`, `-/new`, `-/create`) have no game id.
 *  - Per-game actions take a `{game}` path segment (entity id or 9-char
 *    fingerprint) followed by `-/<action>`.
 *
 * Every payload is wrapped in the standard `{"data": ...}` envelope by the
 * Starlark side (`return {"data": ...}`), so each method returns
 * `Response<ApiResponse<T>>` where `T` is the inner payload shape. Callers
 * unwrap via `org.mochios.android.api.unwrap` (lib helper).
 *
 *  - `list` returns the array directly under `data` (the Starlark
 *    `action_list` does `{"data": games}`), so the inner type is
 *    `List<Game>` rather than a wrapper object.
 *  - `getNewGameFriends` returns `{"data": {"friends": [...]}}`, so the inner
 *    type is [GetNewGameResponse] with a nested `friends` field.
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
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<GetMessagesResponse>>

    @FormUrlEncoded
    @POST("{game}/-/send")
    suspend fun sendMessage(
        @Path("game") game: String,
        @Field("body") body: String,
    ): Response<ApiResponse<SendMessageResponse>>

    /**
     * Submit a move. The body is JSON (the server reads each field via
     * `a.input(...)` which accepts both form fields and JSON payload keys,
     * but JSON keeps the wire shape closest to the web's client which
     * always posts JSON for moves).
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
