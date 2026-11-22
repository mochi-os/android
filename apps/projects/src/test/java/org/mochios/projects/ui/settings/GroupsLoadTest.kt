// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.settings

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.repository.ProjectsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The access dialog's groups: loading from the moment they are asked for, so
 * the dialog shows a spinner rather than "no groups", and a failed read kept
 * for the dialog to show rather than taken for a project with none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GroupsLoadTest {

    private val context = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private lateinit var repository: ProjectsRepository

    /** Whether the server can read the groups. */
    @Volatile private var readable = false

    @Before
    fun begin() {
        AppContext.set(context)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringAfter("/projects/").substringBefore("?")) {
                    "-/groups" -> if (readable) {
                        MockResponse().setBody("""{"data": {"groups": [{"id": "g1", "name": "Friends"}]}}""")
                    } else {
                        MockResponse().setResponseCode(500).setBody("""{"error": "Groups unavailable"}""")
                    }
                    else -> MockResponse().setBody("""{"data": {}}""")
                }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProjectsApi::class.java)
        repository = ProjectsRepository(api, FileStore(context), context, SessionManager(context))
    }

    @After
    fun end() = server.shutdown()

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun model() = ProjectSettingsViewModel(SavedStateHandle(mapOf("projectId" to "p1")), repository)

    @Test
    fun `groups load at once, and groups that cannot be read are said, not taken for none`() {
        val model = model()
        model.loadGroups()
        assertTrue("THE POINT: loading from the call, before the answer", model.uiState.value.isLoadingGroups)
        until { !model.uiState.value.isLoadingGroups }
        assertEquals(MochiError.ServerError(500, "Groups unavailable"), model.uiState.value.groupsError)
        assertTrue(model.uiState.value.groups.isEmpty())
    }

    @Test
    fun `groups that load are kept with no failure`() {
        readable = true
        val model = model()
        model.loadGroups()
        until { !model.uiState.value.isLoadingGroups }
        assertEquals(listOf("Friends"), model.uiState.value.groups.map { group -> group.name })
        assertEquals(null, model.uiState.value.groupsError)
    }
}
