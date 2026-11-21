// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.words.ui.detail

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.mochios.android.websocket.REOPEN
import org.mochios.android.websocket.StreamWsEvent
import org.mochios.words.api.WordsApi
import org.mochios.words.repository.WordsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A words game's chat keeps the older pages read when it is read again, says
 * why a first page failed, and keeps a message that failed to send while
 * saying why; a game the server no longer has answers not found.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordsChatTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: WordsRepository
    private val context = RuntimeEnvironment.getApplication()
    private val newest = AtomicInteger()
    private val refused = AtomicInteger()

    @Volatile private var broken = false
    @Volatile private var gone = false

    private fun rows(range: IntRange) = range.joinToString(",") { at ->
        """{"id": "m$at", "game": "g1", "member": "a2", "name": "Bo", "body": "line $at", "type": "message", "created": ${1_753_900_000 + at}}"""
    }

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val before = request.requestUrl?.queryParameter("before")
                return when (request.path.orEmpty().substringBefore("?")) {
                    "/words/g1/-/view" -> if (gone) {
                        MockResponse().setResponseCode(404).setBody("""{"error": "not_found"}""")
                    } else {
                        ok("""{"game": {"id": "g1", "key": "k1", "status": "active"}, "identity": "a1"}""")
                    }
                    "/words/g1/-/messages" -> when {
                        broken -> {
                            refused.incrementAndGet()
                            MockResponse().setResponseCode(500).setBody("""{"error": "Chat is down"}""")
                        }
                        before == "c5" -> ok("""{"messages": [${rows(0..4)}], "more": false}""")
                        else -> {
                            newest.incrementAndGet()
                            ok("""{"messages": [${rows(5..9)}], "more": true, "cursor": "c5"}""")
                        }
                    }
                    "/words/g1/-/send" -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        repository = WordsRepository(
            Retrofit.Builder()
                .baseUrl(server.url("/words/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WordsApi::class.java),
        )
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model() = WordsGameViewModel(repository, SavedStateHandle(mapOf("gameId" to "g1")))

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a reopened stream keeps the older pages read`() {
        val model = model()
        until { model.chat.state.value.messages.size == 5 }
        model.loadMoreMessages()
        until { model.chat.state.value.messages.size == 10 }
        val reads = newest.get()
        model.onWebsocketEvent(REOPEN, null)
        until { newest.get() > reads && !model.chat.state.value.isLoading }
        assertEquals("THE POINT: the older page survives", 10, model.chat.state.value.messages.size)
        assertFalse(model.chat.state.value.hasMore)
    }

    @Test
    fun `a first page that fails says why`() {
        broken = true
        val model = model()
        until { refused.get() > 0 && !model.chat.state.value.isLoading }
        val error = model.chat.state.value.error
        assertTrue("THE POINT: the empty chat says why", error is MochiError.ServerError)
        assertEquals("Chat is down", (error as MochiError.ServerError).message)
    }

    @Test
    fun `a message that fails to send is kept, and the toast says why`() {
        val model = model()
        until { model.uiState.value.game != null }
        var sent: Boolean? = null
        model.sendChatMessage("good game") { result -> sent = result }
        until { sent != null }
        assertEquals(false, sent)
        assertEquals(
            "THE POINT: the failure is said",
            context.getString(R.string.error_network),
            model.uiState.value.transientToast,
        )
    }

    @Test
    fun `a game the server no longer has is not found`() {
        gone = true
        val model = model()
        until { model.uiState.value.error != null }
        assertTrue(model.uiState.value.error is MochiError.NotFoundError)
    }

    /** A chat frame from Bo, at one second, with [id] or, from an older server, none. */
    private fun frame(id: String?) = StreamWsEvent(
        type = "message",
        created = 1_753_900_100,
        member = "a2",
        name = "Bo",
        body = "gg",
        event = null,
        raw = if (id == null) mapOf() else mapOf("id" to id),
    )

    @Test
    fun `two messages alike from one player in one second both show`() {
        val model = model()
        until { model.chat.state.value.messages.size == 5 }
        for (id in listOf("f1", "f2")) model.onWebsocketEvent("message", frameMessage(frame(id), "g1"))
        val frames = model.chat.state.value.messages.filter { it.created == 1_753_900_100L }
        assertEquals("THE POINT: neither folds into the other", listOf("f1", "f2"), frames.map { it.id })
    }

    @Test
    fun `an older server's frames get ids of their own, not the time and sender`() {
        val model = model()
        until { model.chat.state.value.messages.size == 5 }
        repeat(2) { model.onWebsocketEvent("message", frameMessage(frame(null), "g1")) }
        val frames = model.chat.state.value.messages.filter { it.created == 1_753_900_100L }
        assertEquals("THE POINT: two rows with two keys", 2, frames.map { it.id }.distinct().size)
    }
}
