// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.push.VisibleEntity
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.chat.api.ChatApi
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.data.UnreadChatsStore
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.repository.ChatRepository
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
 * The chat's socket frames are applied to the messages held, as the web
 * applies them to its cache: a message, a reaction, an edit or a delete
 * changes the list without the newest page being asked for again. Only what
 * a frame cannot say is read from the server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatSocketTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var api: ChatApi

    /** How often the main timeline's newest page, a thread's replies, and the read mark were asked for. */
    private val timeline = AtomicInteger()
    private val replies = AtomicInteger()
    private val reads = AtomicInteger()

    /** The server's end of the chat's socket, once it is open. */
    @Volatile private var peer: WebSocket? = null

    /** What the server holds of m1 and r1, which a test changes as a frame says it changed. */
    @Volatile private var hello = "Hello"
    @Volatile private var edited = 0
    @Volatile private var withdrawn = false

    /** Held, the next answer to a messages read is written and then waits for it. */
    @Volatile private var hold: CountDownLatch? = null

    /** The answer, written now and sent once any [hold] is let go: a read that lands late. */
    private fun answered(data: String): MockResponse {
        val response = ok(data)
        val gate = hold
        hold = null
        gate?.await(5, TimeUnit.SECONDS)
        return response
    }

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val thread = request.requestUrl?.queryParameter("thread")
                return when {
                    path == "/chat/c1/-/view" -> ok("""{"chat": {"id": "c1", "key": "k1", "name": "Chat"}, "identity": "a1"}""")
                    path == "/chat/c1/-/messages" && thread == "m2" -> {
                        replies.incrementAndGet()
                        val reply = if (withdrawn) "\"body\": \"\", \"deleted\": true" else "\"body\": \"First reply\""
                        answered(
                            """{"messages": [
                                {"id": "r1", "chat": "c1", "member": "b1", "name": "Bea", $reply, "created": 150, "thread": "m2"}
                            ], "more": false}""",
                        )
                    }
                    path == "/chat/c1/-/messages" -> {
                        timeline.incrementAndGet()
                        answered(
                            """{"messages": [
                                {"id": "m1", "chat": "c1", "member": "b1", "name": "Bea", "body": "$hello", "created": 100, "edited": $edited,
                                 "reactions": {"like": 1}, "reaction": "like"},
                                {"id": "m2", "chat": "c1", "member": "a1", "name": "Al", "body": "Mine", "created": 200,
                                 "edited": 50, "replies": 1, "latest": 150, "reply": "m1",
                                 "answers": [{"id": "r1", "chat": "c1", "member": "b1", "name": "Bea", "body": "First reply", "created": 150, "thread": "m2"}],
                                 "attachments": [{"id": "f1", "name": "a.png", "size": 3, "type": "image/png"}],
                                 "reactions": {"love": 2}, "reaction": "love"}
                            ], "more": false}""",
                        )
                    }
                    path == "/chat/c1/-/read" -> {
                        reads.incrementAndGet()
                        ok("""{"read": 0}""")
                    }
                    path == "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            peer = webSocket
                        }

                        // Answer the client's close, or the server waits on it.
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                    request.method == "POST" -> ok("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        api = Retrofit.Builder()
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
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /** Runs the main looper the network's answers and the frames resume on until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets everything already under way finish, a debounced fetch included. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    /** A chat, loaded, with its socket open and settled. */
    private fun open(): ChatViewModel {
        val model = ChatViewModel(
            SavedStateHandle(mapOf("chatId" to "c1")),
            ChatRepository(api, FileStore(context), context),
            socket,
            session,
            PinnedChatsStore(context),
            context,
            UnreadChatsStore(context),
        )
        until { model.uiState.value.messages.isNotEmpty() && socket.isLive("k1") && peer != null }
        settle()
        return model
    }

    private fun send(frame: String) {
        check(peer!!.send(frame)) { "frame not sent" }
    }

    private fun ChatViewModel.message(id: String): ChatMessage? =
        uiState.value.messages.find { message -> message.id == id }

    @Test
    fun `a message from the socket joins the list without asking for the newest page`() {
        val model = open()
        val fetched = timeline.get()
        send(
            """{"id": "m3", "created": 300, "member": "b1", "name": "Bea", "body": "New", "reply": "m1", "edited": 0,
               "attachments": [{"id": "f2", "name": "b.pdf", "size": 10, "type": "application/pdf", "created": 300, "caption": ""},
                               {"id": "f3"}],
               "quote": {"network": "mastodon", "url": "https://example.org/1", "text": "Quoted"},
               "thread": "", "encrypted": 0}""",
        )
        until { model.message("m3") != null }
        // The same frame again, and an echo of a message already held: neither is added twice.
        send("""{"id": "m3", "created": 300, "member": "b1", "name": "Bea", "body": "New", "thread": ""}""")
        send("""{"id": "m2", "created": 200, "member": "a1", "name": "Al", "body": "Mine", "thread": ""}""")
        settle()
        val messages = model.uiState.value.messages
        assertEquals(listOf("m1", "m2", "m3"), messages.map { message -> message.id })
        val added = messages.last()
        assertEquals("New", added.body)
        assertEquals("b1", added.member)
        assertEquals("Bea", added.name)
        assertEquals("c1", added.chat)
        assertEquals("m1", added.reply)
        assertEquals("the entry without a name, size or type is left out", listOf("b.pdf"), added.attachments.map { file -> file.name })
        assertEquals("mastodon", added.quote?.network)
        assertEquals("Quoted", added.quote?.text)
        assertEquals("absent and 0 are both unencrypted", 0, added.encrypted)
        assertEquals("the echo kept the held row", "f1", model.message("m2")?.attachments?.single()?.id)
        assertEquals("no fetch", fetched, timeline.get())
    }

    @Test
    fun `a message arriving out of order takes its place by time`() {
        val model = open()
        send("""{"id": "m0", "created": 150, "member": "b1", "name": "Bea", "body": "Late", "quote": null}""")
        until { model.message("m0") != null }
        assertEquals(listOf("m1", "m0", "m2"), model.uiState.value.messages.map { message -> message.id })
        assertNull(model.message("m0")?.quote)
    }

    @Test
    fun `an encrypted message whose keys have not come shows as such`() {
        val model = open()
        send("""{"id": "m4", "created": 400, "member": "b1", "name": "Bea", "body": "", "attachments": [], "encrypted": 1}""")
        until { model.message("m4") != null }
        val message = model.message("m4")!!
        assertEquals(1, message.encrypted)
        assertTrue(awaitingKeys(message))
    }

    @Test
    fun `a reply in a thread joins the open thread and counts on its root, not the timeline`() {
        val model = open()
        model.openThread(model.message("m2")!!)
        until { model.uiState.value.thread?.replies?.isNotEmpty() == true }
        settle()
        val fetched = timeline.get()
        val read = replies.get()
        send("""{"id": "r2", "created": 250, "member": "b1", "name": "Bea", "body": "Second reply", "thread": "m2"}""")
        until { model.uiState.value.thread?.replies?.any { reply -> reply.id == "r2" } == true }
        // The echo of a reply the open thread holds is not counted twice.
        send("""{"id": "r2", "created": 250, "member": "b1", "name": "Bea", "body": "Second reply", "thread": "m2"}""")
        settle()
        assertEquals(listOf("r1", "r2"), model.uiState.value.thread?.replies?.map { reply -> reply.id })
        assertNull("not on the timeline", model.message("r2"))
        assertEquals(2, model.message("m2")?.replies)
        assertEquals(250L, model.message("m2")?.latest)
        // With the thread closed, a reply is still counted.
        model.closeThread()
        send("""{"id": "r3", "created": 260, "member": "b1", "name": "Bea", "body": "Third reply", "thread": "m2"}""")
        until { model.message("m2")?.replies == 3 }
        settle()
        assertNull(model.message("r3"))
        assertEquals(260L, model.message("m2")?.latest)
        assertEquals("no fetch", fetched, timeline.get())
        assertEquals("no thread fetch", read, replies.get())
    }

    @Test
    fun `reaction counts from the socket replace the held ones and keep the user's own`() {
        val model = open()
        val fetched = timeline.get()
        send("""{"event": "reaction", "message": "m1", "reactions": {"like": 2, "laugh": 1, "sad": 0}}""")
        until { model.message("m1")?.reactions?.get("like") == 2 }
        settle()
        assertEquals(mapOf("like" to 2, "laugh" to 1), model.message("m1")?.reactions)
        assertEquals("like", model.message("m1")?.reaction)
        assertEquals("no fetch", fetched, timeline.get())
    }

    @Test
    fun `the user's own reaction made on another device comes with the counts`() {
        val model = open()
        // Another member's reaction leaves the user's own as it was.
        send("""{"event": "reaction", "message": "m2", "reactions": {"love": 2, "like": 1}, "member": "b1", "reaction": "like"}""")
        until { model.message("m2")?.reactions?.get("like") == 1 }
        assertEquals("love", model.message("m2")?.reaction)
        // The user took theirs away elsewhere.
        send("""{"event": "reaction", "message": "m2", "reactions": {"love": 1, "like": 1}, "member": "a1", "reaction": null}""")
        until { model.message("m2")?.reactions?.get("love") == 1 }
        assertNull(model.message("m2")?.reaction)
        send("""{"event": "reaction", "message": "m2", "reactions": {"like": 2}, "member": "a1", "reaction": "like"}""")
        until { model.message("m2")?.reactions?.get("like") == 2 }
        assertEquals("like", model.message("m2")?.reaction)
    }

    @Test
    fun `a read of the newest page that a frame lands during is read again, not shown over the frame`() {
        val model = open()
        val fetched = timeline.get()
        hold = CountDownLatch(1)
        val gate = hold!!
        model.refresh()
        until { timeline.get() == fetched + 1 }
        // The server takes an edit after writing that answer, and says so.
        hello = "Changed"
        edited = 60
        send("""{"event": "edit", "message": "m1", "body": "Changed", "edited": 60}""")
        until { model.message("m1")?.body == "Changed" }
        gate.countDown()
        until { timeline.get() == fetched + 2 }
        settle()
        assertEquals("Changed", model.message("m1")?.body)
        assertEquals(60L, model.message("m1")?.edited)
    }

    @Test
    fun `a read of the open thread that a frame lands during is read again, not shown over the frame`() {
        val model = open()
        val asked = replies.get()
        hold = CountDownLatch(1)
        val gate = hold!!
        model.openThread(model.message("m2")!!)
        until { replies.get() == asked + 1 }
        // The reply is deleted after that answer was written, and the frame says so.
        withdrawn = true
        send("""{"event": "delete", "message": "r1"}""")
        settle()
        gate.countDown()
        until { replies.get() == asked + 2 }
        settle()
        assertEquals(true, model.uiState.value.thread?.replies?.find { it.id == "r1" }?.deleted)
    }

    @Test
    fun `an edit from the socket applies only when it is newer than the one held`() {
        val model = open()
        val fetched = timeline.get()
        send("""{"event": "edit", "message": "m2", "body": "Changed", "edited": 60}""")
        until { model.message("m2")?.body == "Changed" }
        // An older edit arriving after it, then a reaction to know it was heard.
        send("""{"event": "edit", "message": "m2", "body": "Stale", "edited": 55}""")
        send("""{"event": "reaction", "message": "m1", "reactions": {"like": 3}}""")
        until { model.message("m1")?.reactions?.get("like") == 3 }
        settle()
        assertEquals("Changed", model.message("m2")?.body)
        assertEquals(60L, model.message("m2")?.edited)
        assertEquals("no fetch", fetched, timeline.get())
    }

    @Test
    fun `a delete from the socket leaves a tombstone`() {
        val model = open()
        val fetched = timeline.get()
        send("""{"event": "delete", "message": "m2"}""")
        until { model.message("m2")?.deleted == true }
        settle()
        val tombstone = model.message("m2")!!
        assertEquals("", tombstone.body)
        assertTrue(tombstone.attachments.isEmpty())
        assertTrue(tombstone.reactions.isEmpty())
        assertNull(tombstone.reaction)
        assertNull(tombstone.reply)
        assertEquals(0, tombstone.replies)
        assertEquals("the others stay", "Hello", model.message("m1")?.body)
        assertEquals("no fetch", fetched, timeline.get())
    }

    @Test
    fun `a reply deleted in the open thread no longer counts on its root`() {
        val model = open()
        model.openThread(model.message("m2")!!)
        until { model.uiState.value.thread?.replies?.isNotEmpty() == true }
        settle()
        val fetched = timeline.get()
        send("""{"event": "delete", "message": "r1"}""")
        until { model.uiState.value.thread?.replies?.single()?.deleted == true }
        // Delivered twice, it is still uncounted once.
        send("""{"event": "delete", "message": "r1"}""")
        settle()
        assertEquals(0, model.message("m2")?.replies)
        assertEquals(0L, model.message("m2")?.latest)
        assertTrue("no reply left in its bubble", model.message("m2")?.answers?.isEmpty() == true)
        assertEquals("no fetch", fetched, timeline.get())
    }

    @Test
    fun `a reply that arrives joins its root's bubble in order, and an edit of it shows there`() {
        // THE POINT: the thread in the bubble follows it with the thread closed.
        val model = open()
        assertEquals(listOf("r1"), model.message("m2")?.answers?.map { it.id })
        val fetched = timeline.get()
        send("""{"id": "r3", "created": 260, "member": "b1", "name": "Bea", "body": "Third reply", "thread": "m2"}""")
        until { model.message("m2")?.answers?.size == 2 }
        // One sent earlier that lands late takes its place by time.
        send("""{"id": "r0", "created": 120, "member": "b1", "name": "Bea", "body": "Sent early", "thread": "m2"}""")
        until { model.message("m2")?.replies == 3 }
        assertEquals(listOf("r0", "r1", "r3"), model.message("m2")?.answers?.map { it.id })
        // Delivered again, it is not counted again.
        send("""{"id": "r3", "created": 260, "member": "b1", "name": "Bea", "body": "Third reply", "thread": "m2"}""")
        settle()
        assertEquals(3, model.message("m2")?.replies)
        send("""{"event": "edit", "message": "r3", "body": "Third reply, edited", "edited": 270}""")
        until { model.message("m2")?.answers?.last()?.body == "Third reply, edited" }
        assertEquals("Mine", model.message("m2")?.body)
        assertEquals("no fetch", fetched, timeline.get())
    }

    @Test
    fun `a reply deleted with its thread closed leaves its root's bubble without a read`() {
        val model = open()
        val fetched = timeline.get()
        send("""{"event": "delete", "message": "r1"}""")
        until { model.message("m2")?.replies == 0 }
        assertTrue(model.message("m2")?.answers?.isEmpty() == true)
        assertEquals(0L, model.message("m2")?.latest)
        settle()
        assertEquals("no fetch", fetched, timeline.get())
    }

    @Test
    fun `a message deleted that is held nowhere reads the newest page where a thread count shows`() {
        val model = open()
        val fetched = timeline.get()
        send("""{"event": "delete", "message": "elsewhere"}""")
        until { timeline.get() > fetched }
    }

    @Test
    fun `a decrypted message is read again`() {
        val model = open()
        val fetched = timeline.get()
        send("""{"event": "decrypted", "message": "m1"}""")
        until { timeline.get() > fetched }
        assertEquals(2, model.uiState.value.messages.size)
    }

    @Test
    fun `a message frame without an id or a time reads the newest page instead`() {
        val model = open()
        val fetched = timeline.get()
        send("""{"created": 300, "member": "b1", "name": "Bea", "body": "No id"}""")
        until { timeline.get() > fetched }
        settle()
        val later = timeline.get()
        send("""{"id": "m5", "member": "b1", "name": "Bea", "body": "No time"}""")
        until { timeline.get() > later }
        settle()
        assertEquals(listOf("m1", "m2"), model.uiState.value.messages.map { message -> message.id })
    }

    @Test
    fun `a message arriving while the chat is on screen is marked read`() {
        val shown = Any()
        VisibleEntity.show(shown, "chat", "c1", marksRead = true, socketKey = "k1")
        try {
            val model = open()
            val before = reads.get()
            // The user's own message, sent from elsewhere, was never unread.
            send("""{"id": "m6", "created": 500, "member": "a1", "name": "Al", "body": "Mine again"}""")
            until { model.message("m6") != null }
            settle()
            assertEquals("not for the user's own", before, reads.get())
            send("""{"id": "m7", "created": 600, "member": "b1", "name": "Bea", "body": "Read me"}""")
            until { reads.get() > before }
        } finally {
            VisibleEntity.hide(shown)
        }
    }

    @Test
    fun `a message arriving while the chat is not on screen is not marked read`() {
        val model = open()
        val before = reads.get()
        send("""{"id": "m7", "created": 600, "member": "b1", "name": "Bea", "body": "Unread"}""")
        until { model.message("m7") != null }
        settle()
        assertEquals(before, reads.get())
        assertFalse(model.uiState.value.messages.isEmpty())
    }
}
