// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

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
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.repository.CalendarsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Reading the calendars brings those pinned to the home screen into line with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarShortcutsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val server = MockWebServer()

    @Before
    fun begin() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().substringBefore("?") == "/calendars/-/calendars") {
                    MockResponse().setBody("""{"data": {"calendars": [{"id": "c1", "name": "Work", "colour": "#60a5fa"}, {"id": "c2", "name": "Other", "colour": "#f87171"}]}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun repository(): CalendarsRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return CalendarsRepository(retrofit.create(CalendarsApi::class.java), retrofit.create(MenuApi::class.java), FileStore(context), context)
    }

    /** The shortcuts the launcher shows greyed out. */
    private fun disabled(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
            ShadowShortcutManager::class.java,
            "disabledPinnedShortcuts",
        ).keys

    @Test
    fun `reading the calendars turns off a gone calendar's shortcut and renames a kept one's`() = runBlocking {
        Shortcuts.pinItem(context, "calendars", "calendar/c1", "Old name")
        Shortcuts.pinItem(context, "calendars", "calendar/c9", "Gone")
        Shortcuts.pinItem(context, "calendars", "calendar/c2", "By its other name")
        repository().listCalendars()
        assertEquals(setOf("calendars_calendar/c9"), disabled())
        assertEquals("Work", manager.pinnedShortcuts.first { it.id == "calendars_calendar/c1" }.shortLabel.toString())
    }
}
