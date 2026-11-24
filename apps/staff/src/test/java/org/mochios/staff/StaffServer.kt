// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff

import android.os.Looper
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.AppContext
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.staff.api.StaffApi
import org.mochios.staff.repository.StaffRepository
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A staff app on a mock server, for a test's view models: the repository, the
 * session and the socket they are built with, every request they make, and
 * the staff-events socket the server end holds, to send frames down. [answer]
 * gives the reply to each request but the socket's; a null reply is a 404.
 */
class StaffServer(private val answer: (RecordedRequest) -> MockResponse?) {

    val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()

    /** Every request but the socket's, in the order they came. */
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** How many times a socket was opened. */
    val handshakes = AtomicInteger()

    /** The server end of the newest socket. */
    @Volatile
    var peer: WebSocket? = null

    lateinit var repository: StaffRepository
    lateinit var session: SessionManager
    lateinit var socket: MochiWebSocket

    fun start(): StaffServer {
        AppContext.set(context)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path.orEmpty().startsWith("/_/websocket")) {
                    handshakes.incrementAndGet()
                    return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            peer = webSocket
                        }

                        // Answer the client's close, or the server waits on it.
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                }
                requests += request
                return answer(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val api = Retrofit.Builder()
            .baseUrl(server.url("/staff/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StaffApi::class.java)
        repository = StaffRepository(api)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        return this
    }

    fun stop() {
        socket.disconnectAll()
        server.shutdown()
    }

    /** The requests made of the action [path] (`-/reports/list`), in order. */
    fun made(path: String): List<RecordedRequest> =
        requests.filter { request -> request.requestUrl?.encodedPath?.endsWith(path) == true }

    /** Runs the main looper until [done], failing after five seconds. */
    fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets the main looper and the server run for [millis], for a test that something does not happen. */
    fun idle(millis: Long = 400) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    companion object {
        fun ok(data: String): MockResponse = MockResponse().setBody("""{"data": $data}""")

        fun refused(message: String = "Comptroller is not available"): MockResponse =
            MockResponse().setResponseCode(502).setBody("""{"error": "$message"}""")
    }
}
