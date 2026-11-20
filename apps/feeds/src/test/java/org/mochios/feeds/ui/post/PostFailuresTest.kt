// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.post

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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.repository.FeedsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The post screen against a stand-in server: a refused reaction is said, not
 * answered by fetching the post again; and a frame about the post, which
 * arrives on the socket's own thread, waits while a tap waits on the server,
 * as a fetch landing first would put the old value back over the tap's guess.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostFailuresTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: FeedsRepository

    /** Holds the pin's answer until released. */
    private val gate = CountDownLatch(1)

    /** Whether the reaction is refused. */
    @Volatile private var refuse = false

    /** When the server holds the post pinned; 0 for not. */
    @Volatile private var pinned = 0L

    /** How many times each path was asked for. */
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** The server's end of each open socket, by key. */
    private val live = ConcurrentHashMap<String, WebSocket>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                return when (path) {
                    "/feeds/f1/-/p1" -> ok("""{"posts": [{"id": "p1", "feed": "f1", "pinned": $pinned}], "permissions": {}}""")
                    "/feeds/f1/-/p1/pin" -> {
                        gate.await(5, TimeUnit.SECONDS)
                        pinned = 1_700_000_000L
                        ok("""{"post": "p1", "pinned": $pinned}""")
                    }
                    "/feeds/f1/-/p1/react" -> if (refuse) {
                        MockResponse().setResponseCode(403).setBody("""{"error": "Not allowed"}""")
                    } else {
                        ok("{}")
                    }
                    "/_/websocket" -> {
                        val key = request.requestUrl?.queryParameter("key").orEmpty()
                        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                live[key] = webSocket
                            }

                            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                                webSocket.close(code, reason)
                            }
                        })
                    }
                    else -> if (request.method == "POST") ok("{}") else MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("a1")
        }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = FeedsRepository(
            retrofit.create(FeedsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
            context,
            session,
        )
    }

    @After
    fun end() {
        gate.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun count(path: String): Int = counts[path]?.get() ?: 0

    /** Runs the main thread until [done]; an assertion fails if it never is. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun page(): PostDetailViewModel {
        val model = PostDetailViewModel(
            SavedStateHandle(mapOf("feedId" to "f1", "postId" to "p1")),
            repository,
            socket,
            session,
        )
        until { model.post.value != null && live.containsKey("f1") }
        settle()
        return model
    }

    @Test
    fun `a refused reaction is said, and the post is not fetched again`() {
        val model = page()
        val reads = count("/feeds/f1/-/p1")
        refuse = true
        model.reactToPost("like")
        until { model.actionError.value != null }
        settle()
        assertNotNull("THE POINT: the refusal is said", model.actionError.value)
        assertEquals("the post is not fetched again for it", reads, count("/feeds/f1/-/p1"))
        assertEquals("", model.post.value!!.myReaction)
    }

    @Test
    fun `a frame about the post waits while a tap waits, and is fetched once it is answered`() {
        val model = page()
        model.togglePin()
        until { count("/feeds/f1/-/p1/pin") == 1 }
        val reads = count("/feeds/f1/-/p1")
        live.getValue("f1").send("""{"type": "react/post", "feed": "f1", "post": "p1"}""")
        settle()
        assertEquals("THE POINT: no fetch goes out while the pin waits", reads, count("/feeds/f1/-/p1"))

        gate.countDown()
        until { count("/feeds/f1/-/p1") > reads }
        settle()
        assertEquals("the server's pin stands", 1_700_000_000L, model.post.value!!.pinned)
    }
}
