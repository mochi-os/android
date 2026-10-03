// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.os.Looper
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.TokenApi
import org.mochios.android.files.FileStore
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.dialogs.SubscribeCalendarScreen
import org.mochios.calendars.ui.dialogs.SubscribeCalendarViewModel
import org.mochios.calendars.ui.dialogs.SubscribeKind
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The subscribe wizard as the web's: Google offers another account beside
 * those already connected, an administrator on a server with no Google
 * client is taken to the settings that enable it, each account is named
 * over its provider, the lists read by name, and a published address starts
 * on teal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscribeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    /** What `-/calendars/accounts` answers. */
    @Volatile private var accounts = """{"accounts": [], "providers": [], "administrator": false}"""

    private val enabled = mutableListOf<Unit>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                return when (path) {
                    "-/calendars/accounts" -> ok(accounts)
                    "-/calendars/remote" -> ok(
                        """{"calendars": [{"href": "/w", "name": "Work"}, {"href": "/b", "name": "birthdays"}, {"href": "/h", "name": "Home"}]}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model(): SubscribeCalendarViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
        )
        val session = SessionManager(context)
        val auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        return SubscribeCalendarViewModel(repository, session, auth)
    }

    private fun settle(model: SubscribeCalendarViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (model.uiState.value.isLoading || server.requestCount == 0) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun show(): SubscribeCalendarViewModel {
        val model = model()
        settle(model)
        rule.setContent {
            SubscribeCalendarScreen(onBack = {}, onSubscribed = {}, onEnableGoogle = { enabled += Unit }, viewModel = model)
        }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.calendars_subscribe_google)).performClick()
        until { !model.uiState.value.isLoading }
        return model
    }

    /**
     * Waits for [done], running the main looper the network's answers resume
     * on; a condition that reads the model alone never runs it.
     */
    private fun until(done: () -> Boolean) {
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            done()
        }
        rule.waitForIdle()
    }

    private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `an administrator on a server with no Google client is taken to the settings that enable it`() {
        accounts = """{"accounts": [], "providers": [], "administrator": true}"""
        show()
        assertEquals(1, count(context.getString(R.string.calendars_subscribe_google_enable)))
        rule.onNode(hasText(context.getString(R.string.calendars_subscribe_google_enable_action)) and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(1, enabled.size)
    }

    @Test
    fun `a user with a Google account is offered another, and the account reads over its provider`() {
        accounts = """{"accounts": [{"id": "g1", "type": "google", "label": "Work", "identifier": "pat@example.com", "granted": ["login", "calendar"]}],
            "providers": ["google"], "administrator": false}"""
        show()
        assertEquals(1, count("Work"))
        assertEquals(1, count("Google"))
        assertEquals(0, count("pat@example.com"))
        assertEquals(1, count(context.getString(R.string.calendars_subscribe_google_connect_action)))
        // Accounts are held, so nothing says none are connected.
        assertEquals(0, count(context.getString(R.string.calendars_subscribe_google_connect)))
    }

    @Test
    fun `an account's calendars are listed by name`() {
        accounts = """{"accounts": [{"id": "g1", "type": "google", "label": "Work", "granted": ["calendar"]}],
            "providers": ["google"], "administrator": false}"""
        val model = show()
        rule.onNodeWithText("Work").performClick()
        until { model.uiState.value.remote.isNotEmpty() }
        assertEquals(listOf("/b", "/h", "/w"), model.uiState.value.remote.map { it.href })
    }

    @Test
    fun `a published address starts on teal, as the web's does`() {
        val model = model()
        settle(model)
        model.choose(SubscribeKind.ADDRESS)
        assertEquals("#2dd4bf", model.uiState.value.colour)
    }
}
