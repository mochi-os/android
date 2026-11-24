// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * What a socket's opening drains from the server's queue is delivered on the
 * one queue live pushes are, one at a time in order, and acked after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DrainQueueTest {
    private lateinit var server: MockWebServer
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "deliveries") }
    private val deliveries = executor.asCoroutineDispatcher()

    @Before
    fun begin() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun end() {
        server.shutdown()
        executor.shutdownNow()
    }

    @Test
    fun `drained pushes are delivered on the deliveries queue, then acked`() {
        server.enqueue(
            MockResponse().setBody(
                """{"data": [
                    {"subscription": "s1", "payload": "{\"title\": \"one\"}", "account": "a1", "event": "e1"},
                    {"subscription": "s1", "payload": "{\"title\": \"two\"}", "account": "a1", "event": "e2"}
                ]}"""
            )
        )
        server.enqueue(MockResponse().setBody("{}"))
        val delivered = mutableListOf<Pair<String, String>>()
        drainQueue(OkHttpClient(), server.url("/").toString(), "token1", deliveries) { _, payload ->
            // The coroutine debugger names the coroutine after the thread.
            delivered += Thread.currentThread().name.substringBefore(" @") to payload
        }
        assertEquals(
            "THE POINT: on the deliveries queue, in order",
            listOf("deliveries" to """{"title": "one"}""", "deliveries" to """{"title": "two"}"""),
            delivered,
        )
        assertEquals("/notifications/-/push/drain", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        val ack = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/notifications/-/push/ack", ack.path)
        val events = JSONArray(URLDecoder.decode(ack.body.readUtf8().removePrefix("events="), "UTF-8"))
        assertEquals(listOf("e1", "e2"), (0 until events.length()).map { i -> events.getJSONObject(i).getString("event") })
    }
}
