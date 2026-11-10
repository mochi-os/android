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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * A feed's next page resumes where the server's cursor says: posts sharing a
 * second, and a list sorted by score, page exactly only by it. A server that
 * predates the cursor is asked by before, as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedPagingTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var session: SessionManager
    private lateinit var api: FeedsApi
    private lateinit var repository: FeedsRepository

    /** The cursor the first page answers with; null for a server without it. */
    @Volatile private var given: String? = null

    /** Each later page's request, as its query string. */
    private val asked = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        context.getSharedPreferences("mochi_feeds", Context.MODE_PRIVATE)
            .edit().putBoolean("unread_only", false).commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl
                return when (url?.encodedPath) {
                    "/feeds/-/info" -> ok("""{"feeds": [{"id": "f1", "fingerprint": "f1", "name": "Feed", "sort": "new"}], "settings": {"sort": "new"}}""")
                    "/feeds/f1/-/info" -> ok("""{"feed": {"id": "f1", "fingerprint": "f1", "name": "Feed", "sort": "new"}, "permissions": {}}""")
                    "/feeds/f1/-/posts" -> {
                        val first = url.queryParameter("before") == null && url.queryParameter("cursor") == null
                        if (first) {
                            val cursor = given?.let { """, "cursor": "$it"""" } ?: ""
                            ok("""{"posts": [{"id": "a", "feed": "f1"}, {"id": "b", "feed": "f1"}], "hasMore": true, "nextCursor": 1790000000$cursor}""")
                        } else {
                            asked.add(url.encodedQuery.orEmpty())
                            ok("""{"posts": [{"id": "c", "feed": "f1"}], "hasMore": false}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
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

    private fun until(state: () -> String = { "" }, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out: ${state()}" }
            Thread.sleep(10)
        }
    }

    /** Opens the feed, loads its next page, and answers that page's query. */
    private fun next(): Map<String, String?> {
        val model = FeedViewModel(
            SavedStateHandle(mapOf("feedId" to "f1")),
            repository,
            SavedRepository(api, Gson(), session),
            socket,
            session,
            context,
        )
        val state = { "posts ${model.posts.value.map { it.id }}, loading ${model.isLoading.value}, error ${model.error.value}" }
        until(state) { model.posts.value.size == 2 && !model.isLoading.value }
        model.loadMore()
        until(state) { model.posts.value.size == 3 }
        val query = asked.single()
        return query.split("&").associate { it.substringBefore("=") to java.net.URLDecoder.decode(it.substringAfter("="), "UTF-8") }
    }

    @Test
    fun `the next page resumes where the server's cursor says`() {
        given = "1790000000:b"
        val query = next()
        assertEquals("THE POINT: the server's cursor goes back as it came", "1790000000:b", query["cursor"])
        assertNull(query["before"])
    }

    @Test
    fun `a server that sends no cursor is asked by before`() {
        given = null
        val query = next()
        assertEquals("1790000000", query["before"])
        assertNull(query["cursor"])
    }
}
