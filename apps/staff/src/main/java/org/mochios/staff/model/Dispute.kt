// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Dispute` in `apps/staff/web/src/types/disputes.ts`. `opener` is the
 * buyer's entity id, or `"stripe"` for a chargeback - then `reason` is Stripe's
 * reason code and the dispute is read-only here until [evidenceDue]. Money is
 * in minor units, timestamps in unix seconds.
 */
data class Dispute(
    val id: String = "",
    val order: String = "",
    val opener: String = "",
    val reason: String = "",
    val description: String = "",
    val status: String = "",
    val response: String = "",
    val resolution: String = "",
    val resolver: String = "",
    /** Who closed it: "staff", "seller", "stripe", or "buyer" for one the buyer withdrew. */
    val decider: String = "",
    val fee: Long = 0,
    @SerializedName("fee_refunded") val feeRefunded: Int = 0,
    /** Stripe `evidence_details.due_by` — Unix seconds. Zero when not applicable. */
    @SerializedName("evidence_due") val evidenceDue: Long = 0,
    @SerializedName("refund_amount") val refundAmount: Long = 0,
    /** Total already refunded against this order (minor units), across prior
     *  resolutions. The remaining refundable amount is total - orderRefunded. */
    @SerializedName("order_refunded") val orderRefunded: Long = 0,
    val resolved: Long = 0,
    val created: Long = 0,
    val listing: String = "",
    val buyer: String = "",
    @SerializedName("buyer_name") val buyerName: String = "",
    @SerializedName("buyer_fingerprint") val buyerFingerprint: String = "",
    val seller: String = "",
    @SerializedName("seller_name") val sellerName: String = "",
    @SerializedName("seller_fingerprint") val sellerFingerprint: String = "",
    val total: Long = 0,
    val currency: String = "",
    val title: String = "",
)

/** Result of `disputes/list`; [next] as on [AccountsListResponse]. */
data class DisputesListResponse(
    val disputes: List<Dispute> = emptyList(),
    val total: Long = 0,
    val next: String? = null,
)
