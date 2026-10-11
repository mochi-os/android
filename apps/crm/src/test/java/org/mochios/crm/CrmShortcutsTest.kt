// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm

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
import org.mochios.crm.api.CrmsApi
import org.mochios.crm.repository.CrmsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Reading the CRMs brings those pinned to the home screen into line with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrmShortcutsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val server = MockWebServer()

    @Before
    fun begin() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().substringBefore("?") == "/crm/-/list") {
                    MockResponse().setBody("""{"data": {"crms": [{"id": "c1", "fingerprint": "fp1", "name": "Sales"}, {"id": "c2", "fingerprint": "fp2", "name": "Other"}]}}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun repository(): CrmsRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/crm/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return CrmsRepository(retrofit.create(CrmsApi::class.java), FileStore(context), context)
    }

    /** The shortcuts the launcher shows greyed out. */
    private fun disabled(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
            ShadowShortcutManager::class.java,
            "disabledPinnedShortcuts",
        ).keys

    @Test
    fun `reading the CRMs turns off a gone CRM's shortcut and renames a kept one's`() = runBlocking {
        Shortcuts.pinItem(context, "crm", "c1", "Old name")
        Shortcuts.pinItem(context, "crm", "c9", "Gone")
        Shortcuts.pinItem(context, "crm", "fp2", "By its other name")
        repository().listCrms()
        assertEquals(setOf("crm_c9"), disabled())
        assertEquals("Sales", manager.pinnedShortcuts.first { it.id == "crm_c1" }.shortLabel.toString())
    }
}
