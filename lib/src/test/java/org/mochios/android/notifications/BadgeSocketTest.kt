// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.notifications

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.FakeKeystore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The badge's socket gets a fresh notifications token when core refuses the
 * one it has, rather than reconnecting with the refused one and hearing
 * nothing again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BadgeSocketTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var store: NotificationsUnreadStore

    /** The credential each handshake carried. */
    private val handshakes = ConcurrentLinkedQueue<String>()

    /** Whether the session's notifications token has been given up. */
    @Volatile private var invalidated = false

    @Before
    fun start() {
        FakeKeystore.install()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path.orEmpty().substringBefore("?")) {
                    "/notifications/-/count" -> MockResponse().setBody("""{"data": {"count": 0}}""")
                    "/_/websocket" -> {
                        val credential = request.getHeader("Authorization").orEmpty()
                        handshakes.add(credential)
                        if (credential != "Bearer fresh") {
                            MockResponse().setResponseCode(401)
                        } else {
                            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                                override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                                    webSocket.close(code, reason)
                                }
                            })
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.saveSession("cookie")
        }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String = if (invalidated) "fresh" else "stale"

            override suspend fun invalidate(app: String) {
                invalidated = true
            }
        })
        val api = Retrofit.Builder()
            .baseUrl(server.url("/notifications/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationsApi::class.java)
        store = NotificationsUnreadStore(NotificationsRepository(api), socket, session, context)
    }

    @After
    fun stop() {
        store.stop()
        socket.disconnectAll()
        server.shutdown()
    }

    @Test
    fun `the badge's socket mints a fresh token after a refusal`() {
        store.ensureStarted()
        val deadline = System.currentTimeMillis() + 6_000
        while ("Bearer fresh" !in handshakes) {
            check(System.currentTimeMillis() < deadline) { "no fresh handshake: $handshakes" }
            Thread.sleep(20)
        }
    }
}
