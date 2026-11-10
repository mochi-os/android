// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui

import android.app.Application
import android.content.Context
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
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.SavedRepository
import org.mochios.feeds.ui.feed.FeedActionEvent
import org.mochios.feeds.ui.feed.FeedViewModel
import org.mochios.feeds.ui.post.PostDetailViewModel
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
 * A pin and a comment reaction, as the web has them: each shows on the tap,
 * before the server answers, and goes back with the error if the server
 * refuses. A refused reaction takes back the reader's own reaction alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedInstantTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var repository: FeedsRepository
    private lateinit var session: SessionManager
    private lateinit var api: FeedsApi

    /** Holds every write until released, so a test sees the screen before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the writes it is sent. */
    @Volatile private var refuse = false

    /** Each write the server has been sent, by path. */
    private val writes = ConcurrentLinkedQueue<String>()

    /** When the server has the post pinned, in seconds; 0 for not pinned. */
    @Volatile private var pinned = 0L

    /** The reader's own reaction on the comment, as the server holds it. */
    @Volatile private var mine = ""

    /** Another reader's reactions on the comment. */
    @Volatile private var others = """[{"reaction": "like"}]"""

    private val events = mutableListOf<FeedActionEvent>()
    private var collector: Job? = null

    @Before
    fun begin() {
        context.getSharedPreferences("mochi_feeds", Context.MODE_PRIVATE)
            .edit().putBoolean("unread_only", false).commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when (path) {
                    "/feeds/f1/-/info" ->
                        ok("""{"feed": {"id": "f1", "fingerprint": "fp1", "name": "Feed", "owner": 1}, "permissions": {}}""")
                    "/feeds/f1/-/posts" -> ok("""{"posts": [${post()}]}""")
                    "/feeds/f1/-/p1", "/feeds/fp1/-/p1" -> ok("""{"posts": [${post()}], "permissions": {}}""")
                    "/feeds/f1/-/p1/pin", "/feeds/fp1/-/p1/pin", "/feeds/f1/-/p1/unpin", "/feeds/fp1/-/p1/unpin" -> {
                        writes.add(path.substringAfterLast('/'))
                        val time = if (path.endsWith("/pin")) 1_700_000_000L else 0L
                        val answered = answer("""{"post": "p1", "pinned": $time}""")
                        if (!refuse) pinned = time
                        answered
                    }
                    "/feeds/f1/-/p1/comment/react" -> {
                        writes.add("react")
                        val reaction = request.body.readUtf8().substringAfter("reaction=").substringBefore('&')
                        val answered = answer("""{"success": true}""")
                        if (!refuse) mine = if (reaction == "none") "" else reaction
                        answered
                    }
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
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
        api = retrofit.create(FeedsApi::class.java)
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("a1", base)
        }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = FeedsRepository(api, retrofit.create(MenuApi::class.java), FileStore(context), context, session)
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

    /** The post as the server holds it, with one comment shaped as feeds sends it: no `created_string`. */
    private fun post(): String {
        val comment = """{"id": "c1", "feed": "f1", "post": "p1", "parent": "", "subscriber": "s2", "name": "Sam", """ +
            """"body": "Hello", "format": "text", "created": 1, "edited": 0, "attachment": "", "remote": "", """ +
            """"page": "", "user": "a1", "attachments": [], "attachment_name": "", "attachment_caption": "", """ +
            """"my_reaction": "$mine", "reactions": $others, "children": []}"""
        return """{"id": "p1", "feed": "f1", "feed_fingerprint": "fp1", "pinned": $pinned, "comments": [$comment]}"""
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
            context as Application,
        )
        until { model.post.value != null }
        settle()
        return model
    }

    private fun list(): FeedViewModel {
        val model = FeedViewModel(
            SavedStateHandle(mapOf("feedId" to "f1")),
            repository,
            SavedRepository(api, Gson(), session),
            socket,
            session,
            context,
        )
        collector = CoroutineScope(Dispatchers.Main).launch { model.actionEvents.collect { events += it } }
        until { model.posts.value.isNotEmpty() }
        settle()
        return model
    }

    private fun PostDetailViewModel.comment() = post.value!!.comments.single()

    @Test
    fun `a comment reaction shows on the tap, and goes back with the error when refused`() {
        val model = page()
        model.reactToComment("c1", "love")
        until { writes.contains("react") }
        assertEquals("love", model.comment().myReaction)

        refuse = true
        gate.countDown()
        until { model.actionError.value != null }
        assertEquals("", model.comment().myReaction)
        // Another reader's reaction is held apart from the reader's own, and stays.
        assertEquals("like", model.comment().reactions.single().reaction)
    }

    @Test
    fun `an accepted comment reaction stays, read back from the server`() {
        val model = page()
        model.reactToComment("c1", "love")
        gate.countDown()
        until { writes.contains("react") }
        settle()
        assertEquals("love", model.comment().myReaction)
        assertNull(model.actionError.value)
    }

    @Test
    fun `a refused comment reaction leaves what a reload brought in meanwhile`() {
        val model = page()
        model.reactToComment("c1", "love")
        until { writes.contains("react") }
        // The reader set another reaction elsewhere, and a reload brings it in.
        mine = "laugh"
        model.loadPost()
        until { model.comment().myReaction == "laugh" }

        refuse = true
        gate.countDown()
        until { model.actionError.value != null }
        assertEquals("laugh", model.comment().myReaction)
    }

    @Test
    fun `a pin on the post page shows on the tap, and goes back with the error when refused`() {
        val model = page()
        model.togglePin()
        until { writes.contains("pin") }
        assertTrue(model.post.value!!.pinned > 0)

        refuse = true
        gate.countDown()
        until { model.actionError.value != null }
        assertEquals(0L, model.post.value!!.pinned)
    }

    @Test
    fun `an accepted pin on the post page takes the server's time`() {
        val model = page()
        model.togglePin()
        gate.countDown()
        until { model.actionMessage.value != null }
        assertEquals(1_700_000_000L, model.post.value!!.pinned)
        assertNull(model.actionError.value)
    }

    @Test
    fun `a pin in the feed list shows on the tap, and goes back with the error when refused`() {
        val model = list()
        model.togglePin("fp1", "p1")
        until { writes.contains("pin") }
        assertTrue(model.posts.value.single().pinned > 0)

        refuse = true
        gate.countDown()
        until { events.any { it is FeedActionEvent.Failure } }
        assertEquals(0L, model.posts.value.single().pinned)
    }

    @Test
    fun `an accepted pin in the feed list takes the server's time`() {
        val model = list()
        model.togglePin("fp1", "p1")
        gate.countDown()
        until { events.any { it is FeedActionEvent.Done } }
        assertEquals(1_700_000_000L, model.posts.value.single().pinned)
        assertNotNull(events.single { it is FeedActionEvent.Done })
    }
}
