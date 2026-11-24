// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * A payment Stripe settled with no order or subscription to carry it. Mirrors
 * `OrphanedPayment` in `apps/staff/web/src/types/payments.ts`. [id] is its
 * Stripe id, which is never shown; [amount] is in minor units of [currency];
 * [seller] is "" when the connected account names no seller.
 */
data class OrphanedPayment(
    val id: String = "",
    val amount: Long = 0,
    val currency: String = "",
    val seller: String = "",
    @SerializedName("seller_name") val sellerName: String = "",
    @SerializedName("seller_fingerprint") val sellerFingerprint: String = "",
    val created: Long = 0,
    val refunded: Boolean = false,
)

/** Result of `payments/orphaned`, newest first; [next] as on [AccountsListResponse]. */
data class OrphanedPaymentsResponse(
    val payments: List<OrphanedPayment> = emptyList(),
    val total: Long = 0,
    val next: String? = null,
)

/** What `payments/refund` answers: the refunds Stripe made and those it refused. */
data class PaymentRefund(
    val payment: String = "",
    val amount: Long = 0,
    val currency: String = "",
    val refunds: Int = 0,
    val failed: Int = 0,
)
