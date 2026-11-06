// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chatlist

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.chat.R
import org.mochios.chat.api.ChatApi
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.data.UnreadChatsStore
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.repository.ChatRepository
import org.mochios.chat.ui.chat.ChatViewModel
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
 * The chat list's own Mark as read and Mark as unread, as the web list has
 * them: a read clears the badge on the tap, the list reads again once the
 * server has it, and a refusal shows the chat unread again with the error; a
 * mark of unread stays on the device until the chat is read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatListMenuTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var repository: ChatRepository
    private lateinit var session: SessionManager
    private lateinit var model: ChatListViewModel
    private lateinit var unread: UnreadChatsStore

    /** Holds every read until released, so a test sees the list before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the reads it is sent. */
    @Volatile private var refuse = false

    /** Each chat the server has been sent a read of. */
    private val reads = ConcurrentLinkedQueue<String>()

    /** How many times the list has been asked for. */
    private val lists = AtomicInteger()

    /** c1's unread count as the server lists it. */
    @Volatile private var count = 3

    private val events = mutableListOf<ChatListEvent>()
    private var collector: Job? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when {
                    path == "/chat/-/list" -> {
                        lists.incrementAndGet()
                        ok(
                            """[{"id": "c1", "name": "One", "members": 3, "unread": $count},
                            {"id": "c2", "name": "Two", "members": 3, "unread": 0},
                            {"id": "c3", "name": "Three", "members": 3, "unread": 0, "status": "invited"}]""",
                        )
                    }
                    path.endsWith("/-/read") -> {
                        reads.add(path.removePrefix("/chat/").substringBefore("/"))
                        // A chat opened reads at once; the list's reads wait for the test.
                        if (!path.startsWith("/chat/c2/")) gate.await(5, TimeUnit.SECONDS)
                        if (refuse) MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")
                        else ok("""{"read": 1}""")
                    }
                    path == "/chat/c2/-/view" -> ok("""{"chat": {"id": "c2", "key": "k2", "name": "Two"}, "identity": "a1"}""")
                    path == "/chat/c2/-/messages" -> ok("""{"messages": [{"id": "m1", "chat": "c2", "body": "Hello"}], "more": false}""")
                    path == "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
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
            .baseUrl(server.url("/chat/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = ChatRepository(api, FileStore(context), context)
        unread = UnreadChatsStore(context)
        model = ChatListViewModel(repository, PinnedChatsStore(context), unread)
        collector = CoroutineScope(Dispatchers.Main).launch { model.events.collect { events += it } }
        until { model.uiState.value.chats.size == 3 }
    }

    @After
    fun end() {
        gate.countDown()
        collector?.cancel()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun chat(id: String) = model.uiState.value.chats.single { it.id == id }

    private fun unreadIds() = model.filteredChats().map { it.id }

    @Test
    fun `Mark as read clears the badge on the tap, and a refusal shows it again with the error`() {
        model.markRead("c1")
        assertEquals(0, chat("c1").unread)
        until { reads.contains("c1") }

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertTrue(events.single() is ChatListEvent.Failure)
        assertEquals(3, chat("c1").unread)
    }

    @Test
    fun `an accepted Mark as read says so and reads the list again`() {
        val before = lists.get()
        count = 0
        model.markRead("c1")
        gate.countDown()
        until { events.isNotEmpty() && lists.get() > before }
        assertEquals(ChatListEvent.Done(R.string.chat_list_marked_read), events.single())
        assertEquals(0, chat("c1").unread)
    }

    @Test
    fun `Mark as unread puts a dot on the chat and keeps it under Unread`() {
        model.setFilter(ChatFilter.UNREAD)
        assertEquals(listOf("c1"), unreadIds())
        model.markUnread("c2")
        until { events.isNotEmpty() }
        assertEquals(ChatListEvent.Done(R.string.chat_list_marked_unread), events.single())
        assertTrue(unreadDot(chat("c2"), open = "", marked = model.marked.value))
        assertEquals(setOf("c1", "c2"), unreadIds().toSet())
        // The chat on screen shows no dot, as it shows no count.
        assertFalse(unreadDot(chat("c2"), open = "c2", marked = model.marked.value))
    }

    @Test
    fun `Mark as read clears a mark of unread`() {
        model.markUnread("c2")
        model.markRead("c2")
        assertFalse("c2" in model.marked.value)
    }

    @Test
    fun `opening the chat clears a mark of unread`() {
        model.markUnread("c2")
        assertTrue("c2" in unread.marked.value)
        ChatViewModel(
            SavedStateHandle(mapOf("chatId" to "c2")),
            repository,
            socket,
            session,
            PinnedChatsStore(context),
            context,
            unread,
        )
        until { reads.contains("c2") }
        assertFalse("c2" in unread.marked.value)
    }

    @Test
    fun `an invited chat is never shown with a dot`() {
        val invited = Chat(id = "c3", status = ChatStatus.INVITED)
        assertFalse(unreadDot(invited, open = "", marked = setOf("c3")))
    }
}
