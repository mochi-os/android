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
import org.mochios.android.push.DeviceStore
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
 * if the server refuses, with the error shown. The server's frames are applied
 * to the list where they can be, and fetch it where they cannot.
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

    /** How many times the picker's categories and topics have been asked for. */
    private val categories = AtomicInteger()
    private val topics = AtomicInteger()

    /** The topics the server holds, as its JSON: the feeds rows' own by default. */
    @Volatile private var topicRows = """[{"app": {"id": "feeds", "name": "Feeds"}, "topic": "post", "object": "f1", "category": "1"}]"""

    /** The rows the list answers, as the server holds them when it is asked. */
    @Volatile private var listed = listOf(row("a", 0), row("b", 0), row("c", 100))

    /** When set, holds each list answer, already read from [listed], until released. */
    @Volatile private var held: CountDownLatch? = null

    /** The server's ends of the open sockets, to send frames down. */
    private val sockets = ConcurrentLinkedQueue<WebSocket>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path.orEmpty().substringBefore("?")) {
                    "/notifications/-/list" -> {
                        lists.incrementAndGet()
                        val rows = listed
                        val unread = rows.count { it.endsWith("\"read\": 0}") }
                        val body = """{"data": [${rows.joinToString()}], "count": $unread}"""
                        held?.await(5, TimeUnit.SECONDS)
                        MockResponse().setBody(body)
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
                    "/settings/-/notifications/categories" -> {
                        categories.incrementAndGet()
                        MockResponse().setBody("""[{"id": "1", "label": "Normal"}]""")
                    }
                    "/settings/-/notifications/topics" -> {
                        topics.incrementAndGet()
                        MockResponse().setBody(topicRows)
                    }
                    "/settings/-/notifications/topics/set/category" -> MockResponse().setBody("""{"ok": true}""")
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }

                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            sockets.add(webSocket)
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

    /** A row as the list answers it, a 10 s later than b 20, c 30. */
    private fun row(id: String, read: Long, created: Long = (id.first() - 'a' + 1) * 10L) =
        """{"id": "$id", "app": "feeds", "topic": "post", "object": "f1", "title": "$id", "created": $created, "read": $read}"""

    /** A new frame for row [id] whose list shows it on [devices], a JSON list, or on every device. */
    private fun arrival(
        id: String,
        created: Long,
        devices: String,
        title: String = id.uppercase(),
        count: Int = 1,
        everywhere: Boolean = false,
    ) =
        """{"type": "new", "id": "$id", "app": "feeds", "topic": "post", "object": "f1", "title": "$title",
            "content": "$title: body", "link": "/feeds/f1", "sender": "s1", "count": $count, "created": $created,
            "read": 0, "web": true, "everywhere": $everywhere, "devices": $devices}"""

    /** This phone's own id, as it sends it in its Device header. */
    private val device by lazy { DeviceStore(context).id() }

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
            DeviceStore(context),
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

    /** [text] sent down every open socket, as the server sends a frame. */
    private fun frame(text: String) = sockets.forEach { it.send(text) }

    /** Row [id] marked read and the read answered, with the screen settled. */
    private fun answered(id: String) {
        open()
        until { sockets.isNotEmpty() }
        model.markRead(id)
        gate.countDown()
        until { reads.contains(id) }
        settle()
    }

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

    @Test
    fun `the echo of an own read fetches no list`() {
        answered("a")
        val before = lists.get()
        frame("""{"type": "read", "id": "a", "app": "feeds"}""")
        settle()
        assertEquals("THE POINT: the list already shows the read", before, lists.get())
        assertTrue(read("a") != 0L)
        assertEquals(1, model.uiState.value.unreadCount)
    }

    @Test
    fun `another device's read is applied to the list, which is not fetched`() {
        answered("a")
        val before = lists.get()
        frame("""{"type": "read", "id": "b", "app": "feeds"}""")
        until { read("b") != 0L }
        settle()
        assertEquals("THE POINT: the frame said which row was read", before, lists.get())
        assertEquals(0, model.uiState.value.unreadCount)
        assertEquals(100L, read("c"))
    }

    @Test
    fun `a read of every row is applied to the list, which is not fetched`() {
        listening()
        val before = lists.get()
        frame("""{"type": "read_all"}""")
        until { model.uiState.value.unreadCount == 0 }
        settle()
        assertEquals(before, lists.get())
        assertTrue(read("a") != 0L && read("b") != 0L)
        // Read before, so it keeps the time it was read.
        assertEquals(100L, read("c"))
    }

    @Test
    fun `a clear of every row empties the list, which is not fetched`() {
        listening()
        val before = lists.get()
        frame("""{"type": "clear_all"}""")
        until { model.uiState.value.items.isEmpty() }
        settle()
        assertEquals(before, lists.get())
        assertEquals(0, model.uiState.value.unreadCount)
    }

    @Test
    fun `an object's clear that changed nothing leaves the list as it is`() {
        listening()
        val before = lists.get()
        frame("""{"type": "clear_object", "app": "feeds", "object": "x", "changed": 0}""")
        settle()
        assertEquals("THE POINT: sent on every open of a feed, it fetched the list each time", before, lists.get())
        assertEquals(3, model.uiState.value.items.size)
        assertEquals(2, model.uiState.value.unreadCount)
    }

    @Test
    fun `frames the list cannot be patched from fetch it`() {
        listening()
        for (text in listOf(
            // Read or deleted: the frame does not say which.
            """{"type": "clear_object", "app": "feeds", "object": "x", "changed": 2}""",
            // A server that predates changed.
            """{"type": "clear_object", "app": "feeds", "object": "x"}""",
            // No title, and nothing to say whether this device lists it.
            """{"type": "new", "id": "d", "app": "feeds", "content": "d", "created": 200, "read": 0, "web": true}""",
            """{"type": "categories"}""",
            """{"type": "read", "app": "feeds"}""",
        )) {
            val before = lists.get()
            frame(text)
            until { lists.get() > before && !model.uiState.value.isRefreshing }
            settle()
        }
    }

    @Test
    fun `a new row this device lists goes into the list from its frame, where its time puts it`() {
        listening()
        val before = lists.get()
        frame(arrival("d", 25, """["another-device", "$device"]"""))
        until { model.uiState.value.items.size == 4 }
        settle()
        assertEquals("THE POINT: the frame named this device, so the row was taken from it", before, lists.get())
        assertEquals(listOf("a", "b", "d", "c"), model.uiState.value.items.map { it.id })
        val row = model.uiState.value.items.single { it.id == "d" }
        assertEquals("D", row.title)
        assertEquals("D: body", row.content)
        assertEquals("/feeds/f1", row.link)
        assertEquals(3, model.uiState.value.unreadCount)
    }

    @Test
    fun `a changed row replaces the one held, where its new time puts it`() {
        listening()
        val before = lists.get()
        frame(arrival("a", 300, """["$device"]""", title = "Again", count = 2))
        until { model.uiState.value.items.lastOrNull()?.id == "a" }
        settle()
        assertEquals(before, lists.get())
        assertEquals(listOf("b", "c", "a"), model.uiState.value.items.map { it.id })
        assertEquals("Again", model.uiState.value.items.last().title)
        assertEquals(2, model.uiState.value.items.last().count)
        // Unread before and after, so the count stays.
        assertEquals(2, model.uiState.value.unreadCount)
    }

    @Test
    fun `a row every device lists goes into the list from its frame`() {
        listening()
        val before = lists.get()
        frame(arrival("d", 25, "[]", everywhere = true))
        until { model.uiState.value.items.size == 4 }
        settle()
        assertEquals("THE POINT: a row of a topic with no category shows everywhere, so it was taken from the frame", before, lists.get())
        assertEquals(listOf("a", "b", "d", "c"), model.uiState.value.items.map { it.id })
    }

    @Test
    fun `a row sharing a second with one held goes where its id puts it, as the server lists them`() {
        listening()
        frame(arrival("ab", 20, """["$device"]"""))
        until { model.uiState.value.items.size == 4 }
        settle()
        assertEquals(listOf("a", "ab", "b", "c"), model.uiState.value.items.map { it.id })
    }

    @Test
    fun `a new row whose frame leaves this device out fetches the list`() {
        listening()
        val before = lists.get()
        // Filtered from this phone's list, or a server that does not know the
        // phone and so filters nothing: the frame cannot say which.
        frame(arrival("d", 200, """["another-device"]"""))
        until { lists.get() > before && !model.uiState.value.isRefreshing }
        settle()
        assertEquals(3, model.uiState.value.items.size)
    }

    @Test
    fun `frames read the list only, and the categories and topics once, as the screen opens`() {
        listening()
        assertEquals(1, categories.get())
        assertEquals(1, topics.get())
        val before = lists.get()
        for (text in listOf(
            arrival("d", 200, """["$device"]"""),
            """{"type": "clear_object", "app": "feeds", "object": "x", "changed": 2}""",
            """{"type": "read", "id": "a", "app": "feeds"}""",
            arrival("e", 210, """["another-device"]"""),
            """{"type": "new", "id": "f", "app": "feeds", "topic": "post", "object": "f1", "created": 220, "read": 0, "web": true}""",
        )) {
            frame(text)
            settle()
        }
        until { lists.get() > before && !model.uiState.value.isRefreshing }
        settle()
        assertEquals("THE POINT: a frame about a row read the topics again", 1, topics.get())
        assertEquals(1, categories.get())
    }

    @Test
    fun `a row of a topic the picker has not read reads the topics for it`() {
        listening()
        topicRows = """[{"app": {"id": "feeds", "name": "Feeds"}, "topic": "post", "object": "f1", "category": "1"},
            {"app": {"id": "chat", "name": "Chat"}, "topic": "message", "object": "c1", "category": "1"}]"""
        frame("""{"type": "new", "id": "g", "app": "chat", "topic": "message", "object": "c1", "title": "G",
            "created": 400, "read": 0, "web": true, "devices": ["$device"]}""")
        until { model.uiState.value.topics.size == 2 }
        assertEquals(2, topics.get())
    }

    @Test
    fun `a change to the categories reads the list, the categories and the topics again`() {
        listening()
        val before = lists.get()
        frame("""{"type": "categories"}""")
        until { lists.get() > before && categories.get() == 2 && topics.get() == 2 }
    }

    @Test
    fun `a topic moved to another category reads the list and the picker again, with no frame to say so`() {
        listening()
        val topic = model.uiState.value.topics.single()
        val before = lists.get()
        model.setCategory(topic, "2")
        until { lists.get() > before && categories.get() == 2 && topics.get() == 2 }
    }

    @Test
    fun `a reopened socket fetches the list, as frames may have been missed`() {
        listening()
        val before = lists.get()
        socket.background()
        socket.foreground()
        until { lists.get() > before && !model.uiState.value.isRefreshing }
    }

    @Test
    fun `a list fetched while a frame landed is fetched again rather than undo it`() {
        listening()
        val gate = CountDownLatch(1)
        held = gate
        val before = lists.get()
        model.refresh()
        until { lists.get() > before }
        // The server reads b before the list answers, after the list was read.
        held = null
        listed = listOf(row("a", 0), row("b", 300), row("c", 100))
        frame("""{"type": "read", "id": "b", "app": "feeds"}""")
        until { read("b") != 0L }
        gate.countDown()
        until { lists.get() > before + 1 && !model.uiState.value.isRefreshing }
        settle()
        assertEquals("THE POINT: the answer from before the frame did not undo it", 300L, read("b"))
        assertEquals(1, model.uiState.value.unreadCount)
    }

    /** The screen open with its socket connected, and the screen settled. */
    private fun listening() {
        open()
        until { sockets.isNotEmpty() }
        settle()
    }
}
