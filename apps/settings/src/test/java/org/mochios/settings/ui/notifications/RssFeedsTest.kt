// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.AppContext
import org.mochios.android.notifications.NotificationsApi
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.settings.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The RSS feeds of the notifications, as the web's dialog has them: listed
 * with their addresses, created, renamed and deleted with the server's say,
 * and the "Notify by default" switch moving on the tap and back with the
 * error if the server refuses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RssFeedsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var model: RssFeedsViewModel
    private lateinit var base: String

    /** Holds the switch's save until released, so a test sees it before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the writes it is sent. */
    @Volatile private var refuse = false

    /** Each write the server has been sent: its path and body. */
    private val writes = ConcurrentLinkedQueue<Pair<String, String>>()

    /** The feeds the server lists. */
    @Volatile private var listed = listOf(feed("r1", "Reader", 1), feed("r2", "Other", 0))

    private val events = mutableListOf<RssFeedsEvent>()
    private var collector: Job? = null

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                if (path == "/notifications/-/rss/list") return ok("[${listed.joinToString()}]")
                if (!path.startsWith("/notifications/-/rss/")) return MockResponse().setResponseCode(404)
                writes.add(path to request.body.readUtf8())
                if (path.endsWith("/update")) gate.await(5, TimeUnit.SECONDS)
                if (refuse) return MockResponse().setResponseCode(400).setBody("""{"error": "Feed name is too long"}""")
                return when (path) {
                    "/notifications/-/rss/create" -> {
                        listed = listed + feed("r3", "New", 1)
                        ok(feed("r3", "New", 1))
                    }
                    else -> ok("{}")
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        val api = Retrofit.Builder()
            .baseUrl(server.url("/notifications/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationsApi::class.java)
        val session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
        model = RssFeedsViewModel(NotificationsRepository(api), session)
        collector = CoroutineScope(Dispatchers.Main).launch { model.events.collect { events += it } }
        until { !model.uiState.value.isLoading }
    }

    @After
    fun end() {
        gate.countDown()
        collector?.cancel()
        server.shutdown()
    }

    private fun feed(id: String, name: String, enabled: Int) =
        """{"id": "$id", "name": "$name", "token": "t-$id", "created": 1, "enabled": $enabled}"""

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out with ${model.uiState.value}" }
            Thread.sleep(10)
        }
    }

    private fun enabled(id: String) = model.uiState.value.feeds.single { it.id == id }.enabled

    private fun sent(path: String) = writes.firstOrNull { it.first == "/notifications/-/rss/$path" }?.second

    @Test
    fun `the feeds list with the address a reader subscribes to`() {
        assertEquals(listOf("Reader", "Other"), model.uiState.value.feeds.map { it.name })
        assertEquals("$base/notifications/rss?token=t-r1", model.address(model.uiState.value.feeds.first()))
    }

    @Test
    fun `the switch moves on the tap, and back with the error when refused`() {
        val feed = model.uiState.value.feeds.first()
        model.setEnabled(feed, false)
        assertEquals(0, enabled("r1"))
        until { sent("update") != null }
        assertEquals("id=r1&enabled=0", sent("update"))

        refuse = true
        gate.countDown()
        until { events.isNotEmpty() }
        assertTrue(events.single() is RssFeedsEvent.Failure)
        assertEquals(1, enabled("r1"))
    }

    @Test
    fun `an accepted switch stays`() {
        model.setEnabled(model.uiState.value.feeds.last(), true)
        gate.countDown()
        until { sent("update") != null }
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
        assertEquals(1, enabled("r2"))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a created feed shows its address, and joins the list`() {
        var closed = false
        model.create("New", existing = false) { closed = true }
        until { model.uiState.value.created != null && model.uiState.value.feeds.size == 3 }
        assertTrue(closed)
        assertEquals("name=New&existing=0", sent("create"))
        assertEquals("$base/notifications/rss?token=t-r3", model.address(model.uiState.value.created!!))
        model.dismissCreated()
        assertEquals(null, model.uiState.value.created)
    }

    @Test
    fun `a refused create says why, as the server words it`() {
        refuse = true
        var closed = false
        model.create("x".repeat(101), existing = true) { closed = true }
        until { events.isNotEmpty() }
        val failure = events.single() as RssFeedsEvent.Failure
        assertEquals("Feed name is too long", failure.error.userMessage())
        assertEquals(false, closed)
        assertEquals(emptySet<String>(), model.uiState.value.busy)
    }

    @Test
    fun `a rename takes the trimmed name, and a blank or unchanged one sends nothing`() {
        val feed = model.uiState.value.feeds.first()
        model.rename(feed, "   ")
        model.rename(feed, "Reader")
        model.rename(feed, "  Renamed ")
        until { events.isNotEmpty() }
        assertEquals(1, writes.size)
        assertEquals("id=r1&name=Renamed", sent("rename"))
        assertEquals(RssFeedsEvent.Done(R.string.notifications_rss_renamed), events.single())
        assertEquals("Renamed", model.uiState.value.feeds.first().name)
    }

    @Test
    fun `a deleted feed leaves the list`() {
        model.delete(model.uiState.value.feeds.first())
        until { events.isNotEmpty() }
        assertEquals("id=r1", sent("delete"))
        assertEquals(RssFeedsEvent.Done(R.string.notifications_rss_deleted), events.single())
        assertEquals(listOf("r2"), model.uiState.value.feeds.map { it.id })
    }

    @Test
    fun `a list the server refuses is the page error`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500).setBody("""{"error": "Down"}""")
        }
        model.load()
        until { model.uiState.value.error != null }
        assertTrue(model.uiState.value.error is MochiError)
    }
}
