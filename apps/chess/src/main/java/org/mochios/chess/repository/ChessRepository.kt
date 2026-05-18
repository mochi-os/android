package org.mochios.chess.repository

import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.chess.api.ChessApi
import org.mochios.chess.model.CreateGameResponse
import org.mochios.chess.model.Game
import org.mochios.chess.model.GameMessage
import org.mochios.chess.model.GameViewResponse
import org.mochios.chess.model.GetMessagesResponse
import org.mochios.chess.model.MoveRequest
import org.mochios.chess.model.NewGameFriend
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [ChessApi] that unwraps the standard `{"data": ...}`
 * envelope and converts thrown [retrofit2.HttpException] / IO errors into
 * [org.mochios.android.api.MochiError] subclasses via `toMochiError`. Method
 * names mirror the web hooks in `apps/chess/web/src/hooks/useGames.ts` so it
 * stays easy to flip back and forth between layers.
 *
 * No caching layer — the chess data set is small (a user has at most a few
 * dozen active games) and TanStack-style stale-while-revalidate happens in
 * each ViewModel by re-issuing the query on resume / push event.
 */
@Singleton
class ChessRepository @Inject constructor(
    private val api: ChessApi,
) {

    // ---- Class-level ----

    /** Every game the caller is a player in, ordered by `updated` desc server-side. */
    suspend fun listGames(): List<Game> =
        try { api.listGames().unwrap() } catch (e: Exception) { throw e.toMochiError() }

    /** Friends eligible to be picked as a new-game opponent. */
    suspend fun getNewGameFriends(): List<NewGameFriend> =
        try { api.getNewGameFriends().unwrap().friends } catch (e: Exception) { throw e.toMochiError() }

    /**
     * Start a new game with [opponent] (entity ID). Returns the new game's
     * row UID + the entity ID assigned to play white. The caller navigates
     * straight into the game using the returned id.
     */
    suspend fun createGame(opponent: String): CreateGameResponse =
        try { api.createGame(opponent).unwrap() } catch (e: Exception) { throw e.toMochiError() }

    // ---- Entity-context ----

    /**
     * Fetch the full state of a single game. The path segment accepts either
     * the row UID or the 9-char fingerprint — both resolve to the same row
     * server-side. Returns the game + the caller's identity so the UI can
     * derive turn-state, draw-offer direction etc. without an extra call.
     */
    suspend fun getGame(game: String): GameViewResponse =
        try { api.viewGame(game).unwrap() } catch (e: Exception) { throw e.toMochiError() }

    /**
     * Cursor-paginated message list for a game. Pass `before = null` for the
     * latest page; pass the previous response's `nextCursor` to walk
     * backwards through history. The server clamps `limit` to [1, 100] and
     * defaults to 30.
     */
    suspend fun getMessages(
        game: String,
        before: Long? = null,
        limit: Int? = null,
    ): GetMessagesResponse =
        try { api.getMessages(game, before, limit).unwrap() } catch (e: Exception) { throw e.toMochiError() }

    /** Append a chat message to the game. Returns the row UID of the new message. */
    suspend fun sendMessage(game: String, body: String): GameMessage {
        try {
            val response = api.sendMessage(game, body).unwrap()
            // Server returns just the id; the caller usually optimistically
            // pre-populated the row, so we hand back a minimal stub for any
            // callers that want one without doing a follow-up list call.
            return GameMessage(id = response.id, game = game, body = body, type = "message")
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    /**
     * Submit a fully-validated move. The client computes [MoveRequest.fen] /
     * `pgn` / `san` locally via chesslib and resends them so the server can
     * stash the post-move position without re-deriving anything. Returns the
     * UID of the move message row inserted server-side.
     */
    suspend fun move(game: String, request: MoveRequest): String =
        try { api.move(game, request).unwrap().id } catch (e: Exception) { throw e.toMochiError() }

    /** Resign the active game; the opponent becomes the winner. */
    suspend fun resign(game: String): Boolean =
        try { api.resign(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }

    /** Open a draw offer to the opponent. No-op if an offer is already standing. */
    suspend fun drawOffer(game: String): Boolean =
        try { api.drawOffer(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }

    /** Accept the opponent's standing draw offer. Game ends as a draw. */
    suspend fun drawAccept(game: String): Boolean =
        try { api.drawAccept(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }

    /** Decline the opponent's standing draw offer. Clears the `draw_offer` field. */
    suspend fun drawDecline(game: String): Boolean =
        try { api.drawDecline(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }

    /**
     * Delete the game locally. Only allowed on completed (non-`active`) games
     * — the server enforces the rule, so the UI should hide the menu item on
     * active games but the repo doesn't second-guess.
     */
    suspend fun deleteGame(game: String): Boolean =
        try { api.deleteGame(game).unwrap().success } catch (e: Exception) { throw e.toMochiError() }
}
