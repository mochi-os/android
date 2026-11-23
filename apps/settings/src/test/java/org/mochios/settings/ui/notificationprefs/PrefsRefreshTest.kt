// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import android.os.Looper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.settings.api.NotificationPrefsApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Notification preferences keep their lists on screen while they are read again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsRefreshTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val categories = AtomicInteger()
    private val topics = AtomicInteger()

    /** Holds each read after the first back until released, when set. */
    @Volatile private var gate: CountDownLatch? = null

    /** Whether the topics are refused. */
    @Volatile private var topicsRefused = false

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/notifications/categories" -> {
                        if (categories.incrementAndGet() > 1) gate?.await(5, TimeUnit.SECONDS)
                        json("""[{"id": "1", "label": "Normal"}]""")
                    }
                    "settings/-/notifications/destinations" -> json("{}")
                    "settings/-/notifications/topics" -> {
                        if (topics.incrementAndGet() > 1) gate?.await(5, TimeUnit.SECONDS)
                        if (topicsRefused) json("""{"error": "Topics unavailable"}""", 500)
                        else json("""[{"app": {"id": "feeds", "name": "Feeds"}, "topic": "post", "object": "f1", "category": "1"}]""")
                    }
                    "settings/-/notifications/topics/set/category" -> json("{}")
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() {
        gate?.countDown()
        server.shutdown()
    }

    private fun model(): NotificationPrefsViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationPrefsApi::class.java)
        val made = NotificationPrefsViewModel(api)
        until { !made.uiState.value.isLoading && made.uiState.value.categories.isNotEmpty() }
        return made
    }

    /** Moving a topic used to blank both tabs to a spinner while they were read again. */
    @Test
    fun `a change reads the lists again without blanking them`() {
        val model = model()
        model.setTab(NotifTab.TOPICS)
        until { model.uiState.value.topicsLoaded }
        gate = CountDownLatch(1)
        val topic = model.uiState.value.topics.single()
        model.setTopicCategory(topic, null)
        until { categories.get() >= 2 && topics.get() >= 2 }
        assertFalse(model.uiState.value.isLoading)
        assertEquals(1, model.uiState.value.categories.size)
        assertEquals(1, model.uiState.value.topics.size)
        gate!!.countDown()
    }

    /** A first read of the topics that failed used to read "No topics" once its snackbar went. */
    @Test
    fun `a failed first read of the topics is kept for the tab, not shown as none`() {
        topicsRefused = true
        val model = model()
        model.setTab(NotifTab.TOPICS)
        until { model.uiState.value.topicsError != null }
        assertFalse(model.uiState.value.topicsLoaded)
        assertNull("not the list's snackbar error", model.uiState.value.error)
        assertNotNull(model.uiState.value.topicsError)
    }
}
