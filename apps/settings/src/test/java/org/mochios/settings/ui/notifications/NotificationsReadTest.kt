// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.notifications.NotificationsApi
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.android.notifications.NotificationsUnreadStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.settings.api.NotificationPrefsApi
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
 * Reading notifications on the Notifications screen, as the web has it: a row
 * shows read on the tap, before the server answers, and goes back to unread
 * if the server refuses, with the error shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationsReadTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: NotificationsViewModel

    /** Holds every read until released, so a test sees the screen before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the reads it is sent. */
    @Volatile private var refuse = false

    /** Each read the server has been sent: a row's id, or "all". */
    private val reads = ConcurrentLinkedQueue<String>()

    /** How many times the list has been asked for. */
    private val lists = AtomicInteger()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path.orEmpty().substringBefore("?")) {
                    "/notifications/-/list" -> {
                        lists.incrementAndGet()
                        MockResponse().setBody(
                            """{"data": [${row("a", 0)}, ${row("b", 0)}, ${row("c", 100)}], "count": 2}""",
                        )
                    }
                    "/notifications/-/read" -> {
                        reads.add(Regex("id=([^&]+)").find(request.body.readUtf8())?.groupValues?.get(1).orEmpty())
                        answer()
                    }
                    "/notifications/-/read/all" -> {
                        reads.add("all")
                        answer()
                    }
                    "/notifications/-/count" -> MockResponse().setBody("""{"data": {"count": 0}}""")
                    "/settings/-/notifications/categories", "/settings/-/notifications/topics" ->
                        MockResponse().setBody("[]")
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }

                        override fun onOpen(webSocket: WebSocket, response: Response) {
                        }
                    })
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() {
        gate.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun row(id: String, read: Long) = """{"id": "$id", "app": "feeds", "title": "$id", "read": $read}"""

    private fun answer(): MockResponse {
        gate.await(5, TimeUnit.SECONDS)
        return if (refuse) {
            MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")
        } else {
            MockResponse().setBody("""{"data": {}}""")
        }
    }

    private fun open() {
        val base = server.url("/").toString().trimEnd('/')
        val session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        val api = Retrofit.Builder()
            .baseUrl(server.url("/notifications/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationsApi::class.java)
        val prefs = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationPrefsApi::class.java)
        val repository = NotificationsRepository(api)
        model = NotificationsViewModel(
            repository,
            NotificationsUnreadStore(repository, socket, session, context),
            socket,
            session,
            prefs,
        )
        until { model.uiState.value.items.size == 3 && !model.uiState.value.isLoading }
        settle()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out with ${model.uiState.value}" }
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun read(id: String) = model.uiState.value.items.single { it.id == id }.read

    @Test
    fun `a row shows read on the tap, and unread again with the error when refused`() {
        open()
        model.markRead("a")
        until { reads.contains("a") }
        settle()
        assertTrue("the row waited for the server", read("a") != 0L)
        assertEquals(1, model.uiState.value.unreadCount)

        refuse = true
        gate.countDown()
        until { model.uiState.value.error != null }
        assertEquals(0L, read("a"))
        assertEquals(0L, read("b"))
        assertEquals(2, model.uiState.value.unreadCount)
    }

    @Test
    fun `an accepted read stays read, with no error`() {
        open()
        model.markRead("a")
        gate.countDown()
        until { reads.contains("a") }
        settle()
        assertTrue(read("a") != 0L)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `Mark all read shows every row read at once, and a refusal puts back only those it marked`() {
        open()
        model.markAllRead()
        until { reads.contains("all") }
        settle()
        assertTrue("the rows waited for the server", read("a") != 0L && read("b") != 0L)
        assertEquals(0, model.uiState.value.unreadCount)

        refuse = true
        gate.countDown()
        until { model.uiState.value.error != null }
        assertEquals(0L, read("a"))
        assertEquals(0L, read("b"))
        // Read before the click, so it keeps the time it was read.
        assertEquals(100L, read("c"))
        assertEquals(2, model.uiState.value.unreadCount)
    }

    @Test
    fun `a list fetched before the server has the read still shows the row read`() {
        open()
        model.markRead("a")
        until { reads.contains("a") }
        val before = lists.get()
        model.refresh()
        until { lists.get() > before && !model.uiState.value.isRefreshing }
        assertTrue("the fetched list undid the tap", read("a") != 0L)
        assertEquals(1, model.uiState.value.unreadCount)

        refuse = true
        gate.countDown()
        until { model.uiState.value.error != null }
        assertNotNull(model.uiState.value.error)
        assertEquals(0L, read("a"))
        assertEquals(2, model.uiState.value.unreadCount)
    }
}
