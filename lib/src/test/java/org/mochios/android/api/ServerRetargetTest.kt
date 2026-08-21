// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.http.GET

/**
 * Retrofit fixes its `baseUrl` at build time, and every Retrofit here is a
 * `@Singleton` built before the user has chosen a server. These cover the
 * retarget that makes the origin follow the current server anyway.
 */
class ServerRetargetTest {

    private interface ProbeApi {
        @GET("_/auth/methods")
        suspend fun methods(): okhttp3.ResponseBody
    }

    private lateinit var serverA: MockWebServer
    private lateinit var serverB: MockWebServer

    @Before
    fun start() {
        serverA = MockWebServer().apply { start() }
        serverB = MockWebServer().apply { start() }
    }

    @After
    fun stop() {
        serverA.shutdown()
        serverB.shutdown()
    }

    // ---------------- the rewrite itself ----------------

    @Test
    fun `retargets origin and keeps the path`() {
        val out = retargetToServer(
            "https://mochi-os.org/feeds/x?page=2".toHttpUrl(),
            "https://self.hosted.example",
        )
        assertEquals("https://self.hosted.example/feeds/x?page=2", out.toString())
    }

    @Test
    fun `retargets scheme and port too`() {
        val out = retargetToServer("https://mochi-os.org/x".toHttpUrl(), "http://localhost:8080")
        assertEquals("http://localhost:8080/x", out.toString())
    }

    @Test
    fun `leaves a url already on the server untouched`() {
        val url = "https://self.hosted.example/feeds/x".toHttpUrl()
        assertEquals(url, retargetToServer(url, "https://self.hosted.example"))
        assertEquals(url, retargetToServer(url, "https://self.hosted.example/"))
    }

    @Test
    fun `unusable server url leaves the request as built`() {
        val url = "https://mochi-os.org/x".toHttpUrl()
        assertEquals(url, retargetToServer(url, ""))
        assertEquals(url, retargetToServer(url, "not a url"))
    }

    // ---------------- the scenario the task asks for ----------------

    /**
     * Start on server A, select server B, prove the next request reaches only
     * B — with the Retrofit still pinned to A, which is the real defect.
     */
    @Test
    fun `selecting a new server moves the next request off the pinned one`() {
        var current = serverA.url("/").toString()
        val client = OkHttpClient.Builder()
            .addInterceptor(serverInterceptor { current })
            .build()
        // Pinned to A at construction, exactly as the singletons are.
        val api = Retrofit.Builder()
            .baseUrl(serverA.url("/"))
            .client(client)
            .build()
            .create(ProbeApi::class.java)

        serverA.enqueue(MockResponse().setBody("{}"))
        runBlocking { api.methods() }
        assertEquals(1, serverA.requestCount)
        assertEquals(0, serverB.requestCount)

        // The user picks their own server. Retrofit is untouched and still
        // pinned to A.
        current = serverB.url("/").toString()

        serverB.enqueue(MockResponse().setBody("{}"))
        runBlocking { api.methods() }
        assertEquals("B must receive it", 1, serverB.requestCount)
        assertEquals("A must not see it", 1, serverA.requestCount)
        assertEquals("/_/auth/methods", serverB.takeRequest().path)
    }

    /**
     * Negative control. Without the interceptor the same sequence keeps hitting
     * the pinned server, so the assertions above are not passing vacuously.
     */
    @Test
    fun `without the interceptor the request stays on the pinned server`() {
        val api = Retrofit.Builder()
            .baseUrl(serverA.url("/"))
            .client(OkHttpClient())
            .build()
            .create(ProbeApi::class.java)

        // "Select" B — with no interceptor there is nothing to act on it.
        serverA.enqueue(MockResponse().setBody("{}"))
        runBlocking { api.methods() }

        assertEquals("reproduces the bug: A still gets it", 1, serverA.requestCount)
        assertNotEquals("B never hears about the change", 1, serverB.requestCount)
    }

    private fun <T> runBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
