// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm

import android.content.Context
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.crm.api.CrmsApi
import org.mochios.crm.repository.CrmsRepository
import org.robolectric.Shadows.shadowOf
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** One request the CRM server was sent: its path under `/crm/`, body and content type. */
data class Sent(val path: String, val body: String, val type: String) {
    /** The body as the JSON object the server reads. */
    fun json(): JsonObject = JsonParser.parseString(body).asJsonObject
}

/**
 * A CRM server for view model and screen tests: each path answers as a test
 * sets it (`{"data": {}}` otherwise), every request is kept in order, and the
 * socket the app opens can be sent frames.
 */
class CrmServer(private val context: Context) {

    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()

    /** Every request, in the order it arrived. */
    val sent = ConcurrentLinkedQueue<Sent>()

    /** The server's end of the app's socket, once open. */
    @Volatile var live: WebSocket? = null

    val session = SessionManager(context)

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val full = request.path.orEmpty().substringBefore("?")
                if (full == "/_/websocket") {
                    return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                }
                val path = full.substringAfter("/crm/")
                sent.add(Sent(path, request.body.clone().readUtf8(), request.getHeader("Content-Type").orEmpty()))
                return routes[path]?.invoke(request) ?: ok("{}")
            }
        }
        server.start()
        runBlocking { session.setServerUrl(base()) }
        // Error messages are read through it.
        AppContext.set(context)
    }

    private fun base() = server.url("/").toString().trimEnd('/')

    /** Answer [path] with [answer]. */
    fun on(path: String, answer: (RecordedRequest) -> MockResponse) {
        routes[path] = answer
    }

    /** Answer [path] with `{"data": [json]}`. */
    fun data(path: String, json: String) = on(path) { ok(json) }

    /** Refuse [path] with [status] and [message]. */
    fun refuse(path: String, message: String = "Refused", status: Int = 400) =
        on(path) { MockResponse().setResponseCode(status).setBody("""{"error": "$message"}""") }

    fun stop() = server.shutdown()

    fun repository(): CrmsRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/crm/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CrmsApi::class.java)
        return CrmsRepository(api, FileStore(context), context, SessionManager(context))
    }

    fun socket(): MochiWebSocket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
        override suspend fun serverUrl(): String = base()

        override suspend fun token(app: String): String? = "token"

        override suspend fun invalidate(app: String) {
        }
    })

    /** The requests made of [path], in order. */
    fun of(path: String): List<Sent> = sent.filter { it.path == path }

    companion object {
        fun ok(data: String): MockResponse = MockResponse().setBody("""{"data": $data}""")
    }
}

/** Wait for [check], running the main looper the answers resume on. */
fun until(what: String, check: () -> Boolean) {
    repeat(500) {
        shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
        if (check()) return
        Thread.sleep(10)
    }
    throw AssertionError("timed out waiting for $what")
}

/** Run the main looper for [millis], for an answer that should not come. */
fun settle(millis: Long = 600) {
    val end = System.currentTimeMillis() + millis
    while (System.currentTimeMillis() < end) {
        shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
        Thread.sleep(10)
    }
}
