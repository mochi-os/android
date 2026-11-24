// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.market.R
import org.mochios.market.api.MarketApi
import org.mochios.market.repository.MarketRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A past-due subscription opens Stripe's billing portal, which sends the buyer
 * back into the app; and a buyer withdraws their own dispute, which reads the
 * order again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PastDueAndWithdrawTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    /** The form bodies each action was sent, by its path. */
    private val sent = CopyOnWriteArrayList<Pair<String, String>>()
    @Volatile private var disputeStatus = "open"

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath.substringAfter("/market/")
                sent += path to URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                return when (path) {
                    "-/subscriptions/mine" -> ok(
                        """{"subscriptions": [{"id": "s1", "listing": "l1", "status": "past_due", "amount": 500, "currency": "gbp"}], "total": 1}""",
                    )
                    "-/subscriptions/portal" -> ok(
                        """{"url": "https://billing.stripe.com/p/session/x", "redirect": "/market/-/redirect?id=r1"}""",
                    )
                    "-/orders/get" -> ok(
                        """{"order": {"id": "o1", "listing": "l1", "status": "disputed", "total": 2000, "currency": "gbp"},
                           "listing": {"id": "l1", "title": "Teapot"},
                           "dispute": {"id": "d1", "order": "o1", "opener": "b1", "status": "$disputeStatus"}}""",
                    )
                    "-/disputes/withdraw" -> {
                        disputeStatus = "resolved_seller"
                        ok("""{"id": "d1", "status": "resolved_seller", "decider": "buyer"}""")
                    }
                    else -> ok("""{"audit": [], "total": 0}""")
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun repository(): MarketRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        return MarketRepository(api, FileStore(context))
    }

    @Test
    fun `a past-due subscription opens Stripe's portal, returning to the app`() {
        val model = MySubscriptionsViewModel(repository())
        until { !model.uiState.value.isLoading }
        val opened = CopyOnWriteArrayList<MySubscriptionsEvent>()
        // Subscribed at once: the events are not replayed to a late collector.
        val job = CoroutineScope(Dispatchers.Main.immediate).launch { model.events.collect { opened += it } }
        model.updatePayment("s1")
        until { opened.isNotEmpty() }
        job.cancel()
        assertEquals(MySubscriptionsEvent.OpenPortal("https://billing.stripe.com/p/session/x"), opened.first())
        val portal = sent.first { (path, _) -> path == "-/subscriptions/portal" }.second
        assertTrue(portal, "id=s1" in portal)
        assertTrue(portal, "return=mochi://market/checkout/success?kind=subscription" in portal)
    }

    @Test
    fun `a withdrawn dispute is sent once and the order read again`() {
        val model = PurchaseDetailViewModel(SavedStateHandle(mapOf("orderId" to "o1")), repository(), context)
        until { model.uiState.value.detail != null }
        val shown = CopyOnWriteArrayList<PurchaseDetailEvent>()
        val job = CoroutineScope(Dispatchers.Main.immediate).launch { model.events.collect { shown += it } }
        model.withdrawDispute()
        until { model.uiState.value.detail?.dispute?.status == "resolved_seller" && shown.isNotEmpty() }
        job.cancel()
        assertEquals(1, sent.count { (path, _) -> path == "-/disputes/withdraw" })
        assertTrue(sent.first { (path, _) -> path == "-/disputes/withdraw" }.second.contains("id=d1"))
        assertEquals(PurchaseDetailEvent.Toast(context.getString(R.string.market_purchase_dispute_withdrawn)), shown.first())
    }

    @Test
    fun `a decided dispute is not withdrawn`() {
        disputeStatus = "resolved_buyer"
        val model = PurchaseDetailViewModel(SavedStateHandle(mapOf("orderId" to "o1")), repository(), context)
        until { model.uiState.value.detail != null }
        model.withdrawDispute()
        // Long enough, in real time, for a request the model sent to reach
        // the server and be read back.
        val deadline = System.currentTimeMillis() + 1_500
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        assertEquals(0, sent.count { (path, _) -> path == "-/disputes/withdraw" })
    }
}
