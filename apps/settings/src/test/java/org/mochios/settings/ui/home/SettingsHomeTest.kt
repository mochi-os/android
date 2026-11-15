// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.home

import android.os.Looper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.api.DomainsApi
import org.mochios.settings.api.NetworksApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The settings home screen asks whether the server offers other networks with
 * the one flag's own action, beside the domains read rather than after it, and
 * asks an older server, which answers it 404, for the whole Networks page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsHomeTest {

    private lateinit var server: MockWebServer

    /** Each path the server was asked for, in order. */
    private val asked = CopyOnWriteArrayList<String>()

    /** Whether the server predates networks/offered. */
    @Volatile private var older = false

    /** Released once networks/offered is asked, which the domains answer waits for. */
    private val flag = CountDownLatch(1)

    /** Whether networks/offered was asked while the domains answer was still held. */
    @Volatile private var overlapped = false

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?").removePrefix("/")
                asked += path
                return when (path) {
                    "settings/-/domains/data" -> {
                        overlapped = flag.await(2, TimeUnit.SECONDS)
                        json("""{"domains": [], "admin": true}""")
                    }
                    "settings/-/user/networks/offered" -> {
                        flag.countDown()
                        if (older) {
                            MockResponse().setResponseCode(404).setBody("""{"error": "action_not_found"}""")
                        } else {
                            json("""{"offered": true}""")
                        }
                    }
                    "settings/-/user/networks/data" -> json("""{"offered": true, "networks": ["fediverse"]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun model(): SettingsHomeViewModel {
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build()
        return SettingsHomeViewModel(retrofit.create(DomainsApi::class.java), retrofit.create(NetworksApi::class.java))
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `whether networks are offered is asked of its own action, beside the domains`() {
        val model = model()
        until { model.state.value.hasNetworks && model.state.value.isAdmin }
        assertTrue("THE POINT: the flag waited for the domains answer", overlapped)
        assertTrue("the whole Networks page was read for one flag", "settings/-/user/networks/data" !in asked)
    }

    @Test
    fun `an older server is asked for the Networks page, and not asked for the flag again`() {
        older = true
        val model = model()
        until { model.state.value.hasNetworks && model.state.value.isAdmin }
        model.refresh()
        until { asked.count { it == "settings/-/user/networks/data" } == 2 }
        assertEquals(1, asked.count { it == "settings/-/user/networks/offered" })
        assertTrue(model.state.value.hasNetworks)
    }
}
