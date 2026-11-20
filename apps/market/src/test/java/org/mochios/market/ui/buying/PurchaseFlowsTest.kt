// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.market.FlowServer
import org.mochios.market.R
import org.mochios.market.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A purchase's page names its delivery in the reader's language and shows no
 * raw order id, and confirming receipt keeps the seller's name, since the
 * server answers the order as the page reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PurchaseFlowsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: FlowServer

    private val order = """{"id": "o1", "listing": "l1", "buyer": "b1", "seller": "s1", "total": 1000, "item": 1000,
        "currency": "gbp", "status": "paid", "delivery": "pickup", "seller_name": "Bea"}"""

    @Before
    fun begin() {
        AppContext.set(context)
        server = FlowServer()
        server.data("-/orders/get", """{"order": $order, "listing": {"id": "l1", "title": ""}}""")
        server.data("-/audit/object", """{"audit": [], "total": 0}""")
        server.data("-/orders/confirm", order.replace("\"paid\"", "\"completed\""))
    }

    @After
    fun end() = server.stop()

    private fun model(): PurchaseDetailViewModel {
        val model = PurchaseDetailViewModel(SavedStateHandle(mapOf("orderId" to "o1")), server.repository(context), context)
        until("the order") { model.uiState.value.detail != null }
        return model
    }

    @Test
    fun `the summary names the delivery and shows no order id`() {
        val model = model()
        rule.setContent { PurchaseDetailScreen(navController = rememberNavController(), viewModel = model) }
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText(context.getString(R.string.market_checkout_delivery_pickup)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("pickup").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("#o1").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Order #o1").fetchSemanticsNodes().size)
    }

    @Test
    fun `confirming receipt keeps the seller's name`() {
        val model = model()
        model.confirmDelivery()
        until("the confirmation") { model.uiState.value.detail?.order?.status?.name == "COMPLETED" }
        assertEquals("Bea", model.uiState.value.detail?.order?.sellerName)
    }
}
