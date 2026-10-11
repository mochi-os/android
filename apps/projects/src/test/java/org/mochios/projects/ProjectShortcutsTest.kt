// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects

import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.android.launcher.Shortcuts
import org.mochios.projects.api.ProjectsApi
import org.mochios.projects.repository.ProjectsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Reading the projects brings those pinned to the home screen into line with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectShortcutsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val server = MockWebServer()

    @Before
    fun begin() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().substringBefore("?") == "/projects/-/list") {
                    MockResponse().setBody("""{"data": {"projects": [{"id": "p1", "fingerprint": "fp1", "name": "Plans"}, {"id": "p2", "fingerprint": "fp2", "name": "Other"}]}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun repository(): ProjectsRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/projects/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return ProjectsRepository(retrofit.create(ProjectsApi::class.java), FileStore(context), context)
    }

    /** The shortcuts the launcher shows greyed out. */
    private fun disabled(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
            ShadowShortcutManager::class.java,
            "disabledPinnedShortcuts",
        ).keys

    @Test
    fun `reading the projects turns off a gone project's shortcut and renames a kept one's`() = runBlocking {
        Shortcuts.pinItem(context, "projects", "p1", "Old name")
        Shortcuts.pinItem(context, "projects", "p9", "Gone")
        Shortcuts.pinItem(context, "projects", "fp2", "By its other name")
        repository().listProjects()
        assertEquals(setOf("projects_p9"), disabled())
        assertEquals("Plans", manager.pinnedShortcuts.first { it.id == "projects_p1" }.shortLabel.toString())
    }
}
