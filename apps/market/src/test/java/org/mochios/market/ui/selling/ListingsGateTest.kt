// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.LoadState
import org.mochios.market.api.MarketApi
import org.mochios.market.repository.MarketRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * My listings decides from the seller's own account, not a live Stripe call,
 * whether New listing is offered and whether payment setup is still to do; and
 * an account that does not sell is sent to become a seller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListingsGateTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    @Volatile private var account = """{"id": "me", "seller": 1, "onboarded": 1}"""
    private val stripe = AtomicInteger()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                return when (path) {
                    "-/accounts/get" -> ok(account)
                    "-/accounts/stripe/status" -> {
                        stripe.incrementAndGet()
                        MockResponse().setResponseCode(400).setBody("""{"error": "No Stripe account"}""")
                    }
                    "-/listings/mine" -> ok("""{"listings": [{"id": "l1", "title": "Teapot"}], "total": 1}""")
                    else -> ok("null")
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model(): MyListingsViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        return MyListingsViewModel(MarketRepository(api, FileStore(context)))
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun settle() = repeat(10) {
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
        Thread.sleep(10)
    }

    @Test
    fun `a seller whose payments are set up can list, without asking Stripe`() {
        val model = model()
        until { model.state.value.account != null && model.state.value.load == LoadState.Idle }
        settle()
        assertTrue(model.state.value.listable)
        assertFalse(model.state.value.onboarding)
        assertEquals(0, stripe.get())
    }

    @Test
    fun `a seller who has not set up payments is shown how, and cannot list yet`() {
        account = """{"id": "me", "seller": 1, "onboarded": 0}"""
        val model = model()
        until { model.state.value.account != null }
        settle()
        assertFalse(model.state.value.listable)
        assertTrue(model.state.value.onboarding)
        assertEquals(0, stripe.get())
    }

    @Test
    fun `an account that does not sell is sent to become a seller`() {
        account = """{"id": "me", "seller": 0, "onboarded": 0}"""
        val model = model()
        val events = CopyOnWriteArrayList<MyListingsEvent>()
        CoroutineScope(Dispatchers.Main).launch { model.events.collect { event -> events += event } }
        until { events.isNotEmpty() }
        assertEquals(MyListingsEvent.NotSeller, events.single())
        assertFalse(model.state.value.onboarding)
    }
}
