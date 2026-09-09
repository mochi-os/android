// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mochios.feeds.ui.component.applied
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * `post/react` and `comment/react` replace the viewer's reaction with whatever
 * they are given; only "none" (or an empty value) removes it. Re-sending the
 * current reaction — which the remove affordance used to do — therefore
 * re-applies it and the removal never reaches the server.
 */
class ReactionRemovalTest {
    private lateinit var server: MockWebServer
    private lateinit var api: FeedsApi

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FeedsApi::class.java)
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `removing a post reaction sends the removal sentinel`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"reaction":""}}"""))
        api.reactToPost("feed1", "post1", "none")
        val body = server.takeRequest().body.readUtf8()
        assertEquals("reaction=none", body)
    }

    @Test
    fun `removing a comment reaction sends the removal sentinel`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"reaction":""}}"""))
        api.reactToComment("feed1", "post1", "comment1", "none")
        val body = server.takeRequest().body.readUtf8()
        assertEquals("comment=comment1&reaction=none", body)
    }

    @Test
    fun `the removal sentinel clears the optimistic reaction, a real one sets it`() {
        assertEquals("", applied("none"))
        assertEquals("", applied(""))
        assertEquals("like", applied("like"))
    }
}
