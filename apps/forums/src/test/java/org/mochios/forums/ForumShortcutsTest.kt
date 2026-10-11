// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums

import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.android.launcher.Shortcuts
import org.mochios.forums.api.ForumsApi
import org.mochios.forums.model.Forum
import org.mochios.forums.repository.ForumsRepository
import org.mochios.forums.ui.forum.holds
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Reading the forums brings those pinned to the home screen into line with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumShortcutsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val server = MockWebServer()

    @Before
    fun begin() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().substringBefore("?") in listOf("/forums/-/list", "/forums/-/information")) {
                    MockResponse().setBody("""{"data": {"forums": [{"id": "f1", "fingerprint": "fp1", "name": "Cycling"}, {"id": "f2", "fingerprint": "fp2", "name": "Other"}]}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun repository(): ForumsRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/forums/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return ForumsRepository(retrofit.create(ForumsApi::class.java), FileStore(context), context)
    }

    /** The shortcuts the launcher shows greyed out. */
    private fun disabled(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
            ShadowShortcutManager::class.java,
            "disabledPinnedShortcuts",
        ).keys

    @Test
    fun `reading the forums turns off a gone forum's shortcut and renames a kept one's`() = runBlocking {
        Shortcuts.pinItem(context, "forums", "f1", "Old name")
        Shortcuts.pinItem(context, "forums", "f9", "Gone")
        Shortcuts.pinItem(context, "forums", "fp2", "By its other name")
        repository().listForums()
        assertEquals(setOf("forums_f9"), disabled())
        assertEquals("Cycling", manager.pinnedShortcuts.first { it.id == "forums_f1" }.shortLabel.toString())
    }

    @Test
    fun `reading the roster without its posts does the same`() = runBlocking {
        Shortcuts.pinItem(context, "forums", "f1", "Old name")
        Shortcuts.pinItem(context, "forums", "f9", "Gone")
        repository().forumsInformation()
        assertEquals(setOf("forums_f9"), disabled())
        assertEquals("Cycling", manager.pinnedShortcuts.first { it.id == "forums_f1" }.shortLabel.toString())
    }

    @Test
    fun `only a forum the user holds is offered, found by its id or fingerprint`() {
        val forums = listOf(Forum(id = "f1", fingerprint = "fp1", name = "Cycling"), Forum(id = "f2", name = "Other"))
        assertTrue(holds(forums, Forum(id = "f1")))
        assertTrue(holds(forums, Forum(id = "x", fingerprint = "fp1")))
        assertFalse(holds(forums, Forum(id = "browsed", fingerprint = "fpb")))
        // A forum with no fingerprint is not matched by an empty one.
        assertFalse(holds(forums, Forum(id = "browsed")))
    }
}
