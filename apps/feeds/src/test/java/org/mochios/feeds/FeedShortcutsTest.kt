// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds

import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.android.launcher.Shortcuts
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.model.Feed
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.ui.feed.heldFeed
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Reading the feeds brings those pinned to the home screen into line with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedShortcutsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val server = MockWebServer()

    @Before
    fun begin() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().substringBefore("?") == "/feeds/-/info") {
                    MockResponse().setBody("""{"data": {"feeds": [{"id": "f1", "fingerprint": "fp1", "name": "Garden"}, {"id": "f2", "fingerprint": "fp2", "name": "Other"}]}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun repository(): FeedsRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return FeedsRepository(retrofit.create(FeedsApi::class.java), retrofit.create(MenuApi::class.java), FileStore(context), context)
    }

    /** The shortcuts the launcher shows greyed out. */
    private fun disabled(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
            ShadowShortcutManager::class.java,
            "disabledPinnedShortcuts",
        ).keys

    @Test
    fun `reading the feeds turns off a gone feed's shortcut and renames a kept one's`() = runBlocking {
        Shortcuts.pinItem(context, "feeds", "f1", "Old name")
        Shortcuts.pinItem(context, "feeds", "f9", "Gone")
        Shortcuts.pinItem(context, "feeds", "fp2", "By its other name")
        repository().getFeedsInfo()
        assertEquals(setOf("feeds_f9"), disabled())
        assertEquals("Garden", manager.pinnedShortcuts.first { it.id == "feeds_f1" }.shortLabel.toString())
    }

    @Test
    fun `only a feed the user holds is offered, found by its id or fingerprint`() {
        val feeds = listOf(Feed(id = "f1", fingerprint = "fp1", name = "Garden"), Feed(id = "f2", name = "Other"))
        assertEquals("Garden", heldFeed(feeds, "f1")?.name)
        assertEquals("Garden", heldFeed(feeds, "fp1")?.name)
        assertNull(heldFeed(feeds, "browsed"))
        // A feed with no fingerprint is not matched by an empty one.
        assertNull(heldFeed(feeds, ""))
    }
}
