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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A search match is brought on screen without reading the whole history: a
 * reply in a thread opens its thread, which the main timeline never holds,
 * and a match of the main timeline not yet loaded is looked for a page at a
 * time, for so many pages and no more.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatSearchTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var api: ChatApi

    /** How many older pages of the main timeline were asked for. */
    private val older = AtomicInteger()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                val cursor = request.requestUrl?.queryParameter("cursor")
                val thread = request.requestUrl?.queryParameter("thread")
                return when (path) {
                    "/chat/c1/-/view" -> ok("""{"chat": {"id": "c1", "key": "", "name": "Talk"}, "identity": "a1"}""")
                    "/chat/c1/-/messages" -> when {
                        thread == "r9" -> ok("""{"messages": [{"id": "t9", "chat": "c1", "member": "b1", "name": "Bea", "body": "a needle", "created": 50, "thread": "r9"}], "more": false}""")
                        cursor == null -> ok("""{"messages": [{"id": "m0", "chat": "c1", "body": "Newest", "created": 100000}], "more": true, "cursor": "99999:m0"}""")
                        else -> {
                            // Always older history, and always more of it.
                            val page = older.incrementAndGet()
                            val created = 100000 - page * 10
                            ok("""{"messages": [{"id": "p$page", "chat": "c1", "body": "Page $page", "created": $created}], "more": true, "cursor": "${created - 1}:p$page"}""")
                        }
                    }
                    "/chat/c1/-/search" -> ok(
                        """{"query": "needle", "results": [
                            {"id": "t9", "created": 50, "thread": "r9"},
                            {"id": "gone", "created": 10, "thread": ""}
                        ], "roots": [{"id": "r9", "chat": "c1", "member": "a1", "name": "Al", "body": "The root", "created": 40}]}""",
                    )
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
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun searched(): ChatViewModel {
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
        model.openSearch()
        model.setSearchQuery("needle")
        until("the matches") { model.uiState.value.searchMatches.size == 2 }
        return model
    }

    @Test
    fun `a reply in a thread opens its thread, under the root the search sent`() {
        val model = searched()
        assertEquals(SearchMatch("t9", "r9"), model.uiState.value.searchMatches.first())
        model.seekMatch()
        settle()
        assertEquals("r9", model.uiState.value.thread?.root?.id)
        assertEquals("The root", model.uiState.value.thread?.root?.body)
        assertEquals(listOf("t9"), model.uiState.value.thread?.replies?.map { it.id })
        assertEquals("the main timeline is not paged for it", 0, older.get())
    }

    @Test
    fun `a match of the main timeline not loaded is looked for so many pages and no more`() {
        val model = searched()
        model.setSearchMatchIndex(1)
        // As the screen asks again each time the messages change.
        repeat(40) {
            model.seekMatch()
            until("the page") { !model.uiState.value.isLoadingMore }
        }
        assertEquals(20, older.get())
        assertEquals(20, model.uiState.value.searchPages)
    }
}
