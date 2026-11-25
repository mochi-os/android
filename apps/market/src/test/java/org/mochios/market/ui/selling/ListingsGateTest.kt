// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.LoadState
import org.mochios.market.api.MarketApi
import org.mochios.market.repository.MarketRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * My listings decides from the seller's own account, not a live Stripe call,
 * whether New listing is offered and whether payment setup is still to do; and
 * an account that does not sell is sent to become a seller.
 *
 * The model runs on a test dispatcher, and each case waits on the state or
 * event the model emits rather than on the clock, so a loaded machine slows a
 * case without failing it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListingsGateTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    @Volatile private var account = """{"id": "me", "seller": 1, "onboarded": 1}"""

    /** The action paths the client asked for, each recorded as its call is made. */
    private val asked = CopyOnWriteArrayList<String>()

    @Before
    fun begin() {
        AppContext.set(context)
        Dispatchers.setMain(StandardTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                return when (path) {
                    "-/accounts/get" -> ok(account)
                    "-/accounts/stripe/status" ->
                        MockResponse().setResponseCode(400).setBody("""{"error": "No Stripe account"}""")
                    "-/listings/mine" -> ok("""{"listings": [{"id": "l1", "title": "Teapot"}], "total": 1}""")
                    else -> ok("null")
                }
            }
        }
        server.start()
    }

    @After
    fun end() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model(): MyListingsViewModel {
        val client = OkHttpClient()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .callFactory(object : Call.Factory {
                override fun newCall(request: Request): Call {
                    asked += request.url.encodedPath.substringAfter("/market/")
                    return client.newCall(request)
                }
            })
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        return MyListingsViewModel(MarketRepository(api, FileStore(context)), SessionManager(context))
    }

    /** How many times the client asked Stripe for the account's status. */
    private fun stripe() = asked.count { path -> path == "-/accounts/stripe/status" }

    @Test
    fun `a seller whose payments are set up can list, without asking Stripe`() = runTest {
        val model = model()
        model.state.first { state -> state.account != null && state.load == LoadState.Idle }
        advanceUntilIdle()
        assertTrue(model.state.value.listable)
        assertFalse(model.state.value.onboarding)
        assertEquals(0, stripe())
    }

    @Test
    fun `a seller who has not set up payments is shown how, and cannot list yet`() = runTest {
        account = """{"id": "me", "seller": 1, "onboarded": 0}"""
        val model = model()
        model.state.first { state -> state.account != null }
        advanceUntilIdle()
        assertFalse(model.state.value.listable)
        assertTrue(model.state.value.onboarding)
        assertEquals(0, stripe())
    }

    @Test
    fun `an account that does not sell is sent to become a seller`() = runTest {
        account = """{"id": "me", "seller": 0, "onboarded": 0}"""
        val model = model()
        val events = mutableListOf<MyListingsEvent>()
        // Subscribed before the model's first read runs, which the test
        // dispatcher holds until this case waits; a foreground job, since
        // advanceUntilIdle does not run background work.
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { model.events.toList(events) }
        model.state.first { state -> state.account != null }
        advanceUntilIdle()
        collector.cancel()
        assertEquals(listOf<MyListingsEvent>(MyListingsEvent.NotSeller), events)
        assertFalse(model.state.value.onboarding)
    }
}
