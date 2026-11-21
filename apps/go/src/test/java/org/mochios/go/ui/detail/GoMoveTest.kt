// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.go.ui.detail

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.go.api.GoApi
import org.mochios.go.engine.GoGame
import org.mochios.go.repository.GoRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The board after a load and after a move the server never took: the
 * last-move marker comes from the record, and a failed move takes its stone
 * back even when the refetch behind it fails too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoMoveTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: GoRepository

    /** How many times each path has been asked for. */
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** The game the first `/view` answers; later ones fail when [offline]. */
    private var game = ""
    private var offline = false

    @Before
    fun begin() {
        // A failure's message is read through the application context.
        AppContext.set(RuntimeEnvironment.getApplication())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val count = counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                return when {
                    path == "/go/g1/-/view" && (count == 1 || !offline) ->
                        ok("""{"game": $game, "identity": "a1"}""")
                    path == "/go/g1/-/messages" -> ok("""{"messages": []}""")
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/go/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoApi::class.java)
        repository = GoRepository(api)
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun count(path: String): Int = counts[path]?.get() ?: 0

    private fun record(fen: String, sgf: String) =
        """{"id": "g1", "key": "k1", "identity": "a1", "opponent": "b1", "black": "a1",
            "board_size": 9, "status": "active", "fen": "$fen", "sgf": "$sgf"}"""

    /**
     * Runs the main looper the network's answers resume on until [done]. The
     * deadline is generous: a loaded machine slows the network's answers.
     */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets everything already under way finish. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    @Test
    fun `the marker sits on the opponent's reply when the game opens`() {
        // Black at (2,3), then White's reply at (4,4).
        game = record(
            "........./........./...B...../........./....W..../........./........./........./......... b 0 0 - 0",
            "B[2,3];W[4,4]",
        )
        val model = GoGameViewModel(SavedStateHandle(mapOf("gameId" to "g1")), repository)
        until { model.state.value.game != null }
        assertEquals(4 to 4, model.state.value.lastMove)
    }

    @Test
    fun `a move the server refused leaves the board as it was`() {
        val empty = "........./........./........./........./........./........./........./........./......... b 0 0 - 0"
        game = record(empty, "")
        offline = true
        val model = GoGameViewModel(SavedStateHandle(mapOf("gameId" to "g1")), repository)
        until { model.state.value.game != null && model.state.value.isMyTurn }

        model.place(0, 0, "failed")
        assertEquals("the stone shows at once", GoGame.BLACK, model.state.value.goGame?.getStone(0, 0))
        until { count("/go/g1/-/move") > 0 && !model.state.value.isMoving }
        // The refetch behind the failure fails as well, as it does offline.
        until { count("/go/g1/-/view") > 1 }
        settle()

        val state = model.state.value
        assertEquals(GoGame.EMPTY, state.goGame?.getStone(0, 0))
        assertEquals(empty, state.game?.fen)
        assertTrue("the turn is the player's again", state.isMyTurn)
        assertEquals(null, state.lastMove)
    }
}
