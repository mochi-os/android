// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

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
import org.mochios.feeds.ui.feedlist.FeedListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mark all read and the New posts tick, as the web has them: what they change
 * shows on the tap, before the server answers, and goes back if the server
 * refuses, with the error said.
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

    /** How many times the drawer's class info has been asked for. */
    private val rosters = AtomicInteger()

    /** The feeds' unread counts as the server lists them. */
    @Volatile private var counts = mapOf("f1" to 3, "f2" to 2)

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
                    "/feeds/-/info" -> {
                        rosters.incrementAndGet()
                        val feeds = counts.entries.joinToString {
                            """{"id": "${it.key}", "fingerprint": "${it.key}", "name": "${it.key}", "unread": ${it.value}}"""
                        }
                        ok("""{"feeds": [$feeds], "settings": {"sort": "new"}}""")
                    }
                    "/feeds/f1/-/info" ->
                        ok("""{"feed": {"id": "f1", "fingerprint": "f1", "name": "f1", "unread": 3}, "permissions": {}}""")
                    "/feeds/f1/-/posts", "/feeds/-/posts" ->
                        ok("""{"posts": [${post("p1", 0)}, ${post("p2", 0)}, ${post("p3", 100)}]}""")
                    "/feeds/f1/-/notifications" -> ok("""{"post": true}""")
                    "/feeds/f1/-/read-all", "/feeds/-/read-all" -> {
                        writes.add(path)
                        answer("{}")
                    }
                    "/feeds/f1/-/notifications/set" -> {
                        writes.add(path)
                        val enabled = request.body.readUtf8().contains("enabled=true")
                        answer("""{"post": $enabled}""")
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
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(FeedsApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
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

    private fun post(id: String, read: Long) = """{"id": "$id", "feed": "f1", "feed_fingerprint": "f1", "read": $read}"""

    private fun answer(data: String): MockResponse {
        gate.await(5, TimeUnit.SECONDS)
        return if (refuse) MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""") else ok(data)
    }

    /** The feed screen for [feed], its posts and the drawer's feeds loaded. */
    private fun open(feed: String = "f1"): Pair<FeedViewModel, FeedListViewModel> {
        val model = FeedViewModel(
            SavedStateHandle(mapOf("feedId" to feed)),
            repository,
            SavedRepository(api, Gson(), session),
            socket,
            session,
            context,
        )
        val drawer = FeedListViewModel(repository, socket, session)
        collector = CoroutineScope(Dispatchers.Main).launch { model.actionEvents.collect { events += it } }
        until { model.posts.value.size == 3 && drawer.feeds.value.size == 2 }
        settle()
        return model to drawer
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

    private fun FeedViewModel.read(id: String) = posts.value.single { it.id == id }.read

    private fun FeedListViewModel.unread(id: String) = feeds.value.single { it.id == id }.unread

    private fun FeedViewModel.markAllRead(drawer: FeedListViewModel) =
        markAllRead { drawer.clearUnread(if (isAllFeeds) null else feedId) }

    @Test
    fun `Mark all read shows the feed read on the tap, and a refusal puts it all back and says why`() {
        val (model, drawer) = open()
        model.markAllRead(drawer)
        until { writes.contains("/feeds/f1/-/read-all") }
        settle()
        assertTrue("the posts waited for the server", model.read("p1") != 0L && model.read("p2") != 0L)
        assertEquals(0, model.feedInfo.value?.unread)
        assertEquals(0, drawer.unread("f1"))
        assertEquals(2, drawer.unread("f2"))

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertTrue(events.single() is FeedActionEvent.Failure)
        assertEquals(0L, model.read("p1"))
        assertEquals(0L, model.read("p2"))
        // Read before the tap, so it keeps the time it was read.
        assertEquals(100L, model.read("p3"))
        assertEquals(3, model.feedInfo.value?.unread)
        assertEquals(3, drawer.unread("f1"))
        assertEquals(2, drawer.unread("f2"))
    }

    @Test
    fun `an accepted Mark all read stays read, and the drawer reads the server's counts`() {
        val (model, drawer) = open()
        val before = rosters.get()
        counts = mapOf("f1" to 0, "f2" to 2)
        model.markAllRead(drawer)
        gate.countDown()
        until { rosters.get() > before }
        settle()
        assertTrue(model.read("p1") != 0L)
        assertEquals(0, drawer.unread("f1"))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `from All feeds every badge clears on the tap, and each gets back its own if refused`() {
        val (model, drawer) = open("__all__")
        model.markAllRead(drawer)
        until { writes.contains("/feeds/-/read-all") }
        settle()
        assertEquals(listOf(0, 0), drawer.feeds.value.map { it.unread })

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertEquals(3, drawer.unread("f1"))
        assertEquals(2, drawer.unread("f2"))
    }

    @Test
    fun `a drawer list fetched before the server answers keeps the badge clear, and a refusal gives it back once`() {
        val (model, drawer) = open()
        model.markAllRead(drawer)
        until { writes.contains("/feeds/f1/-/read-all") }
        val before = rosters.get()
        drawer.refresh()
        until { rosters.get() > before && !drawer.isRefreshing.value }
        assertEquals(0, drawer.unread("f1"))

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertEquals(3, drawer.unread("f1"))
    }

    @Test
    fun `a badge two clears cover stays clear until the later answers, which gives it back`() {
        val (model, drawer) = open()
        model.markAllRead(drawer)
        until { writes.contains("/feeds/f1/-/read-all") }
        // All feeds clears every badge while the feed's own clear waits.
        val every = drawer.clearUnread(null)
        assertEquals(listOf(0, 0), drawer.feeds.value.map { it.unread })

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertEquals(0, drawer.unread("f1"))

        every(false)
        assertEquals(3, drawer.unread("f1"))
        assertEquals(2, drawer.unread("f2"))
    }

    @Test
    fun `the New posts tick moves on the tap, and back with the error when refused`() {
        val (model, _) = open()
        until { model.notifications.value != null }
        model.setPostNotifications(false)
        until { writes.contains("/feeds/f1/-/notifications/set") }
        assertEquals(false, model.notifications.value?.post)

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertTrue(events.single() is FeedActionEvent.Failure)
        assertEquals(true, model.notifications.value?.post)
    }

    @Test
    fun `an accepted tick keeps the server's answer`() {
        val (model, _) = open()
        until { model.notifications.value != null }
        model.setPostNotifications(false)
        gate.countDown()
        until { writes.contains("/feeds/f1/-/notifications/set") }
        settle()
        assertEquals(false, model.notifications.value?.post)
        assertTrue(events.isEmpty())
    }
}
