// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.Downloads
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.attachments.AttachmentsScreen
import org.mochios.wikis.ui.attachments.AttachmentsViewModel
import org.mochios.wikis.ui.changes.ChangesListScreen
import org.mochios.wikis.ui.changes.ChangesViewModel
import org.mochios.wikis.ui.comments.CommentsScreen
import org.mochios.wikis.ui.comments.CommentsViewModel
import org.mochios.wikis.ui.history.PageHistoryScreen
import org.mochios.wikis.ui.history.PageHistoryViewModel
import org.mochios.wikis.ui.history.RevisionViewModel
import org.mochios.wikis.ui.history.RevisionViewScreen
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Each screen beside the page loads the wiki's information and its own content
 * in one go, and its Retry asks again for whichever failed: an information
 * request that failed once used to leave the screen on a spinner for good, the
 * content's retry never asking for it again. A content load that succeeds
 * with nothing in it shows the screen's empty state, not the spinner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikiLoadTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: WikisRepository

    /** How many times each path has been asked for. */
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** How many more times the information request fails. */
    private val failing = AtomicInteger(1)

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                return when (path) {
                    "/wikis/w1/-/info" ->
                        if (failing.getAndUpdate { left -> if (left > 0) left - 1 else 0 } > 0) {
                            MockResponse().setResponseCode(503).setBody("""{"error": "Try again later"}""")
                        } else {
                            ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki"}, "permissions": {"view": true, "edit": true}}""")
                        }
                    "/wikis/w1/-/pages/home" -> ok("""{"page": {"id": "p1", "page": "home", "title": "Home", "content": "Text"}}""")
                    "/wikis/w1/-/pages/home/comments" -> ok("""{"comments": []}""")
                    "/wikis/w1/-/attachment/list" -> ok("""{"attachments": []}""")
                    "/wikis/w1/-/pages/home/history" -> ok("""{"revisions": [], "total": 0}""")
                    "/wikis/w1/-/pages/home/history/2" ->
                        ok("""{"revision": {"id": "r2", "title": "Second draft", "version": 2, "content": "Body"}, "version": {"current": 2}}""")
                    "/wikis/w1/-/changes" -> ok("""{"changes": [], "total": 0}""")
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                        // Answer the client's close, or the server waits on it.
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
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikisApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = WikisRepository(api, FileStore(context), context)
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun count(path: String): Int = counts[path]?.get() ?: 0

    private fun handle() = SavedStateHandle(mapOf("wikiId" to "w1", "page" to "home", "version" to 2))

    /** Runs the main looper the network's answers resume on until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        rule.waitForIdle()
    }

    /**
     * Shows [screen] while the information request fails, retries from the
     * error it shows, and waits for [shown], the screen's content.
     */
    private fun retried(screen: @Composable () -> Unit, shown: String) {
        rule.setContent { screen() }
        until { count("/wikis/w1/-/info") == 1 && runCatching { rule.onNodeWithText("Retry").assertExists() }.isSuccess }
        rule.onNodeWithText("Try again later").assertExists()
        rule.onNodeWithText("Retry").performClick()
        until { runCatching { rule.onNodeWithText(shown).assertExists() }.isSuccess }
        assertEquals("THE POINT: Retry asked for the information again", 2, count("/wikis/w1/-/info"))
    }

    @Test
    fun `comments ask again for the information, follow the wiki once it lands, and show an empty thread`() {
        val model = CommentsViewModel(handle(), repository, session, socket)
        retried({ CommentsScreen(navController = rememberNavController(), viewModel = model) }, "No comments yet")
        until { socket.isLive("wfp") }
    }

    @Test
    fun `attachments ask again for the information and show an empty page`() {
        val model = AttachmentsViewModel(handle(), repository, session, Downloads(context, FileStore(context)))
        retried({ AttachmentsScreen(navController = rememberNavController(), viewModel = model) }, "No attachments yet")
    }

    @Test
    fun `history asks again for the information and shows an empty history`() {
        val model = PageHistoryViewModel(handle(), repository, session)
        retried({ PageHistoryScreen(navController = rememberNavController(), viewModel = model) }, "No revisions found")
    }

    @Test
    fun `a revision asks again for the information and shows the revision`() {
        val model = RevisionViewModel(handle(), repository, session)
        retried({ RevisionViewScreen(navController = rememberNavController(), viewModel = model) }, "Version 2")
    }

    @Test
    fun `recent changes ask again for the information and show an empty list`() {
        val model = ChangesViewModel(SavedStateHandle(mapOf("wikiId" to "w1")), repository, session)
        retried({ ChangesListScreen(navController = rememberNavController(), viewModel = model) }, "No changes yet")
    }
}
