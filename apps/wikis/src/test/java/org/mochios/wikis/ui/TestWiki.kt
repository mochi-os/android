// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui

import android.os.Looper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.PageFetchResponseDeserializer
import org.mochios.wikis.repository.WikisRepository
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * A wiki server for view model and screen tests. [answer] gives the response
 * to a request by its method and path, or null for a 404; the wiki's socket
 * is always accepted.
 */
internal class TestWiki(private val answer: (method: String, path: String) -> MockResponse?) {
    val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()

    /** Each request the server has been sent, as "METHOD path". */
    val requests = ConcurrentLinkedQueue<String>()
    lateinit var repository: WikisRepository
    lateinit var session: SessionManager
    lateinit var socket: MochiWebSocket
    private val jobs = mutableListOf<Job>()

    fun start(): TestWiki {
        AppContext.set(context)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val method = request.method.orEmpty()
                val path = request.path.orEmpty().substringBefore("?")
                requests.add("$method $path")
                if (path == "/_/websocket") {
                    return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                        // Answer the client's close, or the server waits on it.
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                }
                return answer(method, path) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val gson = GsonBuilder()
            .registerTypeAdapter(PageFetchResponse::class.java, PageFetchResponseDeserializer())
            .create()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WikisApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = WikisRepository(api, FileStore(context), context)
        return this
    }

    fun stop() {
        jobs.forEach { job -> job.cancel() }
        socket.disconnectAll()
        server.shutdown()
    }

    /** How many times "METHOD path" has been asked for. */
    fun count(request: String): Int = requests.count { sent -> sent == request }

    /** Everything [flow] emits from now on, as it arrives on the main thread. */
    fun <T> collect(flow: Flow<T>): MutableList<T> {
        val seen = java.util.Collections.synchronizedList(mutableListOf<T>())
        jobs += CoroutineScope(Dispatchers.Main).launch { flow.collect { item -> seen += item } }
        return seen
    }
}

internal fun ok(data: String): MockResponse = MockResponse().setBody("""{"data": $data}""")

internal fun refused(code: Int, message: String): MockResponse =
    MockResponse().setResponseCode(code).setBody("""{"error": "$message"}""")

/** Runs the main looper the network's answers resume on until [done]. */
internal fun until(done: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!done()) {
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        check(System.currentTimeMillis() < deadline) { "timed out" }
        Thread.sleep(10)
    }
}

/** Lets everything already under way finish. */
internal fun settle() {
    repeat(20) {
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        Thread.sleep(10)
    }
}
