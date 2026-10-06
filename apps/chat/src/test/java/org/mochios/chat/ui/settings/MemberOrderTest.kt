// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.settings

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.chat.api.ChatApi
import org.mochios.chat.repository.ChatRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The settings screen lists a chat's members as the web does: you first,
 * then everyone else by name, whatever order the server sends them in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class MemberOrderTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/chat/").substringBefore("?")
                return when (path) {
                    "c1/-/view" -> MockResponse().setBody(
                        """{"data": {"chat": {"id": "c1", "name": "Team", "status": "active", "members": [
                            {"id": "c", "name": "Carol"}, {"id": "me", "name": "Zed"}, {"id": "b", "name": "bob"}
                        ]}, "identity": "me"}}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    @Test
    fun `you come first, then the other members by name`() {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/chat/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val model = ChatSettingsViewModel(
            SavedStateHandle(mapOf("chatId" to "c1")),
            ChatRepository(retrofit.create(ChatApi::class.java), FileStore(context)),
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (model.uiState.value.isLoading || model.uiState.value.chat.members.isEmpty()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        rule.setContent { ChatSettingsScreen(onBack = {}, onChatLeft = {}, viewModel = model) }
        rule.waitForIdle()
        val tops = listOf("Zed", "bob", "Carol").map { name ->
            rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
    }
}
