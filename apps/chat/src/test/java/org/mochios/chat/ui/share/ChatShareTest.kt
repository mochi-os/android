// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.share

import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.share.SharedImages
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.chat.R
import org.mochios.chat.api.ChatApi
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.data.UnreadChatsStore
import org.mochios.chat.repository.ChatRepository
import org.mochios.chat.ui.chat.ChatViewModel
import org.mochios.chat.ui.chatlist.ChatListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * What another app shares, sent in a chat: the choice of the user's chats a
 * message can go in, and a new one; and the chosen chat, its message box
 * filled with the share and its images, with nothing sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ChatShareTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: ChatRepository

    /** The path of each request the server took. */
    private val requests = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                requests.add(path)
                return when (path) {
                    "/chat/-/list" -> ok(
                        """[{"id": "c1", "name": "Talk", "members": 2, "updated": 20},
                        {"id": "c2", "name": "Invitation", "members": 3, "status": "invited"},
                        {"id": "c3", "name": "Garden club", "members": 5, "updated": 10}]""",
                    )
                    "/chat/c1/-/view" -> ok(
                        """{"chat": {"id": "c1", "key": "", "name": "Talk", "status": "active",
                            "members": [{"id": "a1", "name": "Al"}, {"id": "b1", "name": "Bea"}]}, "identity": "a1"}""",
                    )
                    "/chat/c1/-/messages" -> ok("""{"messages": [{"id": "m1", "chat": "c1", "member": "b1", "name": "Bea", "body": "Hello", "created": 100}], "more": false}""")
                    "/chat/c1/-/read" -> ok("""{"read": 1}""")
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

            override suspend fun token(app: String): String? = null

            override suspend fun invalidate(app: String) {
            }
        })
        repository = ChatRepository(api, FileStore(context), context)
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what; requests $requests" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `the choice lists the chats a message can go in, and the chosen one or a new chat is handed on`() {
        val model = ChatListViewModel(repository, PinnedChatsStore(context, session), UnreadChatsStore(context, session), socket, session)
        val chosen = mutableListOf<String>()
        var started = 0
        rule.setContent {
            ChatShareScreen(onBack = {}, onChoose = { chosen += it }, onNewChat = { started++ }, viewModel = model)
        }
        rule.waitUntil(5_000) {
            rule.onAllNodes(isRoot()).fetchSemanticsNodes()
            rule.onAllNodesWithText("Talk").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Garden club").assertExists()
        // An invitation not yet accepted takes no message.
        assertEquals(0, rule.onAllNodesWithText("Invitation").fetchSemanticsNodes().size)
        rule.onNodeWithText("Talk").performClick()
        rule.onNodeWithText(context.getString(R.string.chat_list_new)).performClick()
        rule.waitForIdle()
        assertEquals(listOf("c1"), chosen)
        assertEquals(1, started)
    }

    @Test
    fun `the chosen chat opens with the share and its images in its message box, and nothing is sent`() {
        val image = Uri.parse("content://photos.example/1")
        shadowOf(context.contentResolver).registerInputStream(image, ByteArrayInputStream("pixels".toByteArray()))
        val token = SharedImages.hold(context, listOf(image))
        val message = "Books\nhttps://here.example/forums/fp1"
        val store = ViewModelStore()
        val model = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
                SavedStateHandle(mapOf("chatId" to "c1", "message" to message, "images" to token)),
                repository,
                socket,
                session,
                PinnedChatsStore(context, session),
                context,
                UnreadChatsStore(context, session),
            ) as T
        })[ChatViewModel::class.java]
        until("the conversation") { model.uiState.value.messages.isNotEmpty() }
        repeat(15) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
        assertEquals(message, model.uiState.value.draft)
        val attached = model.uiState.value.pendingAttachments.single()
        assertEquals("pixels", context.contentResolver.openInputStream(attached)!!.use { it.readBytes().decodeToString() })
        assertTrue("nothing is sent: $requests", requests.none { it.endsWith("/-/send") })
        // The chat's screen gone, its copies of the images go too.
        store.clear()
        assertEquals(emptyList<String>(), SharedImages.folder(context).list().orEmpty().filter { it.startsWith(token) })
    }

    @Test
    fun `a chat opened without a share opens with an empty message box`() {
        val model = ChatViewModel(
            SavedStateHandle(mapOf("chatId" to "c1")),
            repository,
            socket,
            session,
            PinnedChatsStore(context, session),
            context,
            UnreadChatsStore(context, session),
        )
        until("the conversation") { model.uiState.value.messages.isNotEmpty() }
        assertEquals("", model.uiState.value.draft)
        assertEquals(emptyList<Uri>(), model.uiState.value.pendingAttachments)
    }
}
