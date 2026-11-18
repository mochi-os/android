// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.accounts

import android.os.Looper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.settings.api.ConnectedAccountsApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A connected account's notify switch moves on the tap, not once the server
 * has answered and the list been read again, and goes back when the server
 * refuses, as the RSS feeds' switch does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifySwitchTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val updates = CopyOnWriteArrayList<String>()

    /** Holds the update's answer back until released, so a test sees the switch before it. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the update. */
    @Volatile private var refuse = false

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @After
    fun end() {
        gate.countDown()
        server.shutdown()
    }

    private fun answer(): MockResponse {
        gate.await(5, TimeUnit.SECONDS)
        return if (refuse) {
            MockResponse().setResponseCode(500).setBody("""{"error": "Not now"}""").setHeader("Content-Type", "application/json")
        } else {
            json("{}")
        }
    }

    private fun api(): ConnectedAccountsApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ConnectedAccountsApi::class.java)

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/accounts/providers" -> json("[]")
                    "settings/-/accounts/list" -> json("""[{"id": "a1", "type": "email", "identifier": "ada@example.com", "enabled": 0, "capabilities": ["notify"]}]""")
                    "settings/-/notifications/devices" -> json("[]")
                    "settings/-/accounts/update" -> {
                        updates += request.body.readUtf8()
                        answer()
                    }
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    private fun model(): ConnectedAccountsViewModel {
        val made = ConnectedAccountsViewModel(api())
        until { !made.uiState.value.isLoading && made.uiState.value.accounts.isNotEmpty() }
        return made
    }

    @Test
    fun `the notify switch moves at once`() {
        val model = model()
        model.toggleNotifyDefault("a1", true)
        assertEquals(1, model.uiState.value.accounts.single().enabled)
        gate.countDown()
        until { updates.isNotEmpty() }
    }

    @Test
    fun `the notify switch goes back when the server refuses, saying why`() {
        refuse = true
        val model = model()
        model.toggleNotifyDefault("a1", true)
        gate.countDown()
        until { model.uiState.value.error != null }
        assertEquals(0, model.uiState.value.accounts.single().enabled)
        assertNotNull(model.uiState.value.error)
    }
}
