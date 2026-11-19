// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.files.FileStore
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.repository.WikisRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * A wiki, page or tag is one segment of the request path. A link in a page's
 * body becomes a slug, so one written `home/delete` would otherwise reach the
 * page's delete action with the reader's token.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikisRepositoryPathTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: WikisRepository

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = WikisRepository(
            retrofit.create(WikisApi::class.java),
            FileStore(RuntimeEnvironment.getApplication()),
            RuntimeEnvironment.getApplication(),
        )
    }

    @After
    fun stop() = server.shutdown()

    private fun refused(call: suspend () -> Unit) = runBlocking {
        try {
            call()
            fail("the call was made")
        } catch (e: MochiError.NotFoundError) {
            // The page is answered as not there.
        }
    }

    @Test
    fun `a slug that is more than one segment is answered as not found and never sent`() {
        refused { repository.getPage("abcdefghi", "home/delete") }
        refused { repository.deletePage("abcdefghi", "home/delete") }
        refused { repository.getPage("abcdefghi", "..") }
        refused { repository.getPage("abcdefghi", ".") }
        refused { repository.getPage("abcdefghi", "") }
        refused { repository.getPagesForTag("abcdefghi", "news/../delete") }
        refused { repository.getInfo("abcdefghi/-/delete") }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a slug of one segment is sent escaped`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data": {}}"""))
        server.enqueue(MockResponse().setBody("""{"data": {}}"""))
        runCatching { repository.getPage("abcdefghi", "changelog") }
        runCatching { repository.getPage("abcdefghi", "café?x") }
        assertEquals("/wikis/abcdefghi/-/pages/changelog", server.takeRequest().path)
        val escaped = server.takeRequest().path ?: ""
        assertTrue(escaped, escaped == "/wikis/abcdefghi/-/pages/caf%C3%A9%3Fx")
    }
}
