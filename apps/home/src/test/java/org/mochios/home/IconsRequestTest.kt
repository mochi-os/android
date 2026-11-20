// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.home.api.HomeApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The grid draws the glyphs built into the client, so it asks for the list
 * without each app's icon inlined, which the web page alone needs.
 */
class IconsRequestTest {

    @Test
    fun `the grid asks for the apps without their icons' data`() {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody("""{"icons": [{"id": "feeds", "link": "feeds"}]}"""))
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(HomeApi::class.java)
            val answer = runBlocking { api.icons() }
            assertEquals("feeds", answer.body()!!.icons.single().link)
            assertEquals("/-/icons?data=false", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        } finally {
            server.shutdown()
        }
    }
}
