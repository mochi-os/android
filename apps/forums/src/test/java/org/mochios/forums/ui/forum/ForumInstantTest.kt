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
import java.util.concurrent.ConcurrentHashMap
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

    /** Writes held by a gate of their own, by path. */
    private val gates = ConcurrentHashMap<String, CountDownLatch>()

    /** Writes the server refuses, by path. */
    private val refused = ConcurrentHashMap.newKeySet<String>()

    /** The reader's votes the server holds, by post or comment. */
    private val votes = ConcurrentHashMap<String, String>()

    /** The lock and pin the server holds on the post. */
    private val flags = ConcurrentHashMap.newKeySet<String>()

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
                    "/forums/f1/-/p1" -> ok(view())
                    "/forums/f1/-/p1/vote/up", "/forums/f1/-/p1/c1/vote/up", "/forums/f1/-/p1/c2/vote/up" -> {
                        writes.add(path)
                        val answered = answer(path, "{}")
                        if (answered.status.contains("200")) {
                            votes[path.removePrefix("/forums/f1/-/p1").removeSuffix("/vote/up").trim('/').ifEmpty { "p1" }] = "up"
                        }
                        answered
                    }
                    "/forums/f1/-/p1/lock", "/forums/f1/-/p1/pin" -> {
                        writes.add(path)
                        val answered = answer(path, "{}")
                        if (answered.status.contains("200")) flags += path.substringAfterLast('/')
                        answered
                    }
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

    private fun answer(data: String): MockResponse = answer("", data)

    /** The answer to a write on [path], held by its own gate where a test set one. */
    private fun answer(path: String, data: String): MockResponse {
        (gates[path] ?: gate).await(5, TimeUnit.SECONDS)
        return if (refuse || path in refused) {
            MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")
        } else {
            ok(data)
        }
    }

    /** Hold the write on [path] until the returned gate is released. */
    private fun hold(path: String) = CountDownLatch(1).also { gates[path] = it }

    /** The post as the server holds it: c2 is a reply to c1. */
    private fun view(): String {
        fun up(id: String, base: Int) = base + if (votes[id] == "up") 1 else 0
        fun vote(id: String) = votes[id].orEmpty()
        val comment2 = """{"id": "c2", "up": ${up("c2", 0)}, "user_vote": "${vote("c2")}"}"""
        val comment1 = """{"id": "c1", "up": ${up("c1", 1)}, "user_vote": "${vote("c1")}", "children": [$comment2]}"""
        val post = """{"id": "p1", "forum": "f1", "up": ${up("p1", 2)}, "user_vote": "${vote("p1")}", """ +
            """"locked": ${"lock" in flags}, "pinned": ${"pin" in flags}}"""
        return """{"forum": {"id": "f1"}, "post": $post, "comments": [$comment1], "following": $following}"""
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

    @Test
    fun `a post vote counts on the tap, and goes back with the error when refused`() {
        val model = post()
        model.votePost("up")
        until { writes.contains("/forums/f1/-/p1/vote/up") }
        assertEquals(3, model.uiState.value.post.up)
        assertEquals("up", model.uiState.value.post.userVote)

        refuse = true
        gate.countDown()
        until { model.uiState.value.error != null }
        assertEquals(2, model.uiState.value.post.up)
        assertEquals("", model.uiState.value.post.userVote)
    }

    @Test
    fun `an accepted post vote stays, read back from the server`() {
        val model = post()
        model.votePost("up")
        gate.countDown()
        until { writes.contains("/forums/f1/-/p1/vote/up") }
        settle()
        assertEquals(3, model.uiState.value.post.up)
        assertEquals("up", model.uiState.value.post.userVote)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `a refused comment vote goes back on that comment alone`() {
        val model = post()
        val first = hold("/forums/f1/-/p1/c1/vote/up")
        val second = hold("/forums/f1/-/p1/c2/vote/up")
        model.voteComment("c1", "up")
        model.voteComment("c2", "up")
        until { writes.contains("/forums/f1/-/p1/c2/vote/up") }
        fun comment(id: String) = model.uiState.value.comments.single().let { if (id == "c1") it else it.children.single() }
        assertEquals(2, comment("c1").up)
        assertEquals(1, comment("c2").up)

        refused += "/forums/f1/-/p1/c1/vote/up"
        first.countDown()
        until { model.uiState.value.error != null }
        assertEquals(1, comment("c1").up)
        assertEquals("", comment("c1").userVote)
        // The reply's vote still waits on the server, and still shows.
        assertEquals(1, comment("c2").up)
        assertEquals("up", comment("c2").userVote)

        second.countDown()
        settle()
        assertEquals(1, comment("c2").up)
        assertEquals("up", comment("c2").userVote)
    }

    @Test
    fun `lock and pin show on the tap, and go back with the error when refused`() {
        val model = post()
        model.lockPost()
        model.pinPost()
        until { writes.contains("/forums/f1/-/p1/lock") && writes.contains("/forums/f1/-/p1/pin") }
        assertTrue(model.uiState.value.post.locked)
        assertTrue(model.uiState.value.post.pinned)

        refuse = true
        gate.countDown()
        until { model.uiState.value.error != null }
        settle()
        assertEquals(false, model.uiState.value.post.locked)
        assertEquals(false, model.uiState.value.post.pinned)
    }

    @Test
    fun `an accepted lock stays, read back from the server`() {
        val model = post()
        model.lockPost()
        gate.countDown()
        until { writes.contains("/forums/f1/-/p1/lock") }
        settle()
        assertTrue(model.uiState.value.post.locked)
        assertNull(model.uiState.value.error)
    }
}
