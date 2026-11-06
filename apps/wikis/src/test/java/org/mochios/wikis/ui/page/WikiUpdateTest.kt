// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.PageFetchResponseDeserializer
import org.mochios.wikis.repository.WikisRepository
import org.mochios.wikis.ui.comments.CommentsViewModel
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
 * An open page reads again only what a change in the wiki concerns: itself
 * when the change is to it, or to which pages exist or which files, and the
 * wiki's info only when the frame does not say what changed. Its comments
 * read again only for a change to them or to the page itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikiUpdateTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var repository: WikisRepository

    /** How many times each path has been asked for. */
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** The server's end of the wiki's socket, once it is open. */
    @Volatile private var peer: WebSocket? = null

    // "old" redirects to "home": opened by one slug, the page has the other.
    private val page = """{"page": {"id": "p1", "slug": "home", "title": "Home", "content": "Text"},
        "comments": {"count": 0}, "links": {"missing": []}}"""

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                return when (path) {
                    INFO -> ok("""{"wiki": {"id": "w1", "fingerprint": "wfp", "name": "Wiki"}, "permissions": {}}""")
                    HOME, OLD -> ok(page)
                    COMMENTS -> ok("""{"comments": []}""")
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            peer = webSocket
                        }

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
        val gson = GsonBuilder()
            .registerTypeAdapter(PageFetchResponse::class.java, PageFetchResponseDeserializer())
            .create()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
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

    /** Runs the main looper the network's answers resume on until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets everything already under way finish. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun handle(slug: String) = SavedStateHandle(mapOf("wikiId" to "w1", "page" to slug))

    /** A page opened by [slug], loaded, with its socket open. */
    private fun page(slug: String): PageViewModel {
        val model = PageViewModel(handle(slug), repository, session, socket)
        until { socket.isLive("wfp") && peer != null && model.uiState.value.page != null }
        settle()
        return model
    }

    private fun update(fields: String) = """{"type": "wiki/update", "wiki": "w1"$fields}"""

    /**
     * How many times [frame] made [paths] be asked for. A [marker] frame that
     * asks for [marked] once follows it, so the count is read only once the
     * frame has certainly been handled: the socket hands frames over in order.
     * The marker is sent once the frame's reload debounce has passed, or the
     * two would be read together as one burst.
     */
    private fun heard(frame: String, marker: String, marked: String, vararg paths: String): List<Int> {
        val before = paths.map { path -> count(path) }
        check(peer!!.send(frame))
        settle()
        val mark = count(marked)
        check(peer!!.send(marker))
        until { count(marked) > mark }
        settle()
        return paths.mapIndexed { index, path -> count(path) - before[index] - if (path == marked) 1 else 0 }
    }

    /** What a page opened by [slug] asks for on [frame]: its page, then the wiki's info. */
    private fun page(slug: String, frame: String): List<Int> {
        page(slug)
        val path = if (slug == "home") HOME else OLD
        return heard(frame, update(""", "kind": "pages", "page": "new""""), path, path, INFO)
    }

    @Test
    fun `an edit to the open page reads the page again but not the wiki`() {
        assertEquals(listOf(1, 0), page("home", update(""", "kind": "edit", "page": "home"""")))
    }

    @Test
    fun `an edit to another page reads nothing`() {
        assertEquals(listOf(0, 0), page("home", update(""", "kind": "edit", "page": "other"""")))
    }

    @Test
    fun `a comment or tag change on the open page reads the page again`() {
        assertEquals(listOf(1, 0), page("home", update(""", "kind": "comment", "page": "home"""")))
        assertEquals(listOf(1, 0), heard(update(""", "kind": "tag", "page": "home""""), update(""", "kind": "pages", "page": "new""""), HOME, HOME, INFO))
        assertEquals(listOf(0, 0), heard(update(""", "kind": "comment", "page": "other""""), update(""", "kind": "pages", "page": "new""""), HOME, HOME, INFO))
        assertEquals(listOf(0, 0), heard(update(""", "kind": "tag", "page": "other""""), update(""", "kind": "pages", "page": "new""""), HOME, HOME, INFO))
    }

    @Test
    fun `a page reached through a redirect answers to both its slugs`() {
        // Opened by "old", it is "home": an edit names the page by the slug it has.
        assertEquals(listOf(1, 0), page("old", update(""", "kind": "edit", "page": "home"""")))
        assertEquals(listOf(1, 0), heard(update(""", "kind": "comment", "page": "old""""), update(""", "kind": "pages", "page": "new""""), OLD, OLD, INFO))
        // The redirect it was opened through.
        assertEquals(listOf(1, 0), heard(update(""", "kind": "redirect", "page": "old""""), update(""", "kind": "pages", "page": "new""""), OLD, OLD, INFO))
    }

    @Test
    fun `a redirect from any slug reads the page again, since its links to that slug may now exist`() {
        assertEquals(listOf(1, 0), page("home", update(""", "kind": "redirect", "page": "elsewhere"""")))
    }

    @Test
    fun `a change to which pages or files exist reads the page again`() {
        assertEquals(listOf(1, 0), page("home", update(""", "kind": "pages", "page": "other", "previous": "before"""")))
        assertEquals(listOf(1, 0), heard(update(""", "kind": "attachment""""), update(""", "kind": "pages", "page": "new""""), HOME, HOME, INFO))
    }

    @Test
    fun `a change that does not say what changed reads the page and the wiki again`() {
        assertEquals(listOf(1, 1), page("home", update("")))
        assertEquals(listOf(1, 1), heard("""{"type": "wiki/resynced", "wiki": "w1"}""", update(""", "kind": "pages", "page": "new""""), HOME, HOME, INFO))
        // A kind this client does not know may be anything.
        assertEquals(listOf(1, 1), heard(update(""", "kind": "future", "page": "other""""), update(""", "kind": "pages", "page": "new""""), HOME, HOME, INFO))
    }

    /** What the comments of "home" ask for on [frame]; a comment on it marks. */
    private fun comment(frame: String): Int =
        heard(frame, update(""", "kind": "comment", "page": "home""""), COMMENTS, COMMENTS).single()

    @Test
    fun `comments read again only for a change to them or to their page`() {
        CommentsViewModel(handle("home"), repository, session, socket)
        until { socket.isLive("wfp") && peer != null && count(COMMENTS) > 0 }
        settle()
        assertEquals(1, comment(update(""", "kind": "comment", "page": "home"""")))
        assertEquals(0, comment(update(""", "kind": "comment", "page": "other"""")))
        assertEquals(1, comment(update(""", "kind": "pages", "page": "home"""")))
        assertEquals("renamed from it", 1, comment(update(""", "kind": "pages", "page": "renamed", "previous": "home"""")))
        assertEquals(0, comment(update(""", "kind": "pages", "page": "other", "previous": "before"""")))
        assertEquals(0, comment(update(""", "kind": "edit", "page": "home"""")))
        assertEquals(0, comment(update(""", "kind": "tag", "page": "home"""")))
        assertEquals(0, comment(update(""", "kind": "redirect", "page": "home"""")))
        assertEquals("their files", 1, comment(update(""", "kind": "attachment"""")))
        assertEquals(1, comment(update("")))
        assertEquals(1, comment("""{"type": "wiki/resynced", "wiki": "w1"}"""))
    }

    private companion object {
        const val INFO = "/wikis/w1/-/info"
        const val HOME = "/wikis/w1/-/pages/home"
        const val OLD = "/wikis/w1/-/pages/old"
        const val COMMENTS = "/wikis/w1/-/pages/home/comments"
    }
}
