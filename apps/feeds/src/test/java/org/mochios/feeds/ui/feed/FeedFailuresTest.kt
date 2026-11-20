// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import android.content.Context
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.model.Post
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.SavedRepository
import org.mochios.feeds.ui.feedlist.FeedListViewModel
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
 * What the feed does when the server refuses or cannot be reached: every
 * action the reader takes ends in the change shown or in the failure said,
 * never a silent reload from the top; a setting whose list cannot be read
 * goes back to the one the list was read under; and a next page that fails is
 * kept as a failure to show, not asked for again on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedFailuresTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var api: FeedsApi
    private lateinit var repository: FeedsRepository

    /** Paths the server refuses with a 403. */
    @Volatile private var refused = setOf<String>()

    /** Whether the first page of the posts fails, for a reload after the first load. */
    @Volatile private var listFails = false

    /** Whether a next page fails. */
    @Volatile private var pageFails = false

    /** Whether the class `-/info` fails, for the drawer. */
    @Volatile private var rosterFails = false

    /** How many times each path was asked for. */
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    /** Each write, as path and form body. */
    private val writes = ConcurrentLinkedQueue<Pair<String, String>>()

    private val events = mutableListOf<FeedActionEvent>()
    private var collector: Job? = null

    @Before
    fun begin() {
        context.getSharedPreferences("mochi_feeds", Context.MODE_PRIVATE)
            .edit().putBoolean("unread_only", false).commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(404)
                val path = url.encodedPath
                counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                if (request.method == "POST") writes.add(path to request.body.readUtf8())
                if (path in refused) {
                    return MockResponse().setResponseCode(403).setBody("""{"error": "Not allowed"}""")
                }
                return when (path) {
                    "/feeds/-/info" -> if (rosterFails) {
                        MockResponse().setResponseCode(500).setBody("""{"error": "Broken"}""")
                    } else {
                        ok("""{"feeds": [{"id": "f1", "fingerprint": "f1", "name": "Feed", "sort": "new"}], "settings": {"sort": "new"}}""")
                    }
                    "/feeds/f1/-/info" -> ok("""{"feed": {"id": "f1", "fingerprint": "f1", "name": "Feed", "sort": "new"}, "permissions": {"manage": true}}""")
                    "/feeds/f1/-/posts" -> {
                        val first = url.queryParameter("cursor") == null && url.queryParameter("before") == null &&
                            url.queryParameter("offset") == null
                        when {
                            first && listFails -> MockResponse().setResponseCode(500).setBody("""{"error": "Broken"}""")
                            first -> ok("""{"posts": [{"id": "a", "feed": "f1"}, {"id": "b", "feed": "f1"}], "hasMore": true, "nextCursor": 1790000000, "cursor": "next"}""")
                            pageFails -> MockResponse().setResponseCode(500).setBody("""{"error": "Broken"}""")
                            else -> ok("""{"posts": [{"id": "c", "feed": "f1"}], "hasMore": false}""")
                        }
                    }
                    "/feeds/-/saved/list" -> ok("""{"saved": []}""")
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
            session.setBoundAccount("a1")
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
        collector?.cancel()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun count(path: String): Int = counts[path]?.get() ?: 0

    /** Runs the main thread until [done]; an assertion fails if it never is. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out, events $events")
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    /** The feed, opened and listed, with its one-shot events collected. */
    private fun open(): FeedViewModel {
        val model = FeedViewModel(
            SavedStateHandle(mapOf("feedId" to "f1")),
            repository,
            SavedRepository(api, Gson(), session),
            socket,
            session,
            context,
        )
        collector = CoroutineScope(Dispatchers.Main).launch { model.actionEvents.collect { events += it } }
        until { model.posts.value.size == 2 && !model.isLoading.value }
        settle()
        return model
    }

    private fun ids(model: FeedViewModel) = model.posts.value.map { post -> post.id }

    private fun failure(): FeedActionEvent.Failure {
        until { events.isNotEmpty() }
        return events.single() as FeedActionEvent.Failure
    }

    @Test
    fun `a next page that fails is kept as the failure, and the retry reads it`() {
        val model = open()
        pageFails = true
        model.loadMore()
        until { !model.isLoadingMore.value && count("/feeds/f1/-/posts") == 2 }
        settle()
        assertNotNull("THE POINT: the failure is kept to show, not dropped", model.loadMoreError.value)
        assertTrue("the list still has more to read", model.hasMore.value)
        assertFalse(model.isLoadingMore.value)

        pageFails = false
        model.loadMore()
        until { model.posts.value.size == 3 }
        assertNull("a page that reads clears the failure", model.loadMoreError.value)
    }

    @Test
    fun `a refused delete keeps the post and says why, with no reload`() {
        val model = open()
        val lists = count("/feeds/f1/-/posts")
        refused = setOf("/feeds/f1/-/a/delete")
        model.deletePost("a")
        assertNotNull("THE POINT: the refusal is said", failure().error)
        settle()
        assertEquals(listOf("a", "b"), ids(model))
        assertEquals("the feed is not read again from the top", lists, count("/feeds/f1/-/posts"))
    }

    @Test
    fun `a refused reaction says why, with no reload`() {
        val model = open()
        val lists = count("/feeds/f1/-/posts")
        refused = setOf("/feeds/f1/-/a/react")
        model.reactToPost("f1", "a", "like")
        assertNotNull("THE POINT: the refusal is said", failure().error)
        settle()
        assertEquals("", model.posts.value.first().myReaction)
        assertEquals("the feed is not read again from the top", lists, count("/feeds/f1/-/posts"))
    }

    @Test
    fun `a refused tag says why`() {
        val model = open()
        refused = setOf("/feeds/f1/-/a/tags/add")
        model.addTag("f1", "a", "harbour")
        assertNotNull("THE POINT: the refusal is said", failure().error)
    }

    @Test
    fun `a refused save says why`() {
        val model = open()
        refused = setOf("/feeds/-/saved/add")
        model.toggleSave(Post(id = "a", feed = "f1"))
        assertNotNull("THE POINT: the refusal is said", failure().error)
        assertFalse(model.savedIds.value.contains("a"))
    }

    @Test
    fun `a sort the server refuses to save goes back, and the list is not read in it`() {
        val model = open()
        val lists = count("/feeds/f1/-/posts")
        refused = setOf("/feeds/f1/-/sort/set")
        model.setSort("top")
        assertNotNull("the refusal is said", failure().error)
        settle()
        assertEquals("THE POINT: the previous sort is ticked again", "new", model.currentSort.value)
        assertEquals("the list is not read in the refused sort", lists, count("/feeds/f1/-/posts"))
    }

    @Test
    fun `a sort whose list cannot be read goes back, and is saved back`() {
        val model = open()
        listFails = true
        model.setSort("top")
        assertNotNull("the failure is said", failure().error)
        until { writes.count { (path, _) -> path == "/feeds/f1/-/sort/set" } == 2 }
        assertEquals("THE POINT: the previous sort is ticked again", "new", model.currentSort.value)
        assertEquals(listOf("sort=top", "sort=new"), writes.filter { (path, _) -> path == "/feeds/f1/-/sort/set" }.map { it.second })
        assertEquals(listOf("a", "b"), ids(model))
    }

    @Test
    fun `a tag filter whose list cannot be read goes back`() {
        val model = open()
        listFails = true
        model.setTag("harbour")
        assertNotNull("the failure is said", failure().error)
        assertNull("THE POINT: the posts shown are every post's, as the filter says again", model.currentTag.value)
    }

    @Test
    fun `unread only whose list cannot be read goes back`() {
        val model = open()
        listFails = true
        model.setUnreadOnly(true)
        assertNotNull("the failure is said", failure().error)
        assertFalse("THE POINT: the posts shown are every post's, as the menu says again", model.unreadOnly.value)
        assertFalse(
            context.getSharedPreferences("mochi_feeds", Context.MODE_PRIVATE).getBoolean("unread_only", true),
        )
    }

    @Test
    fun `revoking the feed's RSS token says it is done`() {
        val model = open()
        model.revokeRssToken()
        until { events.isNotEmpty() }
        assertEquals(
            FeedActionEvent.Done(org.mochios.android.R.string.rss_revoked),
            events.single(),
        )
        assertEquals(listOf("entity=f1"), writes.filter { (path, _) -> path == "/feeds/-/rss/token/revoke" }.map { it.second })
    }

    @Test
    fun `a refused revoke says why`() {
        val model = open()
        refused = setOf("/feeds/-/rss/token/revoke")
        model.revokeRssToken()
        assertTrue("THE POINT: the refusal reaches the reader as the server's error", failure().error is org.mochios.android.api.MochiError.ForbiddenError)
    }

    @Test
    fun `a refused revoke fails as the app's own error`() = runBlocking {
        refused = setOf("/feeds/-/rss/token/revoke")
        val failure = runCatching { repository.revokeRssToken("f1") }.exceptionOrNull()
        assertTrue("THE POINT: the repository says why as every other call does, not as a raw response", failure is org.mochios.android.api.MochiError.ForbiddenError)
    }

    @Test
    fun `the drawer keeps why its feeds could not be read, until a read succeeds`() {
        rosterFails = true
        val drawer = FeedListViewModel(repository, socket, session)
        until { drawer.error.value != null }
        rosterFails = false
        drawer.refresh()
        until { drawer.feeds.value.isNotEmpty() }
        settle()
        assertNull("THE POINT: a refresh that reads the feeds clears the failure the drawer shows", drawer.error.value)
    }
}
