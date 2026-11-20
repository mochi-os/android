// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.checkout

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.market.FlowServer
import org.mochios.market.model.DeliveryMethod
import org.mochios.market.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Checkout: an auction is completed by its winner as the listing says, with
 * no list of bids; postage counts only for shipping; Pay waits for what the
 * server requires; the buyer's saved address fills the form; and the options
 * offered are those covering the buyer's country.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CheckoutFlowsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: FlowServer

    private val options = """[{"id": "s1", "listing": "l1", "region": "GB", "price": 300, "currency": "gbp"},
        {"id": "s2", "listing": "l1", "region": "europe", "price": 900, "currency": "gbp"},
        {"id": "s3", "listing": "l1", "region": "world", "price": 2000, "currency": "gbp"}]"""

    @Before
    fun begin() {
        AppContext.set(context)
        server = FlowServer()
        server.data(
            "-/listings/viewer/get",
            """{"listing": {"id": "l1", "title": "Lamp", "price": 1000, "currency": "gbp", "pricing": "fixed",
                "type": "physical", "status": "active", "shipping": 1, "pickup": 1}, "shipping": $options}""",
        )
        server.data("-/shipping/regions", """{"countries": ["GB", "FR", "US"], "groups": {"world": [], "europe": ["GB", "FR"]}}""")
        server.data("-/accounts/get", """{"id": "b1"}""")
        server.data("-/orders/auction", """{"order": null, "checkout_url": "https://checkout.stripe.com/x"}""")
        server.data("-/orders/create", """{"order": null, "checkout_url": "https://checkout.stripe.com/x"}""")
    }

    @After
    fun end() = server.stop()

    private fun model(): CheckoutViewModel {
        val model = CheckoutViewModel(SavedStateHandle(mapOf("listingId" to "l1")), server.repository(context))
        until("the checkout") { !model.uiState.value.isLoading }
        return model
    }

    private fun auction(status: String, mine: Boolean) = server.data(
        "-/listings/viewer/get",
        """{"listing": {"id": "l1", "title": "Lamp", "price": 100, "currency": "gbp", "pricing": "auction",
            "type": "digital", "status": "sold"},
            "auction": {"id": "a1", "listing": "l1", "status": "$status", "bid": 4200, "mine": $mine}}""",
    )

    @Test
    fun `the winner of an ended auction completes it, priced at the winning bid`() {
        auction("ended_sold", true)
        val model = model()
        val state = model.uiState.value
        assertTrue(state.wonAuction)
        assertEquals(4200L, state.itemPrice)
        assertTrue(state.canSubmit)
        model.submit()
        until("the order") { server.of("-/orders/auction").isNotEmpty() }
        assertEquals(0, server.of("-/orders/create").size)
        assertEquals(0, server.of("-/bids/mine").size)
    }

    @Test
    fun `an auction the caller did not win cannot be paid for`() {
        auction("ended_sold", false)
        val model = model()
        assertFalse(model.uiState.value.wonAuction)
        assertFalse(model.uiState.value.canSubmit)
        auction("active", true)
        assertFalse(model().uiState.value.canSubmit)
    }

    @Test
    fun `postage leaves the total once delivery is not by shipping`() {
        val model = model()
        model.onDeliveryChanged(DeliveryMethod.SHIPPING)
        model.onAddressChanged(country = "GB")
        assertEquals(1300L, model.uiState.value.total)
        model.onDeliveryChanged(DeliveryMethod.PICKUP)
        assertNull(model.uiState.value.selectedOption)
        assertEquals(1000L, model.uiState.value.total)
    }

    @Test
    fun `Pay waits for an option and the address the server requires`() {
        val model = model()
        model.onDeliveryChanged(DeliveryMethod.SHIPPING)
        assertFalse(model.uiState.value.canSubmit)
        model.onAddressChanged(country = "GB")
        assertFalse("an option alone is not enough", model.uiState.value.canSubmit)
        model.onAddressChanged(name = "Ana", line1 = "1 High Street")
        assertFalse("the city is still missing", model.uiState.value.canSubmit)
        assertEquals(setOf("address_city"), model.uiState.value.missing)
        model.onAddressChanged(city = "York")
        assertTrue(model.uiState.value.canSubmit)
        model.onDeliveryChanged(DeliveryMethod.PICKUP)
        assertTrue("pickup needs no address", model.uiState.value.canSubmit)
    }

    @Test
    fun `the buyer's saved address fills the form, with the cheapest option for its country`() {
        server.data(
            "-/accounts/get",
            """{"id": "b1", "address_name": "Ana", "address_line1": "1 Rue", "address_city": "Lyon", "address_country": "FR"}""",
        )
        val model = model()
        val state = model.uiState.value
        assertEquals("Ana", state.addressName)
        assertEquals("Lyon", state.addressCity)
        assertEquals("FR", state.addressCountry)
        assertEquals("s2", state.optionId)
    }

    @Test
    fun `only options covering the buyer's country are offered`() {
        val model = model()
        model.onDeliveryChanged(DeliveryMethod.SHIPPING)
        model.onAddressChanged(country = "US")
        assertEquals(listOf("s3"), model.uiState.value.options.map { it.id })
        assertEquals("s3", model.uiState.value.optionId)
        model.onAddressChanged(country = "GB")
        assertEquals(listOf("s1", "s2", "s3"), model.uiState.value.options.map { it.id })
        assertEquals("s1", model.uiState.value.optionId)
    }
}
