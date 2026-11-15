// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chess.ui.detail

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.chess.api.ChessApi
import org.mochios.chess.repository.ChessRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A chess game's screen reads the game and its messages at once, on opening
 * and on every board frame, rather than the messages after the game; the
 * game's failure is the one shown, however the two answers land.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameLoadTest {

    private lateinit var server: MockWebServer

    /** Released as each messages read arrives, which the game's answer waits for. */
    @Volatile private var messaged = CountDownLatch(1)

    /** How many game answers waited for a messages read and saw it arrive. */
    private val overlapped = AtomicInteger()

    /** Whether the game read is refused, as not found, after its wait. */
    @Volatile private var gone = false

    /** Whether the messages read is refused, at once. */
    @Volatile private var broken = false

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path.orEmpty().substringBefore("?")) {
                    "/chess/g1/-/view" -> {
                        if (messaged.await(2, TimeUnit.SECONDS)) overlapped.incrementAndGet()
                        if (gone) {
                            MockResponse().setResponseCode(404).setBody("""{"error": "not_found"}""")
                        } else {
                            ok("""{"game": {"id": "g1", "status": "active", "pgn": ""}, "identity": "me"}""")
                        }
                    }
                    "/chess/g1/-/messages" -> {
                        messaged.countDown()
                        if (broken) {
                            MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
                        } else {
                            ok("""{"messages": [{"id": "m1", "game": "g1", "body": "hello", "type": "message", "created": 5}], "more": false}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model(): ChessGameViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/chess/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChessApi::class.java)
        return ChessGameViewModel(SavedStateHandle(mapOf("gameId" to "g1")), ChessRepository(api))
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `the game and its messages are read at once, on opening and on a frame`() {
        val model = model()
        until { model.uiState.value.game != null && !model.uiState.value.isLoading }
        assertEquals("THE POINT: the messages were asked for while the game's answer was held", 1, overlapped.get())
        assertEquals(listOf("m1"), model.uiState.value.messages.map { it.id })
        messaged = CountDownLatch(1)
        model.refresh()
        until { overlapped.get() == 2 && !model.uiState.value.isRefreshing }
    }

    @Test
    fun `a refused game shows its error, though the messages answered first`() {
        gone = true
        val model = model()
        until { model.uiState.value.error != null }
        assertTrue(model.uiState.value.error is MochiError.NotFoundError)
        assertNull(model.uiState.value.game)
        assertTrue(model.uiState.value.messages.isEmpty())
    }

    @Test
    fun `a refused game's error is shown over the messages' own, which came first`() {
        gone = true
        broken = true
        val model = model()
        until { model.uiState.value.error != null && !model.uiState.value.isLoading }
        assertTrue(
            "THE POINT: the messages' refusal, answered first, took the game's place",
            model.uiState.value.error is MochiError.NotFoundError,
        )
    }
}
