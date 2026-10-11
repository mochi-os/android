// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis

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
import org.mochios.wikis.api.WikisApi
import org.mochios.wikis.repository.WikisRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Reading the wikis brings those pinned to the home screen into line with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikiShortcutsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val server = MockWebServer()

    @Before
    fun begin() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().substringBefore("?") == "/wikis/-/info") {
                    MockResponse().setBody("""{"data": {"wikis": [{"id": "w1", "fingerprint": "fp1", "name": "Notes"}, {"id": "w2", "fingerprint": "fp2", "name": "Other"}]}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun repository(): WikisRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/wikis/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return WikisRepository(retrofit.create(WikisApi::class.java), FileStore(context), context)
    }

    /** The shortcuts the launcher shows greyed out. */
    private fun disabled(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
            ShadowShortcutManager::class.java,
            "disabledPinnedShortcuts",
        ).keys

    @Test
    fun `reading the wikis turns off a gone wiki's shortcut and renames a kept one's`() = runBlocking {
        Shortcuts.pinItem(context, "wikis", "w1", "Old name")
        Shortcuts.pinItem(context, "wikis", "w9", "Gone")
        Shortcuts.pinItem(context, "wikis", "fp2", "By its other name")
        repository().getClassInfo()
        assertEquals(setOf("wikis_w9"), disabled())
        assertEquals("Notes", manager.pinnedShortcuts.first { it.id == "wikis_w1" }.shortLabel.toString())
    }
}
