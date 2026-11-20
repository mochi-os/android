// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.mochios.chat.R
import org.mochios.chat.api.ChatApi
import org.mochios.chat.model.ChatAccount
import org.mochios.chat.repository.ChatRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * New chat's network choice offers the networks the server offers that the
 * user has no account on, each set up from here instead of chatted on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewChatSetupTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: ChatRepository

    /** The accounts and offered networks New chat is answered with. */
    @Volatile private var accounts = """[{"id": "x1", "identifier": "me@xmpp.example", "network": "xmpp"}]"""
    @Volatile private var offered = """["fediverse", "matrix", "xmpp"]"""
    @Volatile private var friends = """[{"id": "f", "identity": "f", "name": "Dung", "chat": ""}]"""

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path.orEmpty().substringBefore("?")) {
                "/chat/-/new" -> MockResponse().setBody("""{"data": {"name": "Me", "friends": $friends, "accounts": $accounts, "offered": $offered}}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/chat/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = ChatRepository(retrofit.create(ChatApi::class.java), FileStore(context), context)
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun until(what: String, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun text(id: Int): String = context.getString(id)

    @Test
    fun `a network offered that no account is on is chosen to set one up, in place of the people`() {
        val model = NewChatViewModel(SavedStateHandle(), repository)
        until("the accounts") { model.uiState.value.accounts.isNotEmpty() && !model.uiState.value.isLoading }
        val opened = mutableListOf<String>()
        rule.setContent { NewChatScreen(onBack = {}, onChatCreated = {}, onOpenLink = { opened += it }, viewModel = model) }
        rule.onNodeWithText(text(R.string.chat_new_search_placeholder)).assertIsDisplayed()
        rule.onNodeWithText("Dung").performClick()
        rule.onNodeWithText(text(R.string.chat_new_start)).assertIsEnabled()
        // The choice lists the account, then the networks without one by name.
        rule.onNodeWithText(text(R.string.chat_new_network_mochi)).performClick()
        rule.onNodeWithText("XMPP · me@xmpp.example").assertIsDisplayed()
        rule.onNodeWithText("XMPP").assertDoesNotExist()
        rule.onNodeWithText("ActivityPub").assertIsDisplayed()
        rule.onNodeWithText("Matrix").performClick()
        rule.waitForIdle()
        assertEquals("matrix", model.uiState.value.setup)
        // THE POINT: the people field gives way to the way to set an account up.
        rule.onNodeWithText(text(R.string.chat_new_search_placeholder)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.chat_new_start)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.chat_setup)).performClick()
        assertEquals(listOf("settings/user/networks?network=matrix"), opened)
    }

    @Test
    fun `with no account but a network offered, the choice is still shown`() {
        accounts = "[]"
        offered = """["matrix"]"""
        val model = NewChatViewModel(SavedStateHandle(), repository)
        until("the offered networks") { model.uiState.value.offered.isNotEmpty() && !model.uiState.value.isLoading }
        rule.setContent { NewChatScreen(onBack = {}, onChatCreated = {}, viewModel = model) }
        rule.onNodeWithText(text(R.string.chat_new_network)).assertIsDisplayed()
    }

    @Test
    fun `an account chosen after a network to set up is chatted on`() {
        val model = NewChatViewModel(SavedStateHandle(), repository)
        until("the accounts") { model.uiState.value.accounts.isNotEmpty() && !model.uiState.value.isLoading }
        model.selectSetup("matrix")
        model.selectAccount("x1")
        assertEquals(null, model.uiState.value.setup)
        assertEquals("x1", model.uiState.value.chosen?.id)
    }

    @Test
    fun `the networks to set up are those offered no account is on, a linked Mastodon account on the fediverse`() {
        val mastodon = ChatAccount(id = "m1", identifier = "@me@social.example", network = "mastodon")
        val xmpp = ChatAccount(id = "x1", identifier = "me@xmpp.example", network = "xmpp")
        assertEquals(listOf("matrix"), unset(listOf("fediverse", "matrix", "xmpp"), listOf(mastodon, xmpp)))
        assertEquals(listOf("fediverse", "matrix"), unset(listOf("fediverse", "matrix", "xmpp"), listOf(xmpp)))
        assertEquals(emptyList<String>(), unset(emptyList(), listOf(xmpp)))
    }
}
