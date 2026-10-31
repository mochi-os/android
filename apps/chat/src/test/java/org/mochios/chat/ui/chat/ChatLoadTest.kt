// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.chat.api.ChatApi
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.repository.ChatRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A chat's messages are asked for beside the chat itself, and the chat still
 * decides the page's error, whatever the messages said.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatLoadTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var api: ChatApi

    /** Counted down as the messages are asked for. */
    private val asked = CountDownLatch(1)

    /** Whether the messages had been asked for while the chat was unanswered. */
    @Volatile private var overlapped = false

    /** The status the chat answers with, after the messages are asked for. */
    @Volatile private var refusal: Int? = null

    /** The status the messages answer with; null answers them. */
    @Volatile private var failure: Int? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when (path) {
                    "/chat/c1/-/view" -> {
                        overlapped = asked.await(2, TimeUnit.SECONDS)
                        val status = refusal
                        if (status != null) {
                            Thread.sleep(300)
                            MockResponse().setResponseCode(status).setBody("""{"error": "refused"}""")
                        } else {
                            ok("""{"chat": {"id": "c1", "key": "k1", "name": "Chat"}, "identity": "a1"}""")
                        }
                    }
                    "/chat/c1/-/messages" -> {
                        asked.countDown()
                        val status = failure
                        if (status != null) MockResponse().setResponseCode(status).setBody("""{"error": "down"}""")
                        else ok("""{"messages": [{"id": "m1", "chat": "c1", "body": "Hello"}], "more": false}""")
                    }
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                        // Answer the client's close, or the server waits on it.
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        api = Retrofit.Builder()
            .baseUrl(server.url("/chat/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
    }

    @After
    fun end() {
        asked.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /** The chat's screen as it opens. */
    private fun open() = ChatViewModel(
        SavedStateHandle(mapOf("chatId" to "c1")),
        ChatRepository(api, FileStore(context), context),
        socket,
        session,
        PinnedChatsStore(context),
        context,
    )

    /** Runs the main looper the network's answers resume on until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `the messages are asked for beside the chat`() {
        val model = open()
        until { model.uiState.value.messages.isNotEmpty() && !model.uiState.value.isLoading }
        assertTrue(overlapped)
        assertEquals("Chat", model.uiState.value.chat.name)
    }

    @Test
    fun `a refusal of the chat is the page error, and the messages asked beside it are not shown`() {
        refusal = 404
        val model = open()
        until { !model.uiState.value.isLoading && (model.uiState.value.error != null || model.uiState.value.notice != null) }
        assertTrue(model.uiState.value.error is MochiError.NotFoundError)
        assertNull(model.uiState.value.notice)
        assertEquals(emptyList<Any>(), model.uiState.value.messages)
    }

    @Test
    fun `a refusal of the chat still wins when the messages fail first`() {
        refusal = 403
        failure = 500
        val model = open()
        until { !model.uiState.value.isLoading && model.uiState.value.error != null }
        assertTrue(model.uiState.value.error is MochiError.ForbiddenError)
    }
}
