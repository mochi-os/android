// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.market.model.Dispute
import org.mochios.market.model.Order
import org.mochios.market.model.OrderDetailResponse
import org.mochios.market.model.OrderStatus

/**
 * The sale page offers Issue refund only where the server accepts one: money
 * left to refund, a paid order not refunded or cancelled, and no open
 * chargeback.
 */
class RefundableTest {

    private fun detail(
        status: OrderStatus = OrderStatus.PAID,
        total: Long = 2000,
        refunded: Long = 0,
        dispute: Dispute? = null,
    ) = OrderDetailResponse(
        order = Order(status = status, total = total, refunded = refunded),
        dispute = dispute,
    )

    @Test
    fun `a paid order with money left is refundable`() {
        assertTrue(refundable(detail()))
        assertTrue(refundable(detail(status = OrderStatus.SHIPPED, refunded = 500)))
    }

    @Test
    fun `a free order has nothing to refund`() {
        assertFalse(refundable(detail(total = 0)))
    }

    @Test
    fun `a fully refunded or cancelled order is not refundable`() {
        assertFalse(refundable(detail(refunded = 2000)))
        assertFalse(refundable(detail(status = OrderStatus.REFUNDED, refunded = 2000)))
        assertFalse(refundable(detail(status = OrderStatus.CANCELLED)))
    }

    @Test
    fun `an open chargeback blocks a refund`() {
        val chargeback = Dispute(opener = "stripe", status = "open")
        assertFalse(refundable(detail(status = OrderStatus.DISPUTED, dispute = chargeback)))
    }

    @Test
    fun `a closed chargeback or a buyer's dispute does not`() {
        val closed = Dispute(opener = "stripe", status = "resolved_seller")
        assertTrue(refundable(detail(dispute = closed)))
        val buyer = Dispute(opener = "buyer", status = "open")
        assertTrue(refundable(detail(status = OrderStatus.DISPUTED, dispute = buyer)))
    }
}
