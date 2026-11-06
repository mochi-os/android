// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import org.mochios.forums.ui.post.PostViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A forum's notification ticks and following a post, as the web has them:
 * each moves on the tap, before the server answers, and goes back with the
 * error if the server refuses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumInstantTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var repository: ForumsRepository
    private lateinit var session: SessionManager
    private lateinit var api: ForumsApi

    /** Holds every write until released, so a test sees the screen before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the writes it is sent. */
    @Volatile private var refuse = false

    /** Each write the server has been sent, by path. */
    private val writes = ConcurrentLinkedQueue<String>()

    /** Whether the server has the reader following the post. */
    @Volatile private var following = false

    private val events = mutableListOf<ForumEvent>()
    private var collector: Job? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when (path) {
                    "/forums/f1/-/posts" ->
                        ok("""{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Forum"}, "posts": [{"id": "p1"}]}""")
                    "/forums/-/information" -> ok("""{"forums": [], "settings": {"sort": ""}}""")
                    "/forums/f1/-/notifications" -> ok("""{"post": true, "reply": false, "comment": false}""")
                    "/forums/f1/-/notifications/set" -> {
                        writes.add(path)
                        val body = request.body.readUtf8()
                        val enabled = body.contains("enabled=true")
                        answer("""{"post": ${if (body.contains("kind=post")) enabled else true}, "reply": false, "comment": false}""")
                    }
                    "/forums/f1/-/p1" ->
                        ok("""{"forum": {"id": "f1"}, "post": {"id": "p1", "forum": "f1"}, "comments": [], "following": $following}""")
                    "/forums/f1/-/p1/follow", "/forums/f1/-/p1/unfollow" -> {
                        writes.add(path)
                        val answered = answer("""{"following": ${path.endsWith("/follow")}}""")
                        if (!refuse) following = path.endsWith("/follow")
                        answered
                    }
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
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
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = ForumsRepository(api, FileStore(context), context, session)
    }

    @After
    fun end() {
        gate.countDown()
        collector?.cancel()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun answer(data: String): MockResponse {
        gate.await(5, TimeUnit.SECONDS)
        return if (refuse) MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""") else ok(data)
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun forum(): ForumViewModel {
        val model = ForumViewModel(
            SavedStateHandle(mapOf("forumId" to "f1")),
            repository,
            SavedRepository(api, Gson(), session),
            socket,
            session,
        )
        collector = CoroutineScope(Dispatchers.Main).launch { model.events.collect { events += it } }
        until { model.uiState.value.notifications != null }
        settle()
        return model
    }

    private fun post(): PostViewModel {
        val model = PostViewModel(
            SavedStateHandle(mapOf("forumId" to "f1", "postId" to "p1")),
            context,
            repository,
            session,
            socket,
        )
        until { model.uiState.value.following != null && !model.uiState.value.isLoading }
        settle()
        return model
    }

    @Test
    fun `a notification tick moves on the tap, and back with the error when refused`() {
        val model = forum()
        model.setNotification("reply", true)
        until { writes.contains("/forums/f1/-/notifications/set") }
        assertEquals(true, model.uiState.value.notifications?.reply)
        assertEquals(true, model.uiState.value.notifications?.post)

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertTrue(events.single() is ForumEvent.ShowError)
        assertEquals(false, model.uiState.value.notifications?.reply)
        assertEquals(true, model.uiState.value.notifications?.post)
    }

    @Test
    fun `an accepted tick takes the server's answer`() {
        val model = forum()
        model.setNotification("post", false)
        gate.countDown()
        until { writes.contains("/forums/f1/-/notifications/set") }
        settle()
        assertEquals(false, model.uiState.value.notifications?.post)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `following a post flips on the tap, and back with the error when refused`() {
        val model = post()
        model.setFollowing(true)
        until { writes.contains("/forums/f1/-/p1/follow") }
        assertEquals(true, model.uiState.value.following)

        refuse = true
        gate.countDown()
        until { model.uiState.value.error != null }
        assertNotNull(model.uiState.value.error)
        assertEquals(false, model.uiState.value.following)
    }

    @Test
    fun `an accepted follow stays, read back from the server`() {
        val model = post()
        model.setFollowing(true)
        gate.countDown()
        until { writes.contains("/forums/f1/-/p1/follow") }
        settle()
        assertEquals(true, model.uiState.value.following)
        assertNull(model.uiState.value.error)
    }
}
