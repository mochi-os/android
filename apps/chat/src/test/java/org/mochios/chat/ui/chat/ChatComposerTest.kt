// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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
import org.mochios.chat.api.ChatApi
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.data.UnreadChatsStore
import org.mochios.chat.repository.ChatRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The composer and the messages' actions against a stand-in server: what is
 * typed while a send, an edit or a presence question is out stays; a failed
 * send comes back or waits to be sent again; a message too long is never
 * sent; a forward goes once; a delete shows its tombstone at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatComposerTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var api: ChatApi

    /** Each request the server took: its path and its body, decoded. */
    private data class Taken(val path: String, val body: String)

    private val requests = ConcurrentLinkedQueue<Taken>()

    /** Held, the next answer to that path waits for it. */
    @Volatile private var sendGate: CountDownLatch? = null
    @Volatile private var forwardGate: CountDownLatch? = null
    @Volatile private var presenceGate: CountDownLatch? = null

    /** The status a send answers with. */
    @Volatile private var sendStatus = 200

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val query = request.path.orEmpty().substringAfter("?", "")
                requests.add(Taken(path, URLDecoder.decode(request.body.readUtf8(), "UTF-8")))
                return when (path) {
                    "/chat/c1/-/view" -> ok(
                        """{"chat": {"id": "c1", "key": "", "name": "Talk", "status": "active",
                            "members": [{"id": "a1", "name": "Al"}, {"id": "b1", "name": "Bea"}]}, "identity": "a1"}""",
                    )
                    "/chat/c1/-/messages" -> if (query.contains("thread=m1")) {
                        ok("""{"messages": [{"id": "t1", "chat": "c1", "member": "b1", "name": "Bea", "body": "In the thread", "created": 150, "thread": "m1"}], "more": false}""")
                    } else {
                        ok("""{"messages": [{"id": "m1", "chat": "c1", "member": "a1", "name": "Al", "body": "Mine", "created": 100, "replies": 1}], "more": false}""")
                    }
                    "/chat/c1/-/send" -> {
                        sendGate?.await(5, TimeUnit.SECONDS)
                        if (sendStatus == 200) ok("""{"id": "n1"}""")
                        else MockResponse().setResponseCode(sendStatus).setBody("""{"error": "The server could not take it"}""")
                    }
                    "/chat/c1/-/messages/edit" -> ok("""{"id": "m1", "edited": 200}""")
                    "/chat/c1/-/messages/delete" -> ok("""{"deleted": ["m1"]}""")
                    "/chat/c1/-/messages/forward" -> {
                        forwardGate?.await(5, TimeUnit.SECONDS)
                        ok("""{"forwarded": ["m1"], "destination": "c2"}""")
                    }
                    "/chat/c1/-/react" -> ok("""{"reactions": {"like": 1}, "reaction": "like"}""")
                    "/chat/c1/-/presence" -> {
                        presenceGate?.await(5, TimeUnit.SECONDS)
                        ok("""{"member": "b1", "status": "online", "message": "", "seen": 0}""")
                    }
                    "/chat/-/list" -> ok("""[{"id": "c2", "name": "Other", "members": 3, "status": "active"}]""")
                    "/chat/-/new" -> ok("""{"name": "Al", "friends": [], "accounts": []}""")
                    "/chat/c1/-/read" -> ok("""{"read": 1}""")
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

            override suspend fun token(app: String): String? = null

            override suspend fun invalidate(app: String) {
            }
        })
    }

    @After
    fun end() {
        sendGate?.countDown()
        forwardGate?.countDown()
        presenceGate?.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun count(path: String): Int = requests.count { it.path == path }

    private fun sent(path: String): String? = requests.lastOrNull { it.path == path }?.body

    private fun until(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what; requests ${requests.map { it.path }}" }
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(15) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun open(): ChatViewModel {
        val model = ChatViewModel(
            SavedStateHandle(mapOf("chatId" to "c1")),
            ChatRepository(api, FileStore(context), context),
            socket,
            session,
            PinnedChatsStore(context, session),
            context,
            UnreadChatsStore(context, session),
        )
        until("the conversation") { model.uiState.value.messages.isNotEmpty() }
        settle()
        return model
    }

    @Test
    fun `what is typed while a message is sent stays for the next one`() {
        val model = open()
        sendGate = CountDownLatch(1)
        model.setDraft("first")
        model.sendMessage()
        until("the send") { count("/chat/c1/-/send") == 1 }
        assertEquals("the message leaves the composer as it goes", "", model.uiState.value.draft)
        model.setDraft("second")
        sendGate!!.countDown()
        until("the answer") { !model.uiState.value.isSending }
        settle()
        assertEquals("body=first", sent("/chat/c1/-/send"))
        assertEquals("second", model.uiState.value.draft)
        assertTrue(model.uiState.value.unsent.isEmpty())
    }

    @Test
    fun `a failed send comes back into a composer still empty`() {
        val model = open()
        sendStatus = 500
        model.setDraft("lost?")
        model.sendMessage()
        until("the refusal") { count("/chat/c1/-/send") == 1 && !model.uiState.value.isSending }
        assertEquals("lost?", model.uiState.value.draft)
        assertNotNull("said once", model.uiState.value.notice)
        assertTrue(model.uiState.value.unsent.isEmpty())
    }

    @Test
    fun `a failed send waits beside the next message, and goes when retried`() {
        val model = open()
        sendGate = CountDownLatch(1)
        sendStatus = 500
        model.setDraft("first")
        model.sendMessage()
        until("the send") { count("/chat/c1/-/send") == 1 }
        model.setDraft("second")
        sendGate!!.countDown()
        until("the refusal") { !model.uiState.value.isSending }
        assertEquals("the next message stays", "second", model.uiState.value.draft)
        val failed = model.uiState.value.unsent.single()
        assertEquals("first", failed.text)
        assertEquals("The server could not take it", failed.error.message)
        sendGate = null
        sendStatus = 200
        model.retry(failed.key)
        until("the retry") { count("/chat/c1/-/send") == 2 && !model.uiState.value.isSending }
        assertEquals("body=first", sent("/chat/c1/-/send"))
        assertTrue(model.uiState.value.unsent.isEmpty())
        assertEquals("second", model.uiState.value.draft)
    }

    @Test
    fun `a thread reply that failed waits in the thread beside the next one, and goes when retried`() {
        val model = open()
        model.openThread(model.uiState.value.messages.single())
        until("the thread") { model.uiState.value.thread?.isLoading == false }
        sendGate = CountDownLatch(1)
        sendStatus = 500
        model.setThreadDraft("first reply")
        model.sendThreadReply()
        until("the send") { count("/chat/c1/-/send") == 1 }
        model.setThreadDraft("second reply")
        sendGate!!.countDown()
        until("the refusal") { model.uiState.value.thread?.isSending == false }
        assertEquals("second reply", model.uiState.value.thread?.draft)
        val failed = model.uiState.value.thread!!.unsent.single()
        assertEquals("first reply", failed.text)
        sendGate = null
        sendStatus = 200
        model.retryReply(failed.key)
        until("the retry") { count("/chat/c1/-/send") == 2 && model.uiState.value.thread?.isSending == false }
        assertEquals("body=first reply&thread=m1", sent("/chat/c1/-/send"))
        assertEquals("the box keeps the next reply", "second reply", model.uiState.value.thread?.draft)
        assertTrue(model.uiState.value.thread!!.unsent.isEmpty())
    }

    @Test
    fun `a message longer than the server takes is never sent, one at the limit is`() {
        val model = open()
        model.setDraft("😀".repeat(MESSAGE_MAXIMUM + 1))
        model.sendMessage()
        settle()
        assertEquals(0, count("/chat/c1/-/send"))
        assertEquals("the text stays to be cut", MESSAGE_MAXIMUM + 1, length(model.uiState.value.draft))
        model.setDraft("😀".repeat(MESSAGE_MAXIMUM))
        model.sendMessage()
        until("the send") { count("/chat/c1/-/send") == 1 }
    }

    @Test
    fun `an edit leaves the draft, the reply target and the files where they were`() {
        val model = open()
        val root = model.uiState.value.messages.single()
        model.setDraft("half written")
        model.startReply(root)
        model.startEdit(root)
        assertEquals("half written", model.uiState.value.draft)
        assertEquals(root, model.uiState.value.replyingTo)
        assertEquals("Mine", model.uiState.value.composed())
        model.setEditDraft("Mine, fixed")
        model.saveEdit()
        until("the edit") { sent("/chat/c1/-/messages/edit") != null && model.uiState.value.editing == null }
        assertTrue(sent("/chat/c1/-/messages/edit")!!.contains("body=Mine, fixed"))
        assertEquals("half written", model.uiState.value.composed())
        assertEquals(root, model.uiState.value.replyingTo)
        model.startEdit(root)
        model.cancelEdit()
        assertEquals("half written", model.uiState.value.draft)
    }

    @Test
    fun `a forward goes once, however often its destinations are tapped`() {
        val model = open()
        model.openForward("m1")
        until("the destinations") { !model.uiState.value.forwardLoading }
        forwardGate = CountDownLatch(1)
        model.forwardToChat("c2")
        until("the forward") { count("/chat/c1/-/messages/forward") == 1 }
        assertEquals("c2", model.uiState.value.forwarding)
        model.forwardToChat("c2")
        model.forwardToFriend("f1")
        forwardGate!!.countDown()
        until("the answer") { model.uiState.value.forwarding == null }
        settle()
        assertEquals(1, count("/chat/c1/-/messages/forward"))
        assertEquals(0, count("/chat/c1/-/messages/forward/friend"))
        assertTrue(model.uiState.value.forwardMessageIds.isEmpty())
    }

    @Test
    fun `a message deleted shows as its tombstone at once, without the newest page`() {
        val model = open()
        val pages = count("/chat/c1/-/messages")
        model.deleteMessages(listOf("m1"))
        until("the delete") { count("/chat/c1/-/messages/delete") == 1 }
        settle()
        assertTrue("a tombstone", model.uiState.value.messages.single().deleted)
        assertEquals("Mine was emptied", "", model.uiState.value.messages.single().body)
        settle()
        assertEquals("the newest page was not read for it", pages, count("/chat/c1/-/messages"))
    }

    @Test
    fun `a reaction to a reply in the open thread shows in the thread`() {
        val model = open()
        model.openThread(model.uiState.value.messages.single())
        until("the thread") { model.uiState.value.thread?.replies?.isNotEmpty() == true }
        model.react("t1", "like")
        until("the reaction") { count("/chat/c1/-/react") == 1 }
        settle()
        assertEquals("like", model.uiState.value.thread?.replies?.single()?.reaction)
        assertEquals(mapOf("like" to 1), model.uiState.value.thread?.replies?.single()?.reactions)
    }

    @Test
    fun `what is typed while the other person's presence is asked stays`() {
        val model = open()
        presenceGate = CountDownLatch(1)
        val watch = CoroutineScope(Dispatchers.Main).launch { model.watchPresence() }
        try {
            until("the question") { count("/chat/c1/-/presence") == 1 }
            model.setDraft("typed while asking")
            presenceGate!!.countDown()
            until("the presence") { model.uiState.value.presence != null }
            assertEquals("typed while asking", model.uiState.value.draft)
        } finally {
            watch.cancel()
        }
    }

    @Test
    fun `closing the thread ends an edit of one of its replies`() {
        val model = open()
        model.openThread(model.uiState.value.messages.single())
        until("the thread") { model.uiState.value.thread?.replies?.isNotEmpty() == true }
        model.startEdit(model.uiState.value.thread!!.replies.single())
        model.closeThread()
        assertNull(model.uiState.value.editing)
        assertEquals("", model.uiState.value.editDraft)
    }
}
