// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feedlist

import android.app.Application
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
import org.junit.Assert.assertNull
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
import org.mochios.feeds.ui.feed.FeedViewModel
import org.mochios.feeds.ui.post.PostDetailViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The drawer and every feed's view follow all of the user's feeds. Core caps a
 * user's sockets at 32 across their apps and devices, so they hold the one key
 * the class `-/info` answer names rather than one per feed, falling back to
 * each feed's own key from a server that names none. That key carries each
 * feed's frame wrapped, and also the frames of feeds held only as another
 * feed's source, which neither shows. Every screen on a feeds socket reads
 * again what it shows when the socket opens again after being down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedSocketsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var api: FeedsApi
    private lateinit var repository: FeedsRepository

    /** The key the class `-/info` answer names; null leaves the field out. */
    @Volatile private var aggregate: String? = "feeds"

    /** Every feed's posts, as `-/posts` lists them. */
    @Volatile private var posts = """[{"id": "p1", "feed": "f1", "feed_fingerprint": "fp1"}]"""

    /** How many times each path has been asked for. */
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** The keys the client's handshakes asked for, in order. */
    private val keys = ConcurrentLinkedQueue<String>()

    /** The server's end of each open socket, by key. */
    private val live = ConcurrentHashMap<String, WebSocket>()

    /** How long the server takes over each handshake, in milliseconds. */
    @Volatile private var slow = 0L

    /** Whether the server refuses every handshake, the user's sockets at their cap. */
    @Volatile private var refusing = false

    /** How many feeds the class `-/info` lists. */
    @Volatile private var listed = 3

    @Before
    fun begin() {
        context.getSharedPreferences("mochi_feeds", Context.MODE_PRIVATE)
            .edit().putBoolean("unread_only", false).commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                return when (path) {
                    "/feeds/-/info" -> {
                        val named = aggregate?.let { key -> """, "websocket": "$key"""" }.orEmpty()
                        val feeds = (1..listed).joinToString { n -> feed(n) }
                        ok("""{"feeds": [$feeds], "settings": {"sort": ""}$named}""")
                    }
                    "/feeds/-/posts" -> ok("""{"posts": $posts}""")
                    "/feeds/f1/-/info" -> ok("""{"feed": ${feed(1)}, "permissions": {}}""")
                    "/feeds/f1/-/posts" -> ok("""{"posts": [{"id": "p1", "feed": "f1", "feed_fingerprint": "fp1"}]}""")
                    "/feeds/f1/-/p1" -> ok("""{"posts": [{"id": "p1", "feed": "f1", "feed_fingerprint": "fp1"}], "permissions": {}}""")
                    "/feeds/-/saved/list" -> ok("""{"saved": []}""")
                    "/_/websocket" -> {
                        val key = request.requestUrl?.queryParameter("key").orEmpty()
                        keys.add(key)
                        Thread.sleep(slow)
                        if (refusing) return MockResponse().setResponseCode(429)
                        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                live[key] = webSocket
                            }

                            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                                webSocket.close(code, reason)
                            }
                        })
                    }
                    else -> if (request.method == "POST") ok("{}") else MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(FeedsApi::class.java)
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("a1", base)
        }
        socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = "token"

            override suspend fun invalidate(app: String) {
            }
        })
        repository = FeedsRepository(api, retrofit.create(MenuApi::class.java), FileStore(context), context, session)
    }

    @After
    fun end() {
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /** Feed [n]: id `f<n>`, fingerprint `fp<n>`. */
    private fun feed(n: Int) = """{"id": "f$n", "fingerprint": "fp$n", "name": "Feed $n"}"""

    /** [frame] as the aggregate key carries it, wrapped, about [feed]. */
    private fun wrapped(feed: String, frame: String) = """{"type": "feeds/frame", "feed": "$feed", "frame": $frame}"""

    private fun count(path: String): Int = counts[path]?.get() ?: 0

    private fun drawer() = FeedListViewModel(repository, socket, session)

    private fun feed(id: String) = FeedViewModel(
        SavedStateHandle(mapOf("feedId" to id)),
        repository,
        SavedRepository(api, Gson(), session),
        socket,
        session,
        context,
    )

    private fun everything() = feed("__all__")

    /**
     * Runs the main thread until [done], the server answering on its own. Its
     * clock is moved on each turn, or a debounced fetch would never come due.
     */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets everything already under way finish, the debounce included. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun send(key: String, frame: String) {
        live.getValue(key).send(frame)
    }

    /** The app goes to the background and comes back, its sockets closed and opened again. */
    private fun away() {
        socket.background()
        socket.foreground()
    }

    @Test
    fun `the drawer follows every feed on the one key the roster names`() {
        val drawer = drawer()
        until { drawer.feeds.value.size == 3 && live.containsKey("feeds") }
        settle()
        assertEquals(listOf("feeds"), keys.toList())
    }

    @Test
    fun `the drawer follows each feed on its own key from a server that names none`() {
        aggregate = null
        val drawer = drawer()
        until { drawer.feeds.value.size == 3 && live.size == 3 }
        settle()
        assertEquals(listOf("fp1", "fp2", "fp3"), keys.toList().sorted())
    }

    @Test
    fun `every feed's view follows them on the one key the roster names`() {
        val model = everything()
        until { model.posts.value.isNotEmpty() && live.containsKey("feeds") }
        settle()
        assertEquals(listOf("feeds"), keys.toList())
    }

    @Test
    fun `every feed's view follows each feed on its own key from a server that names none`() {
        aggregate = null
        val model = everything()
        until { model.posts.value.isNotEmpty() && live.size == 3 }
        settle()
        assertEquals(listOf("fp1", "fp2", "fp3"), keys.toList().sorted())
    }

    @Test
    fun `the drawer refreshes for a feed it lists and not for one held only as a source`() {
        val drawer = drawer()
        until { drawer.feeds.value.size == 3 && live.containsKey("feeds") && socket.isLive("feeds") }
        settle()
        val before = count("/feeds/-/info")
        send("feeds", wrapped("s9", """{"type": "post/create", "feed": "s9", "post": "x9"}"""))
        settle()
        assertEquals("a source's frame reads nothing", before, count("/feeds/-/info"))
        send("feeds", wrapped("f2", """{"type": "post/create", "feed": "f2", "post": "x2"}"""))
        until { count("/feeds/-/info") == before + 1 }
    }

    @Test
    fun `every feed's view counts new posts only from the feeds it lists`() {
        val model = everything()
        until { model.posts.value.isNotEmpty() && live.containsKey("feeds") && socket.isLive("feeds") }
        settle()
        send("feeds", wrapped("s9", """{"type": "post/create", "feed": "s9", "post": "x9"}"""))
        settle()
        assertEquals("a source's post is not counted", 0, model.newPostsCount.value)
        send("feeds", wrapped("f2", """{"type": "post/create", "feed": "f2", "post": "x2"}"""))
        until { model.newPostsCount.value == 1 }
    }

    @Test
    fun `every feed's view still takes a feed's bare frame on its own key from a server that names none`() {
        aggregate = null
        val model = everything()
        until { model.posts.value.isNotEmpty() && live.size == 3 && socket.isLive("fp2") }
        settle()
        send("fp2", """{"type": "post/create", "feed": "f2", "post": "x2"}""")
        until { model.newPostsCount.value == 1 }
    }

    @Test
    fun `a feed leaving every feed's view takes its posts with it, wherever the reader is`() {
        posts = """[
            {"id": "p1", "feed": "f1", "feed_fingerprint": "fp1"},
            {"id": "p2", "feed": "f2", "feed_fingerprint": "fp2"},
            {"id": "p3", "feed": "f1", "feed_fingerprint": "fp1"}
        ]"""
        val model = everything()
        until { model.posts.value.size == 3 && live.containsKey("feeds") && socket.isLive("feeds") }
        settle()
        // The reader has passed the removed feed's post.
        model.onPostBottomViewed("p3")
        posts = """[{"id": "p1", "feed": "f1", "feed_fingerprint": "fp1"}, {"id": "p3", "feed": "f1", "feed_fingerprint": "fp1"}]"""
        send("feeds", wrapped("f2", """{"type": "feed/removed", "feed": "f2"}"""))
        until { model.posts.value.none { post -> post.feed == "f2" } }
        settle()
        assertEquals(listOf("p1", "p3"), model.posts.value.map { post -> post.id })
        assertNull(model.gone.value)
    }

    @Test
    fun `the drawer reads its feeds again when its socket comes back`() {
        val drawer = drawer()
        until { drawer.feeds.value.size == 3 && socket.isLive("feeds") }
        settle()
        val before = count("/feeds/-/info")
        away()
        until { count("/feeds/-/info") > before }
    }

    @Test
    fun `every feed's view reads its posts again when its socket comes back`() {
        val model = everything()
        until { model.posts.value.isNotEmpty() && socket.isLive("feeds") }
        settle()
        val before = count("/feeds/-/posts")
        away()
        until { count("/feeds/-/posts") > before }
    }

    @Test
    fun `a feed reads its posts and itself again when its socket comes back`() {
        val model = feed("f1")
        until { model.posts.value.isNotEmpty() && socket.isLive("f1") }
        settle()
        val posts = count("/feeds/f1/-/posts")
        val information = count("/feeds/f1/-/info")
        away()
        until { count("/feeds/f1/-/posts") > posts && count("/feeds/f1/-/info") > information }
    }

    @Test
    fun `a post reads itself again when its socket comes back`() {
        val model = PostDetailViewModel(
            SavedStateHandle(mapOf("feedId" to "f1", "postId" to "p1")),
            repository,
            socket,
            session,
            context as Application,
        )
        until { model.post.value != null && socket.isLive("f1") }
        settle()
        val before = count("/feeds/f1/-/p1")
        away()
        until { count("/feeds/f1/-/p1") > before }
    }

    /** Runs the main thread for [millis] of real time, its clock moving with it. */
    private fun pass(millis: Long) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    @Test
    fun `every feed's view reads once when its feeds' sockets open again together`() {
        aggregate = null
        val model = everything()
        until { model.posts.value.isNotEmpty() && live.size == 3 && listOf("fp1", "fp2", "fp3").all { key -> socket.isLive(key) } }
        settle()
        val before = count("/feeds/-/posts")
        away()
        until { count("/feeds/-/posts") > before }
        pass(1500)
        assertEquals("one read for three sockets", before + 1, count("/feeds/-/posts"))
    }

    @Test
    fun `a feed's return reads once, after its socket is open`() {
        val model = feed("f1")
        until { model.posts.value.isNotEmpty() && socket.isLive("f1") }
        settle()
        // The screen's first resume, which its opening load already covers.
        model.reloadOnForeground()
        settle()
        val before = count("/feeds/f1/-/posts")
        slow = 300
        socket.background()
        socket.foreground()
        model.reloadOnForeground()
        until { count("/feeds/f1/-/posts") > before }
        pass(2000)
        assertEquals("one read for the return", before + 1, count("/feeds/f1/-/posts"))
    }

    @Test
    fun `the drawer's return reads once, after its socket is open`() {
        val drawer = drawer()
        until { drawer.feeds.value.size == 3 && socket.isLive("feeds") }
        settle()
        // The screen's first resume, which its opening load already covers.
        drawer.onScreenResumed()
        settle()
        val before = count("/feeds/-/info")
        slow = 300
        socket.background()
        socket.foreground()
        drawer.onScreenResumed()
        until { count("/feeds/-/info") > before }
        pass(2000)
        assertEquals("one read for the return", before + 1, count("/feeds/-/info"))
    }

    @Test
    fun `a feed's return reads even when its socket is refused`() {
        val model = feed("f1")
        until { model.posts.value.isNotEmpty() && socket.isLive("f1") }
        settle()
        model.reloadOnForeground()
        settle()
        val before = count("/feeds/f1/-/posts")
        refusing = true
        socket.background()
        socket.foreground()
        model.reloadOnForeground()
        until { count("/feeds/f1/-/posts") > before }
    }

    @Test
    fun `a feed's return reads while its first connect is still waiting`() {
        slow = 4000
        val model = feed("f1")
        until { model.posts.value.isNotEmpty() }
        settle()
        val before = count("/feeds/f1/-/posts")
        // The opening resume, then a return, the socket not yet open.
        model.reloadOnForeground()
        model.reloadOnForeground()
        until { count("/feeds/f1/-/posts") > before }
    }

    @Test
    fun `the drawer's return reads even when its socket is refused`() {
        val drawer = drawer()
        until { drawer.feeds.value.size == 3 && socket.isLive("feeds") }
        settle()
        drawer.onScreenResumed()
        settle()
        val before = count("/feeds/-/info")
        refusing = true
        socket.background()
        socket.foreground()
        drawer.onScreenResumed()
        until { count("/feeds/-/info") > before }
    }

    @Test
    fun `the drawer reads once when many feeds' sockets open again together`() {
        aggregate = null
        listed = 16
        val drawer = drawer()
        until { drawer.feeds.value.size == 16 && live.size == 16 && (1..16).all { n -> socket.isLive("fp$n") } }
        settle()
        val before = count("/feeds/-/info")
        away()
        until { count("/feeds/-/info") > before }
        pass(1500)
        assertEquals("one read for sixteen sockets", before + 1, count("/feeds/-/info"))
    }
}
