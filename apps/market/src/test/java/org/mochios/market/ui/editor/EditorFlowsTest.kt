// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.market.FlowServer
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.AccountFees
import org.mochios.market.model.Asset
import org.mochios.market.model.Currency
import org.mochios.market.model.Interval
import org.mochios.market.model.ListingType
import org.mochios.market.model.PricingModel
import org.mochios.market.repository.MarketRepository
import org.mochios.market.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The listing editor's flows: a change made just before Publish is saved
 * first; an auction is priced by its starting bid; a relisted auction keeps
 * its settings; a download defaults to unlimited stock; the delivery note is
 * sent; reorders take the server's order; and the price warning, the fee
 * preview and the reorder arrows say what the server will do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorFlowsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: FlowServer
    private lateinit var repository: MarketRepository

    @Before
    fun begin() {
        AppContext.set(context)
        server = FlowServer()
        server.data("-/categories/list", "[]")
        server.data("-/accounts/stripe/status", """{"charges_enabled": true, "payouts_enabled": true}""")
        server.data("-/accounts/fees", """{"platform": 5, "minimums": {"gbp": 30, "jpy": 100}}""")
        server.data("-/shipping/regions", """{"countries": ["GB", "FR"], "groups": {"world": [], "europe": ["GB", "FR"]}}""")
        server.data(
            "-/listings/viewer/get",
            """{"listing": {"id": "l1", "title": "Lamp", "price": 100, "currency": "gbp", "pricing": "fixed",
                "type": "physical", "status": "draft", "quantity": 2, "information": "Posted in two days"}}""",
        )
        server.data("-/photos/owned/list", "[]")
        server.data("-/listings/update", """{"id": "l1", "title": "Lamp"}""")
        server.data("-/listings/create", """{"id": "l9", "title": "Lamp"}""")
        server.on("-/listings/publish") { okhttp3.mockwebserver.MockResponse().setBody("""{"data": {"id": "l1"}}""") }
        repository = server.repository(context)
    }

    @After
    fun end() = server.stop()

    private fun editor(id: String = "l1"): EditListingViewModel {
        val model = EditListingViewModel(SavedStateHandle(mapOf("id" to id)), repository)
        until("the editor") { !model.state.value.isLoading }
        return model
    }

    private fun blank(): EditListingViewModel = editor("new")

    @Test
    fun `a change made just before Publish is saved before the publish`() {
        val model = editor()
        model.setPriceText("100.00")
        model.publish()
        until("the publish") { server.of("-/listings/publish").isNotEmpty() }
        val order = server.asked.map { it.path }
        val update = order.indexOf("-/listings/update")
        assertTrue("the draft is saved before it is published: $order", update in 0 until order.indexOf("-/listings/publish"))
        assertEquals("10000", server.of("-/listings/update").first().fields["price"])
    }

    @Test
    fun `an auction is priced by its starting bid, which the form asks for`() {
        val model = blank()
        model.setTitle("Lamp")
        model.setPricing(PricingModel.AUCTION)
        rule.setContent {
            val state by model.state.collectAsState()
            PricingModelSelector(
                pricing = state.pricing,
                currency = state.currency,
                priceText = state.priceText,
                interval = state.interval,
                durationDays = state.durationDays,
                reserveText = state.reserveText,
                instantText = state.instantText,
                opensAt = state.opensAt,
                minimum = minimumCharge(state),
                onPricingChange = model::setPricing,
                onCurrencyChange = model::setCurrency,
                onPriceChange = model::setPriceText,
                onIntervalChange = model::setInterval,
                onDurationChange = model::setDurationDays,
                onReserveChange = model::setReserveText,
                onInstantChange = model::setInstantText,
                onOpensChange = model::setOpensAt,
            )
        }
        rule.onNode(hasSetTextAction() and hasText(context.getString(R.string.market_editor_starting_bid)))
            .performTextInput("5")
        rule.waitForIdle()
        model.publish()
        until("the publish") { server.of("-/listings/publish").isNotEmpty() }
        val created = server.of("-/listings/create").single().fields
        assertEquals("auction", created["pricing"])
        assertEquals("500", created["price"])
    }

    @Test
    fun `an auction's minimum charge applies to its starting bid`() {
        val state = EditUiState(
            pricing = PricingModel.AUCTION,
            currency = Currency.GBP,
            priceText = "0.10",
            reserveText = "50",
            fees = AccountFees(platform = 5.0, minimums = mapOf("gbp" to 30L)),
        )
        assertTrue(priceBelowStripeMinimum(state))
    }

    @Test
    fun `reserve and buy-now must each exceed the starting bid`() {
        val base = EditUiState(pricing = PricingModel.AUCTION, currency = Currency.GBP, priceText = "10")
        assertEquals(R.string.market_editor_reserve_below_start to null, auctionProblems(base.copy(reserveText = "5")))
        assertEquals(null to R.string.market_editor_instant_below_start, auctionProblems(base.copy(instantText = "10")))
        assertEquals(
            R.string.market_editor_reserve_above_instant to null,
            auctionProblems(base.copy(reserveText = "30", instantText = "20")),
        )
        assertEquals(null to null, auctionProblems(base.copy(reserveText = "20", instantText = "30")))
    }

    @Test
    fun `a relisted auction's settings fill the editor once, and go with the publish`() {
        server.data(
            "-/listings/relist",
            """{"listing": {"id": "l1"}, "auction": {"reserve": 50000, "instant": 90000, "opens": 1000,
                "closes": ${1000 + 5 * 86_400}, "extend": 1, "extension": 10}}""",
        )
        runBlocking { repository.relistListing("l0") }
        val model = editor("l1")
        val state = model.state.value
        assertEquals("500.00", state.reserveText)
        assertEquals("900.00", state.instantText)
        assertEquals(5, state.durationDays)
        assertEquals("taken once", "", editor("l1").state.value.reserveText)
    }

    @Test
    fun `a relisted auction's anti-sniping settings are sent with the publish`() {
        server.data(
            "-/listings/viewer/get",
            """{"listing": {"id": "l1", "title": "Lamp", "price": 100, "currency": "gbp", "pricing": "auction",
                "type": "physical", "status": "draft"}}""",
        )
        server.data(
            "-/listings/relist",
            """{"listing": {"id": "l1"}, "auction": {"reserve": 0, "instant": 0, "opens": 0, "closes": 0, "extend": 1, "extension": 10}}""",
        )
        runBlocking { repository.relistListing("l0") }
        val model = editor("l1")
        model.publish()
        until("the publish") { server.of("-/listings/publish").isNotEmpty() }
        val fields = server.of("-/listings/publish").single().fields
        assertEquals("1", fields["extend"])
        assertEquals("10", fields["extension"])
    }

    @Test
    fun `a download defaults to unlimited stock until the seller sets it`() {
        val model = blank()
        model.setType(ListingType.DIGITAL)
        assertTrue(model.state.value.unlimitedStock)
        model.setType(ListingType.PHYSICAL)
        assertFalse(model.state.value.unlimitedStock)
        assertEquals("1", model.state.value.quantityText)
        model.setQuantity("3")
        model.setType(ListingType.DIGITAL)
        assertFalse("the seller's own stock stays", model.state.value.unlimitedStock)
    }

    @Test
    fun `the delivery note is loaded and sent`() {
        val model = editor()
        assertEquals("Posted in two days", model.state.value.information)
        model.setInformation("Collected from the shop")
        model.save()
        until("the save") { server.of("-/listings/update").isNotEmpty() }
        assertEquals("Collected from the shop", server.of("-/listings/update").first().fields["information"])
    }

    @Test
    fun `reorders take the server's answer`() {
        server.data("-/assets/reorder", """[{"id": "a2", "position": 0}, {"id": "a1", "position": 1}]""")
        server.on("-/photos/reorder") {
            okhttp3.mockwebserver.MockResponse().setBody(
                """{"data": [{"id": "p2", "rank": 0}, {"id": "p1", "rank": 1}]}""",
            )
        }
        val model = editor()
        model.reorderAssets(listOf(Asset(id = "a2"), Asset(id = "a1")))
        until("the asset order") { server.of("-/assets/reorder").isNotEmpty() }
        org.mochios.market.settle()
        // The positions are the server's: the order assumed on the phone carried none.
        assertEquals(listOf("a2" to 0L, "a1" to 1L), model.state.value.assets.map { it.id to it.position })
        model.reorderPhotos(listOf(org.mochios.market.model.Photo(id = "p2"), org.mochios.market.model.Photo(id = "p1")))
        until("the photo order") { server.of("-/photos/reorder").isNotEmpty() }
        org.mochios.market.settle()
        assertEquals(listOf("p2" to 0L, "p1" to 1L), model.state.value.photos.map { it.id to it.rank })
    }

    @Test
    fun `the price warning uses the server's minimum`() {
        assertEquals(30L, minimumCharge(EditUiState(currency = Currency.GBP)))
        assertEquals(100L, minimumCharge(EditUiState(currency = Currency.JPY)))
        assertEquals(40L, minimumCharge(EditUiState(currency = Currency.GBP, fees = AccountFees(minimums = mapOf("gbp" to 40L)))))
        var minimum = ""
        rule.setContent {
            minimum = formatPrice(100L, Currency.JPY)
            PricingModelSelector(
                pricing = PricingModel.FIXED,
                currency = Currency.JPY,
                priceText = "50",
                interval = Interval.MONTHLY,
                durationDays = 7,
                reserveText = "",
                instantText = "",
                opensAt = null,
                minimum = 100L,
                onPricingChange = {},
                onCurrencyChange = {},
                onPriceChange = {},
                onIntervalChange = {},
                onDurationChange = {},
                onReserveChange = {},
                onInstantChange = {},
                onOpensChange = {},
            )
        }
        val warning = context.getString(R.string.market_editor_price_below_min, minimum)
        assertEquals(1, rule.onAllNodesWithText(warning).fetchSemanticsNodes().size)
    }

    @Test
    fun `the fee preview takes the server's cut, rounded down`() {
        assertEquals(9L, mochiFee(199L, 5.0))
        var fee = ""
        var payout = ""
        rule.setContent {
            fee = formatPrice(9L, Currency.GBP)
            payout = formatPrice(190L, Currency.GBP)
            FeePreview(price = 199L, currency = Currency.GBP, pricing = PricingModel.FIXED, percent = 5.0)
        }
        assertEquals(1, rule.onAllNodesWithText("−$fee").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText(payout).fetchSemanticsNodes().size)
    }

    @Test
    fun `an auction's fee preview names the winning bid, not the starting price`() {
        var price = ""
        rule.setContent {
            price = formatPrice(500L, Currency.GBP)
            FeePreview(price = 500L, currency = Currency.GBP, pricing = PricingModel.AUCTION, percent = 5.0)
        }
        val line = context.getString(R.string.market_fee_preview_auction, "5")
        assertEquals(1, rule.onAllNodesWithText(line).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(price).fetchSemanticsNodes().size)
    }

    @Test
    fun `the reorder arrows are named`() {
        rule.setContent {
            DigitalAssetsManager(
                assets = listOf(Asset(id = "a1", filename = "one.zip"), Asset(id = "a2", filename = "two.zip")),
                onUpload = {},
                onDelete = {},
                onReorder = {},
                onAddExternal = { _, _, _ -> },
                upload = null,
            )
        }
        val up = context.getString(R.string.market_editor_move_up)
        val down = context.getString(R.string.market_editor_move_down)
        assertEquals(2, rule.onAllNodesWithContentDescription(up).fetchSemanticsNodes().size)
        assertEquals(2, rule.onAllNodesWithContentDescription(down).fetchSemanticsNodes().size)
    }
}
