// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat

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
import org.mochios.chat.api.ChatApi
import org.mochios.chat.model.ChatDetail
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.repository.ChatRepository
import org.mochios.chat.ui.chat.pinnable
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager
import org.robolectric.util.ReflectionHelpers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Reading the chats brings those pinned to the home screen into line with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatShortcutsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val server = MockWebServer()

    @Before
    fun begin() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().substringBefore("?") == "/chat/-/list") {
                    MockResponse().setBody("""{"data": [{"id": "c1", "key": "k1", "name": "Team"}, {"id": "c2", "key": "k2", "name": "Other"}]}""")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun repository(): ChatRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/chat/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return ChatRepository(retrofit.create(ChatApi::class.java), FileStore(context), context)
    }

    /** The shortcuts the launcher shows greyed out. */
    private fun disabled(): Set<String> =
        ReflectionHelpers.getStaticField<Map<String, ShortcutInfo>>(
            ShadowShortcutManager::class.java,
            "disabledPinnedShortcuts",
        ).keys

    @Test
    fun `reading the chats turns off a gone chat's shortcut and renames a kept one's`() = runBlocking {
        Shortcuts.pinItem(context, "chat", "c1", "Old name")
        Shortcuts.pinItem(context, "chat", "c9", "Gone")
        Shortcuts.pinItem(context, "chat", "k2", "By its other name")
        repository().listChats()
        assertEquals(setOf("chat_c9"), disabled())
        assertEquals("Team", manager.pinnedShortcuts.first { it.id == "chat_c1" }.shortLabel.toString())
    }

    @Test
    fun `a chat the user is still in goes on the home screen, and one left does not`() {
        assertTrue(pinnable(ChatDetail(id = "c1", name = "Team")))
        assertFalse(pinnable(ChatDetail(id = "c1", name = "Team", status = ChatStatus.LEFT)))
        assertFalse(pinnable(ChatDetail(id = "c1", name = "")))
    }
}
