// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.model.Post
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.repository.SavedRepository
import org.mochios.forums.ui.forumlist.ForumListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * A forum's list against a server that pages it twenty at a time: what the
 * tag chips, a reload nobody asked for, a failed page and a frame do to it,
 * and what reaches the drawer beside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumListingTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var api: ForumsApi
    private lateinit var session: SessionManager
    private lateinit var repository: ForumsRepository

    /** The forum's posts as the server orders them. */
    @Volatile private var posts: List<String> = (1..45).map { number -> "p$number" }

    /** Titles the server gives posts, by id; a post's id where none is set. */
    private val titles = ConcurrentHashMap<String, String>()

    /** Rows the server adds to every page, as written. */
    @Volatile private var extra: List<String> = emptyList()

    /** Whether the server refuses every list of posts. */
    @Volatile private var down = false

    /** The forum's name in the roster. */
    @Volatile private var name = "Forum"

    /** Each request for posts, as its query. */
    private val asked = ConcurrentLinkedQueue<Map<String, String>>()

    /** The server's end of the forum's websocket, once the client has connected. */
    @Volatile private var live: WebSocket? = null

    private val events = mutableListOf<ForumEvent>()
    private var collector: Job? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/forums/f1/-/posts" -> {
                        asked.add(url.queryParameterNames.associateWith { key -> url.queryParameter(key).orEmpty() })
                        if (down) return refused()
                        val limit = url.queryParameter("limit")?.toInt() ?: 20
                        val start = url.queryParameter("cursor")?.toInt() ?: 0
                        val page = posts.drop(start).take(limit)
                        val next = start + page.size
                        val more = next < posts.size
                        val cursor = if (more) """, "cursor": "$next"""" else ""
                        ok("""{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Forum", "sort": "new"}, "posts": [${rows(page)}], "hasMore": $more$cursor}""")
                    }
                    "/forums/-/list" -> {
                        asked.add(emptyMap())
                        if (down) refused()
                        else ok("""{"forums": [], "posts": [${rows(posts.take(20))}], "settings": {"sort": ""}}""")
                    }
                    "/forums/-/information" ->
                        ok("""{"forums": [{"id": "f1", "fingerprint": "fp1", "name": "$name"}], "settings": {"sort": ""}}""")
                    "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
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
        api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = ForumsRepository(api, FileStore(context), context, session)
    }

    @After
    fun end() {
        collector?.cancel()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun refused() = MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")

    private fun rows(page: List<String>) =
        (page.map { id -> """{"id": "$id", "title": "${titles[id] ?: id}"}""" } + extra).joinToString()

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets everything already under way finish, a debounce included. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun build(forum: String = "f1"): ForumViewModel {
        val model = ForumViewModel(
            SavedStateHandle(mapOf("forumId" to forum)),
            repository,
            SavedRepository(api, Gson(), session),
            socket,
            session,
        )
        collector?.cancel()
        collector = CoroutineScope(Dispatchers.Main).launch { model.events.collect { events += it } }
        return model
    }

    /** Opens [forum] and waits for its first page. */
    private fun open(forum: String = "f1"): ForumViewModel {
        val model = build(forum)
        until { model.uiState.value.posts.isNotEmpty() && !model.uiState.value.isLoading }
        settle()
        return model
    }

    /** Loads the next page and waits for it. */
    private fun more(model: ForumViewModel) {
        val size = model.uiState.value.posts.size
        model.loadMore()
        until { model.uiState.value.posts.size > size && !model.uiState.value.isLoadingMore }
    }

    /** The reader comes back to the forum, and the list is read again for nobody who asked. */
    private fun comeBack(model: ForumViewModel) {
        model.reloadOnForeground()
        val before = asked.size
        model.reloadOnForeground()
        until { asked.size > before }
        settle()
    }

    private fun ids(model: ForumViewModel) = model.uiState.value.posts.map { post -> post.id }

    private fun errors() = events.count { event -> event is ForumEvent.ShowError }

    @Test
    fun `a tag tapped again clears the filter`() {
        val model = open()
        val before = asked.size
        model.setTagFilter("news")
        settle()
        assertEquals("news", asked.last()["tag"])
        model.setTagFilter("news")
        settle()
        assertEquals("THE POINT: the second tap reads the forum again", before + 2, asked.size)
        assertNull("with no tag", asked.last()["tag"])
        assertNull(model.uiState.value.currentTag)
    }

    @Test
    fun `a reload nobody asked for reads again every page the reader has loaded`() {
        val model = open()
        more(model)
        assertEquals(40, model.uiState.value.posts.size)
        titles["p30"] = "Edited"
        comeBack(model)
        assertEquals("THE POINT: one request for every post loaded", "40", asked.last()["limit"])
        assertEquals((1..40).map { number -> "p$number" }, ids(model))
        assertEquals("Edited", model.uiState.value.posts.single { post -> post.id == "p30" }.title)
        assertEquals("40", model.uiState.value.cursor)
    }

    @Test
    fun `past one request's worth, the first page is read again and the pages after it stay`() {
        posts = (1..130).map { number -> "p$number" }
        val model = open()
        repeat(5) { more(model) }
        assertEquals(120, model.uiState.value.posts.size)
        // Somebody posts, and a post the reader passed is edited.
        posts = listOf("p0") + posts
        titles["p50"] = "Edited"
        comeBack(model)
        assertEquals("the most one request answers", "100", asked.last()["limit"])
        assertEquals("THE POINT: the new post above every page the reader loaded", listOf("p0") + (1..120).map { number -> "p$number" }, ids(model))
        assertEquals("Edited", model.uiState.value.posts.single { post -> post.id == "p50" }.title)
        assertEquals("the next page resumes where it did", "120", model.uiState.value.cursor)
    }

    private fun list(vararg ids: String) = ids.map { id -> Post(id = id) }

    @Test
    fun `a first page that shares no post with the list replaces it`() {
        assertNull(fold(list("a", "b", "c"), list("x", "y")))
    }

    @Test
    fun `a first page that reaches the list's last post replaces it`() {
        assertNull(fold(list("a", "b", "c"), list("new", "a", "b", "c")))
    }

    @Test
    fun `a post the first page no longer holds goes, and one it moved past stays after it`() {
        // "b" was deleted; "d" fell out of the first page as "new" arrived.
        val folded = fold(list("a", "b", "c", "d", "e"), list("new", "a", "c"))
        assertEquals(listOf("new", "a", "c", "d", "e"), folded?.map { post -> post.id })
    }

    @Test
    fun `rows without an id, and repeats of one, are left out of the first page`() {
        extra = listOf("""{"id": "p1"}""", """{"id": ""}""", """{"title": "none"}""")
        val model = open()
        assertEquals((1..20).map { number -> "p$number" }, ids(model))
    }

    @Test
    fun `rows without an id, and repeats of one, are left out of a refresh`() {
        val model = open()
        extra = listOf("""{"id": "p1"}""", """{"id": ""}""", """{"title": "none"}""")
        model.refresh()
        settle()
        assertEquals((1..20).map { number -> "p$number" }, ids(model))
    }

    @Test
    fun `rows without an id, and repeats of one, are left out of a reload nobody asked for`() {
        val model = open()
        extra = listOf("""{"id": "p1"}""", """{"id": ""}""", """{"title": "none"}""")
        comeBack(model)
        assertEquals((1..20).map { number -> "p$number" }, ids(model))
    }

    @Test
    fun `rows without an id are left out of the next page`() {
        val model = open()
        extra = listOf("""{"id": ""}""", """{"title": "none"}""")
        more(model)
        assertEquals((1..40).map { number -> "p$number" }, ids(model))
    }

    @Test
    fun `a forum counts as loaded only once its posts have come`() {
        down = true
        val failing = build()
        until { failing.uiState.value.error != null }
        assertFalse("a forum that failed to open is not opened again at the next start", failing.uiState.value.loaded)
        down = false
        val model = open()
        assertTrue(model.uiState.value.loaded)
    }

    @Test
    fun `a refresh that fails over posts shown says so and keeps them`() {
        val model = open()
        down = true
        model.refresh()
        settle()
        assertEquals("THE POINT: said in a snackbar", 1, errors())
        assertNull("not in the list's place", model.uiState.value.error)
        assertEquals(20, model.uiState.value.posts.size)
        assertFalse(model.uiState.value.isRefreshing)
    }

    @Test
    fun `a next page that fails says so and keeps the list`() {
        val model = open()
        down = true
        model.loadMore()
        settle()
        assertEquals("THE POINT: said in a snackbar", 1, errors())
        assertNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isLoadingMore)
        assertEquals(20, model.uiState.value.posts.size)
    }

    @Test
    fun `a refresh of every forum at once that fails says so and keeps the list`() {
        val model = open(LastViewedStore.ALL)
        down = true
        model.refresh()
        settle()
        assertEquals("THE POINT: said in a snackbar", 1, errors())
        assertNull(model.uiState.value.error)
        assertEquals(20, model.uiState.value.posts.size)
    }

    @Test
    fun `a refresh that fails with no posts shown says so in their place`() {
        posts = emptyList()
        val model = build()
        until { !model.uiState.value.isLoading }
        settle()
        down = true
        model.refresh()
        settle()
        assertEquals(0, errors())
        assertTrue(model.uiState.value.error != null)
    }

    @Test
    fun `a frame is handled on the main thread, not the socket's`() {
        val model = open()
        until { live != null }
        settle()
        live!!.send("""{"type": "forum/removed", "forum": "f1"}""")
        // The frame reaches the socket's thread meanwhile; the main one is not run.
        Thread.sleep(500)
        assertNull("THE POINT: nothing changed off the main thread", model.uiState.value.gone)
        until { model.uiState.value.gone != null }
    }

    @Test
    fun `the drawer reads its forums again when a frame says the forum changed`() {
        val drawer = ForumListViewModel(repository)
        val model = open()
        until { drawer.uiState.value.forums.isNotEmpty() && live != null }
        settle()
        assertEquals(listOf("Forum"), drawer.uiState.value.forums.map { forum -> forum.name })
        name = "Renamed"
        live!!.send("""{"type": "forum/update", "forum": "f1"}""")
        settle()
        assertEquals("THE POINT: the drawer shows the new name", listOf("Renamed"), drawer.uiState.value.forums.map { forum -> forum.name })
        assertEquals(20, model.uiState.value.posts.size)
    }
}
