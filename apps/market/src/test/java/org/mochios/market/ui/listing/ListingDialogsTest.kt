// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.mochios.android.i18n.AppContext
import org.mochios.market.R
import org.mochios.market.api.MarketApi
import org.mochios.market.lib.RecentlyViewedStore
import org.mochios.market.lib.ReportedStore
import org.mochios.market.repository.MarketRepository
import org.mochios.market.repository.SavedRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * The listing's bid and report dialogs over a real view model: an accepted
 * request closes the dialog, and a refused one keeps it open on the server's
 * reason with the form usable again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListingDialogsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    /** The reason `-/bids/place` and `-/reports/create` refuse with, or null to accept. */
    @Volatile private var refusal: String? = null
    private val bids = AtomicInteger(0)
    private val reports = AtomicInteger(0)

    private val closed = mutableListOf<Unit>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                return when (path) {
                    "-/listings/viewer/get" -> ok(
                        """{"listing": {"id": "l1", "title": "Teapot", "price": 100, "currency": "jpy", "pricing": "auction", "status": "active"},
                           "auction": {"id": "a1", "listing": "l1", "status": "active", "bids": 0}}""",
                    )
                    "-/bids/place" -> {
                        bids.incrementAndGet()
                        answer(
                            """{"bid": {"id": "b1", "auction": "a1", "amount": 100, "status": "active"}, "outbid": false}""",
                        )
                    }
                    "-/reports/create" -> {
                        reports.incrementAndGet()
                        answer("""{"ok": true}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun answer(data: String): MockResponse {
        val reason = refusal ?: return ok(data)
        return MockResponse().setResponseCode(400).setBody("""{"error": "$reason"}""")
    }

    private fun model(): ListingDetailViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(MarketApi::class.java)
        val model = ListingDetailViewModel(
            MarketRepository(api, FileStore(context)),
            SavedRepository(api),
            RecentlyViewedStore(context),
            ReportedStore(context),
            context,
        )
        model.load("l1")
        settle { model.state.value.listing != null && !model.state.value.isLoading }
        return model
    }

    /** [until] for before any content is set, which the rule's waits need. */
    private fun settle(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
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

    private fun string(id: Int) = context.getString(id)

    private fun showBid(model: ListingDetailViewModel) {
        rule.setContent {
            var open by remember { mutableStateOf(true) }
            val state by model.state.collectAsState()
            BidDialogHost(
                open = open,
                detail = state.listing,
                viewModel = model,
                onClose = {
                    open = false
                    closed += Unit
                },
            )
        }
        rule.waitForIdle()
    }

    private fun bid() {
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("100")
        rule.onNode(hasText(string(R.string.market_bid_dialog_submit)) and hasClickAction()).performClick()
    }

    private fun showReport(model: ListingDetailViewModel) {
        rule.setContent {
            var open by remember { mutableStateOf(true) }
            ReportDialogHost(
                open = open,
                viewModel = model,
                onClose = {
                    open = false
                    closed += Unit
                },
            )
        }
        rule.waitForIdle()
    }

    private fun report() {
        rule.onNode(hasText(string(R.string.market_report_dialog_submit)) and hasClickAction()).performClick()
    }

    @Test
    fun `an accepted bid closes the dialog`() {
        val model = model()
        showBid(model)
        bid()
        until { closed.isNotEmpty() }
        assertEquals(1, bids.get())
        assertEquals(0, count(string(R.string.market_bid_dialog_submitting)))
        assertEquals(0, count(string(R.string.market_bid_dialog_title)))
    }

    @Test
    fun `a refused bid stays open on the reason and can be sent again`() {
        refusal = "Bid must be at least ¥101"
        val model = model()
        showBid(model)
        bid()
        until { count("Bid must be at least ¥101") == 1 }
        assertEquals(1, bids.get())
        assertEquals(0, closed.size)
        assertEquals(0, count(string(R.string.market_bid_dialog_submitting)))
        refusal = null
        rule.onNode(hasText(string(R.string.market_bid_dialog_submit)) and hasClickAction()).performClick()
        until { closed.isNotEmpty() }
        assertEquals(2, bids.get())
    }

    @Test
    fun `a filed report closes the dialog`() {
        val model = model()
        showReport(model)
        report()
        until { closed.isNotEmpty() }
        assertEquals(1, reports.get())
        assertEquals(0, count(string(R.string.market_report_dialog_title)))
    }

    @Test
    fun `a refused report stays open on the reason and can be sent again`() {
        refusal = "You have already reported this listing"
        val model = model()
        showReport(model)
        report()
        until { count("You have already reported this listing") == 1 }
        assertEquals(1, reports.get())
        assertEquals(0, closed.size)
        assertEquals(0, count(string(R.string.market_report_dialog_submitting)))
        refusal = null
        report()
        until { closed.isNotEmpty() }
        assertEquals(2, reports.get())
    }
}
