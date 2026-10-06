// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.moderation

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.model.ModerationSettings
import org.mochios.forums.repository.ForumsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The moderation settings against a server: a reply that lands late leaves what changed meanwhile alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModerationFlowTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ForumsRepository
    private val asked = ConcurrentLinkedQueue<RecordedRequest>()

    /** Holds the settings' reply until released. */
    @Volatile private var reading: CountDownLatch? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked.add(request)
                return when (request.path.orEmpty().substringAfter("/forums/").substringBefore("?")) {
                    "f1/-/moderation/queue" -> ok("""{"posts": [], "comments": [], "reports": []}""")
                    "f1/-/moderation/settings/save" -> MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
                    "f1/-/moderation/settings" -> {
                        reading?.await(10, TimeUnit.SECONDS)
                        ok("""{"settings": {"moderation_posts": true}}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = ForumsRepository(retrofit.create(ForumsApi::class.java), FileStore(context))
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /** Runs the main thread until [done], the server answering on its own. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a failed save's error, once cleared, stays cleared when the settings come back`() {
        val model = ModerationViewModel(SavedStateHandle(mapOf("forumId" to "f1")), repository)
        until { !model.uiState.value.isLoading }
        val held = CountDownLatch(1)
        reading = held
        model.saveSettings(ModerationSettings(moderationComments = true))
        until { model.uiState.value.error != null && asked.any { it.path.orEmpty().endsWith("/moderation/settings") } }
        // The snackbar has shown it.
        model.clearError()
        held.countDown()
        until { model.uiState.value.settings?.moderationPosts == true }
        assertNull(model.uiState.value.error)
    }
}
