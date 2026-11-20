// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.market.FlowServer
import org.mochios.market.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The seller's refund sends only what the server reads (no reason, and no
 * amount for the whole remainder), and an order shipped or refunded keeps the
 * buyer's name, since the server answers the order as the order page reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SaleDetailFlowsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: FlowServer

    private val order = """{"id": "o1", "listing": "l1", "buyer": "b1", "seller": "s1", "total": 1000,
        "currency": "gbp", "status": "paid", "delivery": "shipping", "buyer_name": "Ana", "buyer_fingerprint": "abcdefghi"}"""

    @Before
    fun begin() {
        AppContext.set(context)
        server = FlowServer()
        server.data("-/orders/get", """{"order": $order, "listing": {"id": "l1", "title": "Lamp"}}""")
        server.data("-/audit/object", """{"audit": [], "total": 0}""")
        server.data("-/orders/refund", """{"order": ${order.replace("\"paid\"", "\"refunded\"")}, "dispute": null}""")
        server.data("-/orders/ship", order.replace("\"paid\"", "\"shipped\""))
    }

    @After
    fun end() = server.stop()

    private fun model(): SaleDetailViewModel {
        val model = SaleDetailViewModel(server.repository(context), SavedStateHandle(mapOf("orderId" to "o1")))
        until("the order") { model.state.value.order != null }
        return model
    }

    @Test
    fun `a whole refund sends no amount and no reason`() {
        val model = model()
        model.refundOrder(null, "failed")
        until("the refund") { server.of("-/orders/refund").isNotEmpty() && !model.state.value.refundSubmitting }
        val fields = server.of("-/orders/refund").single().fields
        assertEquals("o1", fields["id"])
        assertFalse("a whole refund names no amount: $fields", "amount" in fields)
        assertFalse("nothing reads a reason: $fields", "reason" in fields)
    }

    @Test
    fun `a part refund sends its amount`() {
        val model = model()
        model.refundOrder(250, "failed")
        until("the refund") { server.of("-/orders/refund").isNotEmpty() && !model.state.value.refundSubmitting }
        assertEquals("250", server.of("-/orders/refund").single().fields["amount"])
    }

    @Test
    fun `the buyer's name stays after shipping and refunding`() {
        val model = model()
        model.shipOrder("Royal Mail", "RM1", "", "shipped", "failed")
        until("the shipment") { model.state.value.order?.order?.status?.name == "SHIPPED" }
        assertEquals("Ana", model.state.value.order?.order?.buyerName)
        model.refundOrder(null, "failed")
        until("the refund") { model.state.value.order?.order?.status?.name == "REFUNDED" }
        assertEquals("Ana", model.state.value.order?.order?.buyerName)
    }
}
