// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.repository

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.staff.api.StaffApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Setting a value, deleting a category and removing a team member answer
 * nothing the app reads: a success is a success whether the server answers no
 * data or `{"ok": true}`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmptyAnswerTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: StaffRepository

    /** The `data` every answer carries. */
    @Volatile private var data = "null"

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setBody("""{"data": $data}""")
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/staff/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StaffApi::class.java)
        repository = StaffRepository(api)
    }

    @After
    fun end() = server.shutdown()

    @Test
    fun `setting a value succeeds on an answer with no data`() {
        data = "null"
        runBlocking { repository.setConfig("fee", "5") }
    }

    @Test
    fun `deleting a category succeeds on an answer with no data`() {
        data = "null"
        runBlocking { repository.deleteCategory("c1") }
    }

    @Test
    fun `removing a team member succeeds on an answer with no data`() {
        data = "null"
        runBlocking { repository.removeTeamMember("m1") }
    }

    @Test
    fun `each succeeds on an answer of ok`() {
        data = """{"ok": true}"""
        runBlocking {
            repository.setConfig("fee", "5")
            repository.deleteCategory("c1")
            repository.removeTeamMember("m1")
        }
    }
}
