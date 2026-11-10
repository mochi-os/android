// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.interests

import android.os.Looper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.api.InterestsApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Removing an interest, as the web has it: the row goes on the tap, before
 * the server answers, and comes back where it stood, with the error, if the
 * server refuses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InterestsRemoveTest {

    private lateinit var server: MockWebServer
    private lateinit var api: InterestsApi

    /** Holds every removal until released, so a test sees the list before the answer. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the removals it is sent. */
    @Volatile private var refuse = false

    /** The interests the server holds, in order. */
    private val held = ConcurrentLinkedQueue(listOf("q1", "q2", "q3"))

    /** How many removals the server has been sent. */
    @Volatile private var removals = 0

    /** How many times the list has been read. */
    @Volatile private var reads = 0

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path.orEmpty().substringBefore("?")) {
                    "/settings/-/user/interests" -> {
                        reads += 1
                        val rows = held.joinToString { qid -> """{"qid": "$qid", "label": "$qid", "weight": 50}""" }
                        MockResponse().setBody("""{"interests": [$rows], "summary": ""}""")
                    }
                    "/settings/-/user/interests/remove" -> {
                        removals += 1
                        val qid = request.body.readUtf8().substringAfter("qid=")
                        gate.await(5, TimeUnit.SECONDS)
                        if (refuse) {
                            MockResponse().setResponseCode(500).setBody("""{"error": "Refused"}""")
                        } else {
                            held.remove(qid)
                            MockResponse().setResponseCode(200)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InterestsApi::class.java)
    }

    @After
    fun end() {
        gate.countDown()
        server.shutdown()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun model(): InterestsViewModel {
        val model = InterestsViewModel(api)
        until { !model.uiState.value.isLoading }
        return model
    }

    private fun InterestsViewModel.shown() = uiState.value.interests.map { it.qid }

    @Test
    fun `an interest goes on the tap, and comes back where it stood when refused`() {
        val model = model()
        model.remove("q2")
        assertEquals(listOf("q1", "q3"), model.shown())
        until { removals == 1 }

        refuse = true
        gate.countDown()
        until { model.uiState.value.error != null }
        assertNotNull(model.uiState.value.error)
        assertEquals(listOf("q1", "q2", "q3"), model.shown())
    }

    @Test
    fun `an accepted removal stays, read back from the server`() {
        val model = model()
        model.remove("q2")
        gate.countDown()
        until { reads == 2 && !model.uiState.value.isLoading }
        assertEquals(listOf("q1", "q3"), model.shown())
        assertNull(model.uiState.value.error)
    }
}
