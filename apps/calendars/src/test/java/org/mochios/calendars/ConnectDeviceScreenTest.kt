// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.calendars.api.CalendarsApi
import org.mochios.calendars.api.MenuApi
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.devices.ConnectDeviceScreen
import org.mochios.calendars.ui.devices.ConnectDeviceViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The connected-devices screen gives a device the calendar address and the
 * username, before a device is added and again beside its new password, and
 * no bare server beside them: the address serves where a client asks for a
 * server, and a second field read as the address sent people astray.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectDeviceScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/calendars/").substringBefore("?")
                return when (path) {
                    "-/token/list" -> ok("""{"tokens": [], "username": "someone@example.test"}""")
                    "-/token/create" -> ok("""{"token": "mochi-secret", "username": "someone@example.test"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private val session = SessionManager(context)

    /** The origin the session points at, which the address is built on. */
    private val origin: String = runBlocking { session.serverUrl.first().trimEnd('/') }

    private fun show(): ConnectDeviceViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/calendars/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val repository = CalendarsRepository(
            retrofit.create(CalendarsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
        )
        val model = ConnectDeviceViewModel(repository, session)
        rule.setContent { ConnectDeviceScreen(onBack = {}, viewModel = model) }
        until { !model.uiState.value.isLoading && model.uiState.value.address.isNotEmpty() }
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

    /** The address and the username each once, and neither a "Server" label nor the bare origin. */
    private fun details() {
        assertEquals(1, count("$origin/calendars/caldav/"))
        assertEquals(1, count("someone@example.test"))
        assertEquals(0, count("Server"))
        assertEquals(0, count(origin))
    }

    @Test
    fun `before a device is added the screen shows the address and the username and no bare server`() {
        show()
        details()
    }

    @Test
    fun `a new device's password comes with the address and the username and no bare server`() {
        val model = show()
        // Typed through the model: text entry in a dialog window never
        // settles under Robolectric, and the dialog is not what is tested.
        rule.runOnIdle {
            model.setName("iPad")
            model.create()
        }
        until { model.uiState.value.token != null && !model.uiState.value.isLoading }
        assertEquals(1, count("mochi-secret"))
        details()
    }
}
