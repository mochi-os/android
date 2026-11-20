// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.repository

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.forums.api.ForumsApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Clearing a forum's notifications answers no data: the clear succeeds all
 * the same, and only a refusal is a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumAnswersTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ForumsRepository

    @Before
    fun begin() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForumsApi::class.java)
        repository = ForumsRepository(api, FileStore(context), context, SessionManager(context))
    }

    @After
    fun end() = server.shutdown()

    private fun clear(): Throwable? = runCatching { runBlocking { repository.clearNotifications("f1") } }.exceptionOrNull()

    @Test
    fun `a clear answered with null data succeeds`() {
        server.enqueue(MockResponse().setBody("""{"data": null}"""))
        assertEquals(null, clear())
        assertEquals("/forums/f1/-/notifications/clear", server.takeRequest().path)
    }

    @Test
    fun `a clear answered with an empty object succeeds`() {
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals(null, clear())
    }

    @Test
    fun `a refused clear fails`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error": "Not allowed"}"""))
        assertTrue(clear() != null)
    }
}
