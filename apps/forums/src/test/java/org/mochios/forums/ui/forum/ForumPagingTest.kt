// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forum

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
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.repository.SavedRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * A forum's next page resumes where the server's cursor says: posts sharing a
 * second, a list sorted by score, and pinned posts page exactly only by it. A
 * server that predates the cursor is asked by before, as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumPagingTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var api: ForumsApi
    private lateinit var session: SessionManager

    /** The cursor the first page answers with; null for a server without it. */
    @Volatile private var given: String? = null

    /** Each later page's request, as its query string. */
    private val asked = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl
                return when (url?.encodedPath) {
                    "/forums/f1/-/posts" -> {
                        val first = url.queryParameter("before") == null && url.queryParameter("cursor") == null
                        if (first) {
                            val cursor = given?.let { """, "cursor": "$it"""" } ?: ""
                            ok("""{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Forum", "sort": "new"}, "posts": [{"id": "a"}, {"id": "b"}], "hasMore": true, "nextCursor": 1790000000$cursor}""")
                        } else {
                            asked.add(url.encodedQuery.orEmpty())
                            ok("""{"forum": {"id": "f1", "fingerprint": "fp1", "name": "Forum", "sort": "new"}, "posts": [{"id": "c"}], "hasMore": false}""")
                        }
                    }
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
        socket.disconnectAll()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Opens the forum, loads its next page, and answers that page's query. */
    private fun next(): Map<String, String?> {
        val model = ForumViewModel(
            SavedStateHandle(mapOf("forumId" to "f1")),
            ForumsRepository(api, FileStore(context), context, session),
            SavedRepository(api, Gson(), session),
            socket,
            session,
        )
        until { model.uiState.value.posts.size == 2 && !model.uiState.value.isLoading }
        model.loadMore()
        until { model.uiState.value.posts.size == 3 }
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
