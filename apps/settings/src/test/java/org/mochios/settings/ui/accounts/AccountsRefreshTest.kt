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
import org.junit.Assert.assertFalse
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The connected accounts stay on screen while they are read again after a change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountsRefreshTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val lists = AtomicInteger()

    /** Holds each read of the list after the first back until released, when set. */
    @Volatile private var gate: CountDownLatch? = null

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/accounts/providers" -> json("""[{"type": "openai", "capabilities": ["ai"], "flow": "form", "fields": []}]""")
                    "settings/-/accounts/list" -> {
                        if (lists.incrementAndGet() > 1) gate?.await(5, TimeUnit.SECONDS)
                        json("""[{"id": "a1", "type": "openai", "identifier": "default"}]""")
                    }
                    "settings/-/notifications/devices" -> json("[]")
                    "settings/-/accounts/default" -> json("{}")
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() {
        gate?.countDown()
        server.shutdown()
    }

    /** Each change used to blank the list to a spinner while it was read again. */
    @Test
    fun `the accounts stay while they are read again after a change`() {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ConnectedAccountsApi::class.java)
        val model = ConnectedAccountsViewModel(api)
        until { !model.uiState.value.isLoading && model.uiState.value.accounts.isNotEmpty() }
        gate = CountDownLatch(1)
        model.setAiDefault("a1")
        until { lists.get() >= 2 }
        assertFalse(model.uiState.value.isLoading)
        assertEquals(1, model.uiState.value.accounts.size)
        gate!!.countDown()
    }
}
