// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.repository.SavedRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * A change of sort while a page is in flight: the page asked under the old
 * sort must not land in the new sort's list, and a next page must not carry
 * one sort's cursor under another. The server's cursor names its sort, as
 * `sort:last`, so the mock can tell a mismatched one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumSortRaceTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var api: ForumsApi
    private lateinit var session: SessionManager

    /** Holds the answer to a next page until released. */
    @Volatile private var holdNext: CountDownLatch? = null

    /** Holds the answer to the top sort's first page until released. */
    @Volatile private var holdTop: CountDownLatch? = null

    /** Each next page's request, as `sort|cursor`. */
    private val asked = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(404)
                return when (url.encodedPath) {
                    "/forums/f1/-/posts" -> posts(
                        sort = url.queryParameter("sort") ?: "new",
                        cursor = url.queryParameter("cursor"),
                        before = url.queryParameter("before"),
                    )
                    "/forums/-/information" -> ok("""{"forums": [], "settings": {"sort": ""}}""")
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
    }

    @After
    fun end() {
        holdNext?.countDown()
        holdTop?.countDown()
        socket.disconnectAll()
        server.shutdown()
    }

    private fun posts(sort: String, cursor: String?, before: String?): MockResponse {
        if (cursor == null && before == null) {
            if (sort == "top") {
                holdTop?.await(5, TimeUnit.SECONDS)
                return page(sort, listOf("x", "y"), more = true, cursor = "top:y")
            }
            return page(sort, listOf("a", "b"), more = true, cursor = "new:b")
        }
        asked.add("$sort|$cursor")
        holdNext?.await(5, TimeUnit.SECONDS)
        return page(sort, listOf("c"), more = false, cursor = null)
    }

    private fun page(
        sort: String,
        ids: List<String>,
        more: Boolean,
        cursor: String?,
    ): MockResponse {
        val items = ids.joinToString(", ") { id -> """{"id": "$id"}""" }
        val next = cursor?.let { value -> """, "cursor": "$value"""" } ?: ""
        val forum = """{"id": "f1", "fingerprint": "fp1", "name": "Forum", "sort": "$sort"}"""
        return ok(
            """{"forum": $forum, "posts": [$items], "hasMore": $more,""" +
                """ "nextCursor": 1790000000$next}""",
        )
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(state: () -> String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out: ${state()}" }
            Thread.sleep(10)
        }
    }

    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun open(): Pair<ForumViewModel, () -> String> {
        val model = ForumViewModel(
            SavedStateHandle(mapOf("forumId" to "f1")),
            ForumsRepository(api, FileStore(context), context, session),
            SavedRepository(api, Gson(), session),
            socket,
            session,
        )
        val state = {
            val ui = model.uiState.value
            "posts ${ui.posts.map { post -> post.id }}, loading ${ui.isLoading}, " +
                "more ${ui.hasMore}, error ${ui.error}, asked $asked"
        }
        until(state) { model.uiState.value.posts.size == 2 && !model.uiState.value.isLoading }
        return model to state
    }

    @Test
    fun `a page asked under the old sort is not added to the new sort's list`() {
        val (model, state) = open()
        val release = CountDownLatch(1)
        holdNext = release

        model.loadMore()
        until(state) { asked.isNotEmpty() }
        model.setSort("top")
        until(state) { model.uiState.value.posts.map { post -> post.id } == listOf("x", "y") }
        release.countDown()
        settle()

        val ui = model.uiState.value
        assertEquals(
            "THE POINT: the old sort's page stays out of the new sort's list",
            listOf("x", "y"),
            ui.posts.map { post -> post.id },
        )
        assertTrue("the old sort's last page must not end the new sort's paging", ui.hasMore)
        assertEquals("top:y", ui.cursor)
        assertNull("a page dropped for a new sort is no error", ui.error)
    }

    @Test
    fun `a next page never carries one sort's cursor under another`() {
        val (model, state) = open()
        val release = CountDownLatch(1)
        holdTop = release

        model.setSort("top")
        until(state) { model.uiState.value.isLoading }
        model.loadMore()
        settle()
        release.countDown()
        until(state) {
            model.uiState.value.posts.map { post -> post.id }.take(2) == listOf("x", "y")
        }
        settle()

        val mismatched = asked.filter { entry ->
            val sort = entry.substringBefore("|")
            val cursor = entry.substringAfter("|")
            cursor != "null" && !cursor.startsWith("$sort:")
        }
        assertEquals(
            "THE POINT: every next page's cursor came from the sort it is asked under",
            emptyList<String>(),
            mismatched,
        )
    }
}
