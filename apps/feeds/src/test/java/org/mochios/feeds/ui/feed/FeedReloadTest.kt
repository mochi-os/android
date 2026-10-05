// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import android.content.Context
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.SavedRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A feed against a server and its websocket: what changes the list under a
 * reader part-way through it. A reload they did not ask for keeps what they
 * have reached, and a frame about one post touches that post alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedReloadTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: FeedViewModel

    /** The server's end of the feed's websocket, once the client has connected. */
    @Volatile private var live: WebSocket? = null

    /** The posts the server lists, in order. */
    @Volatile private var listing = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")

    /** How many times the list has been asked for. */
    private val lists = AtomicInteger()

    /** Each post asked for by itself, in order. */
    private val singles = ConcurrentLinkedQueue<String>()

    /** Each post the client has marked read. */
    private val reads = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when {
                    path == "/feeds/f1/-/info" ->
                        ok("""{"feed": {"id": "f1", "fingerprint": "f1", "name": "Feed", "sort": "new"}, "permissions": {}}""")
                    path == "/feeds/f1/-/posts" || path == "/feeds/-/posts" -> {
                        lists.incrementAndGet()
                        ok("""{"posts": [${listing.joinToString { id -> post(id) }}]}""")
                    }
                    path == "/feeds/f1/-/posts/read" -> {
                        Regex("post=([^&]+)").findAll(request.body.readUtf8()).forEach { reads.add(it.groupValues[1]) }
                        ok("{}")
                    }
                    path == "/_/websocket" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            live = webSocket
                        }

                        // Answer the client's close, or the server waits on it.
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                    // Any other read under the feed is one post, asked for by itself.
                    request.method == "GET" && path.startsWith("/feeds/f1/-/") && path != "/feeds/f1/-/notifications" -> {
                        val id = path.removePrefix("/feeds/f1/-/")
                        singles.add(id)
                        ok("""{"posts": [${post(id, body = "edited")}]}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun post(id: String, body: String = "") =
        """{"id": "$id", "feed": "f1", "feed_fingerprint": "f1", "body": "$body"}"""

    /** Opens [feed] and waits for its first list. */
    private fun open(feed: String = "f1", unread: Boolean = false) {
        context.getSharedPreferences("mochi_feeds", Context.MODE_PRIVATE)
            .edit().putBoolean("unread_only", unread).commit()
        val base = server.url("/").toString().trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(FeedsApi::class.java)
        val session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        model = FeedViewModel(
            SavedStateHandle(mapOf("feedId" to feed)),
            FeedsRepository(api, retrofit.create(MenuApi::class.java), FileStore(context)),
            SavedRepository(api, Gson()),
            socket,
            session,
            context,
        )
        until { ids() == listing }
        // The screen's first resume lands behind the load and is not honoured.
        model.reloadOnForeground()
    }

    /**
     * Runs the main thread until [done], the server answering on its own. Its
     * clock is moved on each turn, or a debounced fetch would never come due.
     */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out with ${ids()}, error ${model.error.value}" }
            Thread.sleep(10)
        }
    }

    /** Lets everything already under way finish, and a second pass of time besides. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun ids() = model.posts.value.map { post -> post.id }

    /** The reader flips through [posts], landing on each. */
    private fun land(vararg posts: String) = posts.forEach { post -> model.onPostBottomViewed(post) }

    /** The reader leaves the app and comes back, which reloads the list unasked. */
    private fun comeBack() {
        val before = lists.get()
        model.reloadOnForeground()
        until { lists.get() > before }
        settle()
    }

    /** Frames from the server, all on the wire before the client looks at any. */
    private fun frames(vararg frames: String) {
        until { live != null }
        frames.forEach { frame -> live!!.send(frame) }
        Thread.sleep(150)
    }

    @Test
    fun `coming back to an unread-only feed keeps the posts read, and what is unread follows them`() {
        open(unread = true)
        land("a", "b", "c", "d", "e")
        until { reads.containsAll(listOf("a", "b", "c", "d", "e")) }
        // The server no longer lists what has been read.
        listing = listOf("f", "g", "h", "i", "j")
        comeBack()
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), ids())
        assertTrue(model.posts.value.take(5).all { post -> post.read != 0L })
    }

    @Test
    fun `flipping back does not give up the posts beyond`() {
        open(unread = true)
        land("a", "b", "c", "d", "e", "d", "c")
        until { reads.containsAll(listOf("a", "b", "c", "d", "e")) }
        listing = listOf("f", "g", "h", "i", "j")
        comeBack()
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), ids())
    }

    @Test
    fun `coming back to every feed at once keeps the posts read the same way`() {
        open(feed = "__all__", unread = true)
        land("a", "b", "c", "d", "e")
        listing = listOf("f", "g", "h", "i", "j")
        comeBack()
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), ids())
    }

    @Test
    fun `a refresh the reader asks for replaces the list, and what was reached no longer holds`() {
        open()
        land("a", "b", "c", "d", "e")
        listing = listOf("n1") + listing
        model.refresh()
        until { ids() == listing }
        // Nothing of the new list has been reached, so a reload replaces it whole.
        listing = listOf("n2") + listing
        comeBack()
        assertEquals(listing, ids())
    }

    @Test
    fun `a new sort replaces the list, and what was reached no longer holds`() {
        open()
        land("a", "b", "c", "d", "e")
        listing = listing.reversed()
        model.setSort("top")
        until { ids() == listing }
        listing = listOf("n1") + listing
        comeBack()
        assertEquals(listing, ids())
    }

    @Test
    fun `a reload that adds nothing past the post reached leaves the count of new posts standing`() {
        open()
        land(*listing.toTypedArray())
        frames("""{"type": "post/create", "feed": "f1", "post": "elsewhere"}""")
        until { model.newPostsCount.value == 1 }
        comeBack()
        assertEquals(1, model.newPostsCount.value)
    }

    @Test
    fun `a frame about a post fetches that post alone, and changes it in place`() {
        open()
        val listed = lists.get()
        frames("""{"type": "react/post", "feed": "f1", "post": "c"}""")
        until { model.posts.value[2].body == "edited" }
        settle()
        assertEquals(listOf("c"), singles.toList())
        assertEquals(listed, lists.get())
        assertEquals(listing, ids())
    }

    @Test
    fun `a burst of frames about one post makes one fetch`() {
        open()
        frames(
            """{"type": "tag/add", "feed": "f1", "post": "c"}""",
            """{"type": "tag/add", "feed": "f1", "post": "c"}""",
            """{"type": "comment/create", "feed": "f1", "post": "c"}""",
        )
        until { singles.isNotEmpty() }
        settle()
        assertEquals(listOf("c"), singles.toList())
    }

    @Test
    fun `a frame about a post that is not in the list fetches nothing`() {
        open()
        val listed = lists.get()
        frames("""{"type": "post/edit", "feed": "f1", "post": "elsewhere"}""")
        settle()
        assertEquals(emptyList<String>(), singles.toList())
        assertEquals(listed, lists.get())
    }

    @Test
    fun `a post deleted elsewhere leaves the list, and nothing is reloaded`() {
        open()
        val listed = lists.get()
        frames("""{"type": "post/delete", "feed": "f1", "post": "b"}""")
        until { ids() == listing - "b" }
        settle()
        assertEquals(listed, lists.get())
    }

    @Test
    fun `the post that takes a deleted post's page is where the reader then is`() {
        open(unread = true)
        land("a", "b", "c", "d", "e")
        until { reads.containsAll(listOf("a", "b", "c", "d", "e")) }
        // The post on screen is deleted; f moves onto its page, still unread.
        frames("""{"type": "post/delete", "feed": "f1", "post": "e"}""")
        until { ids() == listing - "e" }
        listing = listOf("f", "g", "h", "i", "j")
        comeBack()
        assertEquals(listOf("a", "b", "c", "d", "f", "g", "h", "i", "j"), ids())
    }
}
