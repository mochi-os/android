// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * `comment/create` answers the new comment's id, its feed and its post, not
 * the comment itself. Creating one answers that id, read from the shape the
 * server sends, rather than a comment the answer never held.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommentCreateTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: FeedsRepository

    @Before
    fun begin() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = FeedsRepository(
            retrofit.create(FeedsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
            context,
            SessionManager(context),
        )
    }

    @After
    fun end() = server.shutdown()

    @Test
    fun `a new comment answers its id`() = runBlocking {
        // The answer action_comment_create gives for a feed held here.
        server.enqueue(
            MockResponse().setBody("""{"data": {"id": "c9", "feed": {"id": "f1", "name": "Feed"}, "post": "p1"}}"""),
        )
        val id = repository.createComment(feedId = "f1", postId = "p1", body = "Hello", files = emptyList())
        assertEquals("THE POINT: the new comment's id, as the server sends it", "c9", id)
    }
}
