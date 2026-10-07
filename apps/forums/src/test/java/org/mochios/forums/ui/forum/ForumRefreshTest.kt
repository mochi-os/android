// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.repository.SavedRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A forum against a server and its websocket: the posts that arrive while the
 * list is on screen are counted on the refresh button, and a refresh that
 * brings them in clears the count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumRefreshTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: ForumViewModel

    /** The server's end of the forum's websocket, once the client has connected. */
    @Volatile private var live: WebSocket? = null

    /** How many times the list has been asked for. */
    private val lists = AtomicInteger()

    /** Whether the server answers the list at all. */
    @Volatile private var down = false

    /** Holds the list's answer until released. */
    @Volatile private var hold: CountDownLatch? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when (path) {
                    "/forums/f1/-/posts" -> {
                        // Each answer holds one post more than the last.
                        val posts = (1..lists.incrementAndGet()).joinToString { number -> """{"id": "p$number"}""" }
                        hold?.await(10, TimeUnit.SECONDS)
                        if (down) MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
                        else ok("""{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Forum", "sort": "new"}, "posts": [$posts]}""")
                    }
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
                        }

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
        val api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        val session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        model = ForumViewModel(
            SavedStateHandle(mapOf("forumId" to "f1")),
            ForumsRepository(api, FileStore(context)),
            SavedRepository(api, Gson()),
            socket,
            session,
        )
        until { live != null }
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /**
     * Runs the main thread until [done], the server answering on its own. Its
     * clock is moved on each turn, or a debounced reload would never come due.
     */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Somebody else posts to the forum. */
    private fun arrive(post: String) {
        val before = model.newPostsCount.value
        live!!.send("""{"type": "post/create", "post": "$post"}""")
        until { model.newPostsCount.value == before + 1 }
    }

    /** Refreshes as the button and the pull do, and waits for the answer. */
    private fun refresh() {
        val before = lists.get()
        model.refresh()
        until { lists.get() > before && !model.uiState.value.isRefreshing }
    }

    @Test
    fun `posts that arrive are counted, and a refresh that brings them in clears the count`() {
        arrive("p2")
        arrive("p3")
        assertEquals(2, model.newPostsCount.value)
        refresh()
        assertEquals(0, model.newPostsCount.value)
    }

    @Test
    fun `a refresh that fails leaves the count, the posts still being unseen`() {
        arrive("p2")
        down = true
        refresh()
        assertEquals(1, model.newPostsCount.value)
    }

    /**
     * Starts a refresh the server sits on, then has somebody edit a post: the
     * reload that follows replaces the refresh under way. Returns with that
     * reload asked for and both still unanswered.
     */
    private fun overtake(): CountDownLatch {
        val held = CountDownLatch(1)
        hold = held
        model.refresh()
        until { lists.get() == 2 }
        assertTrue(model.uiState.value.isRefreshing)
        live!!.send("""{"type": "post/edit", "post": "p1"}""")
        until { lists.get() == 3 }
        return held
    }

    @Test
    fun `a refresh overtaken by a change to the forum ends as that change's reload lands`() {
        val held = overtake()
        held.countDown()
        until { model.uiState.value.posts.size == 3 }
        assertFalse(model.uiState.value.isRefreshing)
    }

    @Test
    fun `a refresh overtaken by a reload that then fails still ends`() {
        val held = overtake()
        down = true
        held.countDown()
        until { !model.uiState.value.isRefreshing }
        assertEquals(1, model.uiState.value.posts.size)
    }
}
