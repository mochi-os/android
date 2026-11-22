// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.api

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
import org.mochios.projects.repository.ProjectsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** A recommended project's card reads the blurb the recommendations carry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecommendationsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()

    @Before
    fun begin() = server.start()

    @After
    fun end() = server.shutdown()

    @Test
    fun `a recommendation's blurb is what its card describes it by`() {
        server.enqueue(
            MockResponse().setBody(
                """{"data": {"projects": [{"id": "p1", "name": "Roadmap", "blurb": "What we build next", "fingerprint": "abc123def", "server": "https://example.org"}]}}"""
            )
        )
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        val repository = ProjectsRepository(api, FileStore(context), context, SessionManager(context))
        val project = runBlocking { repository.getRecommendations() }.single()
        assertEquals("THE POINT: the blurb", "What we build next", project.description)
        assertEquals("abc123def", project.fingerprint)
        assertEquals("https://example.org", project.server)
    }
}
