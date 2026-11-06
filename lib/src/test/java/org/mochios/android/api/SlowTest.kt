// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.http.GET
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * A call marked [Slow] waits for an answer past the client's usual read
 * timeout, as an import within the server's budget does; any other call still
 * gives up at it.
 */
class SlowTest {

    private interface ProbeApi {
        @Slow
        @GET("slow")
        suspend fun slow(): ResponseBody

        @GET("plain")
        suspend fun plain(): ResponseBody
    }

    private lateinit var server: MockWebServer
    private lateinit var api: ProbeApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .addInterceptor(slowInterceptor)
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .build()
            .create(ProbeApi::class.java)
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun late(body: String) =
        MockResponse().setBody(body).setHeadersDelay(2, TimeUnit.SECONDS)

    @Test
    fun `a slow call waits past the client's read timeout for its answer`() {
        server.enqueue(late("done"))
        assertEquals("done", runBlocking { api.slow().string() })
    }

    @Test
    fun `any other call still gives up at the read timeout`() {
        server.enqueue(late("done"))
        try {
            runBlocking { api.plain().string() }
            fail("an unmarked call waited past its read timeout")
        } catch (_: InterruptedIOException) {
        }
    }
}
