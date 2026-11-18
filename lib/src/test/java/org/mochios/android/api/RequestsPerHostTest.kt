// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import androidx.test.core.app.ApplicationProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
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
import org.mochios.android.auth.SessionManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A module's client, built from the shared one, sends a screen's reads to the
 * server at once: an opened project item asks for its object and eight tabs,
 * and OkHttp's default of five at a time per host held four of them back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RequestsPerHostTest {

    private lateinit var server: MockWebServer

    /** How many reads a screen sends at once: an item's object and its eight tabs. */
    private val reads = 9

    /** Counted down as each read arrives; every answer waits for all of them. */
    private val arrived = CountDownLatch(reads)

    /** How many answers saw every read arrive while they waited. */
    private val together = AtomicInteger()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                arrived.countDown()
                if (arrived.await(3, TimeUnit.SECONDS)) together.incrementAndGet()
                return MockResponse().setBody("{}")
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    @Test
    fun `a module's client sends nine reads to the server at once`() {
        val pass = Interceptor { chain -> chain.proceed(chain.request()) }
        val shared = ApiClient.provideOkHttpClient(pass, pass, pass, SessionManager(ApplicationProvider.getApplicationContext()))
        val client = shared.newBuilder().build()
        val answered = CountDownLatch(reads)
        repeat(reads) { index ->
            client.newCall(Request.Builder().url(server.url("/projects/p1/-/read$index")).build())
                .enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) = answered.countDown()

                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                        answered.countDown()
                    }
                })
        }
        assertTrue(answered.await(15, TimeUnit.SECONDS))
        assertEquals("THE POINT: some of the reads waited for others to answer", reads, together.get())
    }
}
