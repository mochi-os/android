// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.chat.api.ChatApi
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.model.ChatReceipt
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.model.Friend
import org.mochios.chat.repository.ChatRepository
import org.mochios.chat.ui.chat.ChatViewModel
import org.mochios.chat.ui.encryption.EncryptionEvent
import org.mochios.chat.ui.encryption.EncryptionViewModel
import org.mochios.chat.ui.encryption.VerificationViewModel
import org.mochios.chat.ui.newchat.NewChatViewModel
import org.mochios.chat.ui.privacy.PrivacyViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * The chat view models against a stand-in server: what each sends for
 * conversations on other networks and for typing, receipts, presence and
 * threads, and what it makes of the answers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksViewModelsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ChatRepository
    private lateinit var session: SessionManager
    private lateinit var socket: MochiWebSocket

    /** Each request the server took: its path, its query and its body. */
    private data class Taken(val path: String, val query: String, val body: String)

    private val requests = ConcurrentLinkedQueue<Taken>()

    /** The conversation the view answers with: its account, network, members and status. */
    @Volatile private var account = ""
    @Volatile private var network = ""
    @Volatile private var members = """[{"id": "me", "name": "Me"}, {"id": "you", "name": "You"}]"""
    @Volatile private var status = "active"
    @Volatile private var key = ""

    /** The server's side of the conversation's websocket, once the client is connected. */
    @Volatile private var frames: WebSocket? = null

    /** How many times recovery was asked to be set up. */
    @Volatile private var setups = 0

    @Volatile private var verifications = """[{"account": "m1", "flow": "f", "person": "@carol:m.example", "room": "!r", "phase": "compare", "started": true, "emoji": ["🐶"], "updated": 1}]"""

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val query = request.path.orEmpty().substringAfter("?", "")
                requests.add(Taken(path, query, request.body.readUtf8()))
                return when (path) {
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            frames = webSocket
                        }
                    })
                    "/chat/c1/-/view" -> ok("""{"chat": {"id": "c1", "key": "$key", "name": "Talk", "status": "$status", "account": "$account", "network": "$network", "members": $members}, "identity": "me"}""")
                    "/chat/c1/-/messages" -> if (query.contains("thread=r")) {
                        ok("""{"messages": [{"id": "t1", "member": "you", "name": "You", "body": "In the thread", "thread": "r", "created": 5}], "more": false, "cursor": null, "receipts": []}""")
                    } else {
                        ok("""{"messages": [{"id": "r", "member": "me", "name": "Me", "body": "Root", "created": 4, "replies": 1}], "more": false, "cursor": null,
                            "receipts": [{"member": "you", "name": "You", "delivered": 10, "displayed": 8}]}""")
                    }
                    "/chat/c1/-/read" -> ok("""{"read": 4}""")
                    "/chat/c1/-/typing" -> ok("""{"sent": true}""")
                    "/chat/c1/-/presence" -> ok("""{"member": "you", "status": "online", "message": "", "seen": 0}""")
                    "/chat/c1/-/send" -> ok("""{"id": "n1"}""")
                    "/chat/c1/-/accept" -> {
                        status = "active"
                        ok("""{"id": "c1"}""")
                    }
                    "/chat/c1/-/decline" -> ok("""{"declined": true}""")
                    "/chat/-/preferences/get" -> ok("""{"policy": "friends", "presence": true, "receipts": true, "typing": true}""")
                    "/chat/-/preferences/set" -> ok("""{}""")
                    "/chat/-/new" -> ok("""{"name": "Me", "friends": [], "accounts": [{"id": "x1", "identifier": "me@xmpp.example", "network": "xmpp"}]}""")
                    "/chat/-/create" -> ok("""{"id": "n9", "name": "", "members": []}""")
                    "/chat/c1/-/encryption" -> ok("""{"account": "m1", "status": {"network": "matrix", "address": "@me:m.example", "device": "D", "fingerprint": "abc", "signing": false}, "people": [{"id": "@carol:m.example", "name": "Carol"}]}""")
                    "/chat/c1/-/encryption/devices" -> ok("""{"devices": [{"id": "7", "fingerprint": "f", "verified": false, "own": false}, {"id": "D", "fingerprint": "g", "own": true}]}""")
                    "/chat/c1/-/encryption/trust" -> ok("""{}""")
                    "/chat/c1/-/encryption/setup" -> {
                        setups++
                        if (setups == 1) ok("""{"password": true}""") else ok("""{"recovery": "KEY one"}""")
                    }
                    "/chat/c1/-/encryption/recover" -> ok("""{"restored": 3}""")
                    "/chat/-/encryption/verifications" -> ok("""{"verifications": $verifications}""")
                    "/chat/-/encryption/act" -> ok("""{}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/chat/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = ChatRepository(retrofit.create(ChatApi::class.java), FileStore(context), context)
        session = SessionManager(context)
        val base = server.url("/").toString().trimEnd('/')
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base
            override suspend fun token(app: String): String? = null
            override suspend fun invalidate(app: String) {}
        })
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun idle(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)
    }

    private fun until(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            idle(50)
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what; requests ${requests.map { it.path }}" }
            Thread.sleep(10)
        }
    }

    /**
     * As [until], but with the main looper's clock held still, so a typist
     * cannot lapse while it waits: what it waits for must be what makes it so.
     */
    private fun soon(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    /** Let what is in flight land, so a request that was going to be made has been. */
    private fun settle() {
        repeat(10) {
            idle(20)
            Thread.sleep(20)
        }
    }

    private fun count(path: String): Int = requests.count { it.path == path }

    private fun sent(path: String): String? = requests.lastOrNull { it.path == path }?.body

    private fun conversation(): ChatViewModel {
        val model = ChatViewModel(
            SavedStateHandle(mapOf("chatId" to "c1")),
            repository, socket, session, PinnedChatsStore(context), context,
        )
        until("the conversation") { model.uiState.value.chat.id == "c1" }
        return model
    }

    @Test
    fun `the newest page's receipts are kept, and a receipt only moves a member's watermarks forward`() {
        val model = conversation()
        assertEquals(listOf(ChatReceipt("you", "You", 10, 8)), model.uiState.value.receipts)
        model.received("you", delivered = 5, displayed = 3)
        assertEquals(ChatReceipt("you", "You", 10, 8), model.uiState.value.receipts.single())
        model.received("you", delivered = 20, displayed = 15)
        assertEquals(ChatReceipt("you", "You", 20, 15), model.uiState.value.receipts.single())
        model.received("new", delivered = 1, displayed = 0)
        assertEquals(ChatReceipt("new", "", 1, 0), model.uiState.value.receipts.last())
    }

    @Test
    fun `typing goes out as the text grows, at most once an interval`() {
        val model = conversation()
        model.setDraft("h")
        until("the typing") { count("/chat/c1/-/typing") == 1 }
        model.setDraft("he")
        settle()
        assertEquals(1, count("/chat/c1/-/typing"))
        idle(4_100)
        model.setDraft("h")
        settle()
        assertEquals(1, count("/chat/c1/-/typing"))
        model.setDraft("hel")
        until("the second typing") { count("/chat/c1/-/typing") == 2 }
    }

    @Test
    fun `a conversation on a network without typing tells no one`() {
        account = "f1"
        network = "fediverse"
        val model = conversation()
        model.setDraft("h")
        settle()
        assertEquals(0, count("/chat/c1/-/typing"))
    }

    @Test
    fun `who is typing shows for the window, longer where the event holds it, and goes when they stop`() {
        val model = conversation()
        model.typed("you", "You", stopped = false, held = null)
        assertEquals(listOf("You"), model.uiState.value.typing)
        idle(8_100)
        assertEquals(emptyList<String>(), model.uiState.value.typing)
        model.typed("you", "You", stopped = false, held = 30.0)
        idle(8_100)
        assertEquals(listOf("You"), model.uiState.value.typing)
        model.typed("you", "", stopped = true, held = null)
        assertEquals(emptyList<String>(), model.uiState.value.typing)
        // The user's own typing, echoed back, is not shown.
        model.typed("me", "Me", stopped = false, held = null)
        assertEquals(emptyList<String>(), model.uiState.value.typing)
    }

    @Test
    fun `the websocket's typing, receipts, decrypted messages and thread replies reach the conversation`() {
        key = "k1"
        val model = conversation()
        until("the websocket") { frames != null }
        frames!!.send("""{"event": "typing", "member": "you", "name": "You", "at": 1, "typing": true, "held": 30}""")
        until("the typist") { model.uiState.value.typing == listOf("You") }
        frames!!.send("""{"event": "typing", "member": "you", "name": "You", "typing": false}""")
        soon("the stop") { model.uiState.value.typing.isEmpty() }
        frames!!.send("""{"event": "receipt", "member": "you", "delivered": 40, "displayed": 30}""")
        until("the receipt") { model.uiState.value.receipts.single().displayed == 30L }
        frames!!.send("""{"event": "typing", "member": "you", "name": "You", "at": 1, "held": 30}""")
        soon("the second typist") { model.uiState.value.typing == listOf("You") }
        model.openThread(model.uiState.value.messages.single())
        until("the replies") { model.uiState.value.thread?.isLoading == false }
        val threads = requests.count { it.query.contains("thread=r") }
        val pages = requests.count { it.path == "/chat/c1/-/messages" && !it.query.contains("thread=") }
        frames!!.send("""{"id": "t2", "member": "you", "name": "You", "body": "More", "created": 6, "thread": "r"}""")
        // Their message ends their typing, held as it was for thirty seconds.
        soon("the message to end the typing") { model.uiState.value.typing.isEmpty() }
        until("the thread read again") { requests.count { it.query.contains("thread=r") } > threads }
        until("the timeline read again") { requests.count { it.path == "/chat/c1/-/messages" && !it.query.contains("thread=") } > pages }
        val read = requests.count { it.path == "/chat/c1/-/messages" && !it.query.contains("thread=") }
        frames!!.send("""{"event": "decrypted", "message": "r"}""")
        until("the decrypted message read again") { requests.count { it.path == "/chat/c1/-/messages" && !it.query.contains("thread=") } > read }
    }

    @Test
    fun `a one-to-one conversation asks whether the other person is online`() {
        val model = conversation()
        val watch = CoroutineScope(Dispatchers.Main).launch { model.watchPresence() }
        until("the presence") { model.uiState.value.presence != null }
        watch.cancel()
        assertEquals("online", model.uiState.value.presence?.status)
    }

    @Test
    fun `a group asks no one's presence`() {
        members = """[{"id": "me", "name": "Me"}, {"id": "you", "name": "You"}, {"id": "third", "name": "Third"}]"""
        val model = conversation()
        val watch = CoroutineScope(Dispatchers.Main).launch { model.watchPresence() }
        settle()
        watch.cancel()
        assertEquals(0, count("/chat/c1/-/presence"))
        assertNull(model.uiState.value.presence)
    }

    @Test
    fun `a thread is read under its message, and a reply goes into it`() {
        val model = conversation()
        model.openThread(model.uiState.value.messages.single())
        until("the replies") { model.uiState.value.thread?.replies?.isNotEmpty() == true }
        assertEquals("In the thread", model.uiState.value.thread?.replies?.single()?.body)
        model.setThreadDraft("Hi")
        model.sendThreadReply()
        until("the reply") { sent("/chat/c1/-/send") != null }
        assertEquals("body=Hi&thread=r", sent("/chat/c1/-/send"))
        until("the draft") { model.uiState.value.thread?.draft == "" }
    }

    @Test
    fun `a message on the main timeline goes out with no thread`() {
        val model = conversation()
        model.setDraft("Hello")
        model.sendMessage()
        until("the message") { sent("/chat/c1/-/send") != null }
        assertEquals("body=Hello", sent("/chat/c1/-/send"))
    }

    @Test
    fun `an invitation accepted joins the room and the conversation opens`() {
        status = "invited"
        val model = conversation()
        assertEquals(ChatStatus.INVITED, model.uiState.value.chat.status)
        model.accept()
        until("the join") { count("/chat/c1/-/accept") == 1 }
        until("the conversation") { model.uiState.value.chat.status == ChatStatus.ACTIVE && !model.uiState.value.answering }
    }

    @Test
    fun `an invitation declined takes the conversation away`() {
        status = "invited"
        val model = conversation()
        model.decline()
        until("the decline") { model.uiState.value.chatDeleted }
        assertEquals(1, count("/chat/c1/-/decline"))
    }

    @Test
    fun `privacy saves only what changed`() {
        val model = PrivacyViewModel(repository)
        until("the settings") { model.uiState.value.loaded != null }
        model.change(model.uiState.value.value.copy(typing = false))
        model.save()
        until("the save") { model.uiState.value.saved }
        assertEquals("typing=false", sent("/chat/-/preferences/set"))
    }

    @Test
    fun `privacy unchanged saves nothing`() {
        val model = PrivacyViewModel(repository)
        until("the settings") { model.uiState.value.loaded != null }
        model.save()
        until("the close") { model.uiState.value.saved }
        assertEquals(0, count("/chat/-/preferences/set"))
    }

    @Test
    fun `a conversation on another network is started through the account chosen, with the addresses given`() {
        val model = NewChatViewModel(SavedStateHandle(), repository)
        until("the accounts") { model.uiState.value.accounts.isNotEmpty() }
        model.selectAccount("x1")
        model.updateAddresses(" a@x.example, b@y.example ")
        model.updateGroupName(" Pals ")
        model.createRemote()
        until("the chat") { model.uiState.value.createdChatId == "n9" }
        assertEquals("name=Pals&members=a%40x.example%2Cb%40y.example&account=x1", sent("/chat/-/create"))
    }

    @Test
    fun `a messaging address handed over from people starts the conversation at once`() {
        val model = NewChatViewModel(
            SavedStateHandle(mapOf("account" to "x1", "address" to "dave@xmpp.example", "name" to "Dave")),
            repository,
        )
        until("the chat") { model.uiState.value.createdChatId == "n9" }
        assertEquals("name=Dave&members=dave%40xmpp.example&account=x1", sent("/chat/-/create"))
    }

    @Test
    fun `a Mochi chat names no account`() {
        val model = NewChatViewModel(SavedStateHandle(), repository)
        until("the accounts") { model.uiState.value.accounts.isNotEmpty() }
        model.toggleSelect(Friend(id = "friend", name = "Friend"))
        model.createChat("Friend")
        until("the chat") { model.uiState.value.createdChatId == "n9" }
        assertFalse(sent("/chat/-/create")!!.contains("account"))
    }

    private fun encryption(): EncryptionViewModel {
        val model = EncryptionViewModel(SavedStateHandle(mapOf("chatId" to "c1")), repository, context)
        model.load()
        until("the encryption") { model.uiState.value.overview != null }
        return model
    }

    @Test
    fun `a person's devices are read as their row opens, and the user's word on one goes to the server`() {
        val model = encryption()
        model.toggle("@carol:m.example")
        until("the devices") { model.uiState.value.devices["@carol:m.example"]?.isLoading == false }
        assertEquals("person=%40carol%3Am.example", requests.last { it.path == "/chat/c1/-/encryption/devices" }.query)
        model.trust("@carol:m.example", "7", "verified")
        until("the trust") { sent("/chat/c1/-/encryption/trust") != null }
        assertEquals("person=%40carol%3Am.example&device=7&trust=verified", sent("/chat/c1/-/encryption/trust"))
        model.toggle("@carol:m.example")
        assertFalse("@carol:m.example" in model.uiState.value.open)
    }

    @Test
    fun `recovery asks for the password where the server wants it, then shows the key once`() {
        val model = encryption()
        model.openSetup()
        model.setup()
        until("the question") { model.uiState.value.setup?.asked == true }
        assertEquals("", sent("/chat/c1/-/encryption/setup"))
        model.setPassword("pw")
        model.setup()
        until("the key") { model.uiState.value.setup?.recovery == "KEY one" }
        assertEquals("password=pw", sent("/chat/c1/-/encryption/setup"))
    }

    @Test
    fun `a recovery key set up elsewhere restores the history's keys`() {
        val model = encryption()
        val events = mutableListOf<EncryptionEvent>()
        val listen = CoroutineScope(Dispatchers.Main).launch { model.events.collect { events += it } }
        model.openRecovery()
        model.setRecovery("  KEY two  ")
        model.recover()
        until("the restore") { events.isNotEmpty() }
        listen.cancel()
        assertEquals(EncryptionEvent.Restored(3), events.single())
        assertEquals("key=KEY+two", sent("/chat/c1/-/encryption/recover"))
        assertNull(model.uiState.value.recovery)
    }

    @Test
    fun `a verification under way is shown, its steps go to the server, and how it ended is said once`() {
        val model = VerificationViewModel(repository, context)
        val said = mutableListOf<String>()
        val listen = CoroutineScope(Dispatchers.Main).launch { model.events.collect { said += it } }
        runBlocking { model.read() }
        assertEquals("f", model.uiState.value.shown?.flow)
        model.act("confirm")
        until("the step") { sent("/chat/-/encryption/act") != null }
        assertEquals("account=m1&flow=f&action=confirm", sent("/chat/-/encryption/act"))
        verifications = """[{"account": "m1", "flow": "f", "person": "@carol:m.example", "room": "!r", "phase": "done", "updated": 2}]"""
        runBlocking { model.read() }
        runBlocking { model.read() }
        until("the end said") { said.isNotEmpty() }
        settle()
        listen.cancel()
        assertEquals(listOf("Verified"), said)
        assertNull(model.uiState.value.shown)
    }

    @Test
    fun `a verification closed is cancelled and not shown again`() {
        val model = VerificationViewModel(repository, context)
        runBlocking { model.read() }
        model.close()
        until("the cancel") { sent("/chat/-/encryption/act") != null }
        assertEquals("account=m1&flow=f&action=cancel", sent("/chat/-/encryption/act"))
        assertTrue(model.uiState.value.shown == null)
    }
}
