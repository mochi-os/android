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
 * A tag picked from a post filters the posts, in one feed and in All feeds
 * alike, as on the web; and opening All feeds clears every feed's
 * notifications, as opening one feed clears its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedTagFilterTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var socket: MochiWebSocket
    private lateinit var model: FeedViewModel

    /** The tag each list was asked for with, "" for none. */
    private val tags = ConcurrentLinkedQueue<String>()

    /** Each path a notification clear was posted to. */
    private val clears = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when {
                    path == "/feeds/f1/-/info" ->
                        ok("""{"feed": {"id": "f1", "fingerprint": "f1", "name": "Feed", "sort": "new"}, "permissions": {}}""")
                    path == "/feeds/-/info" -> ok("""{"settings": {"sort": "new"}}""")
                    path == "/feeds/f1/-/posts" || path == "/feeds/-/posts" -> {
                        val tag = request.requestUrl?.queryParameter("tag").orEmpty()
                        tags.add(tag)
                        val ids = if (tag == "harbour") listOf("tagged") else listOf("tagged", "other")
                        ok("""{"posts": [${ids.joinToString { id -> post(id) }}]}""")
                    }
                    path.endsWith("/notifications/clear") -> {
                        clears.add(path)
                        ok("""{"success": true}""")
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

    private fun post(id: String) = """{"id": "$id", "feed": "f1", "feed_fingerprint": "f1", "body": "$id"}"""

    /** Opens [feed] and waits for its first list. */
    private fun open(feed: String) {
        context.getSharedPreferences("mochi_feeds", Context.MODE_PRIVATE)
            .edit().putBoolean("unread_only", false).commit()
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
            FeedsRepository(api, retrofit.create(MenuApi::class.java), FileStore(context), context, session),
            SavedRepository(api, Gson(), session),
            socket,
            session,
            context,
        )
        until { ids() == listOf("tagged", "other") }
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out with ${ids()}, tags $tags, error ${model.error.value}" }
            Thread.sleep(10)
        }
    }

    private fun ids() = model.posts.value.map { post -> post.id }

    private fun filtersBy(feed: String) {
        open(feed)
        assertNull(model.currentTag.value)

        model.setTag("harbour")
        until { ids() == listOf("tagged") }
        assertEquals("harbour", model.currentTag.value)
        assertEquals("harbour", tags.last())

        // Picking the tag filtered by again shows every post, as does clearing.
        model.setTag("harbour")
        until { ids() == listOf("tagged", "other") }
        assertNull(model.currentTag.value)
        assertEquals("", tags.last())

        model.setTag("harbour")
        until { ids() == listOf("tagged") }
        model.setTag(null)
        until { ids() == listOf("tagged", "other") }
        assertEquals("", tags.last())
    }

    @Test
    fun `All feeds lists the posts carrying a tag picked from a post`() = filtersBy("__all__")

    @Test
    fun `one feed lists the posts carrying a tag picked from a post`() = filtersBy("f1")

    @Test
    fun `opening All feeds clears every feed's notifications`() {
        open("__all__")
        model.clearNotifications()
        until { clears.isNotEmpty() }
        assertEquals(listOf("/feeds/-/notifications/clear"), clears.toList())
    }

    @Test
    fun `opening one feed clears its own notifications`() {
        open("f1")
        model.clearNotifications()
        until { clears.isNotEmpty() }
        assertEquals(listOf("/feeds/f1/-/notifications/clear"), clears.toList())
    }
}
