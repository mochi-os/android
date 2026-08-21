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
    val seller: String = "",
    @SerializedName("seller_name") val sellerName: String = "",
    val total: Long = 0,
    val currency: String = "",
    val title: String = "",
) {
    /**
     * [fee] for a chargeback, null otherwise - a zero-fee chargeback stays
     * distinct from a manual dispute.
     */
    val chargebackFee: Long?
        get() = if (opener == "stripe") fee else null

    val chargebackReason: String?
        get() = if (opener == "stripe") reason else null
}

data class DisputesListResponse(
    val disputes: List<Dispute> = emptyList(),
    val total: Long = 0,
)

/**
 * Known `status` values, written by `event_staff_disputes_review`; free-form on
 * the wire.
 */
enum class DisputeStatus {
    @SerializedName("open") OPEN,
    @SerializedName("responded") RESPONDED,
    @SerializedName("resolved_buyer") RESOLVED_BUYER,
    @SerializedName("resolved_seller") RESOLVED_SELLER,
}

/**
 * `resolved_buyer` refunds the buyer (partial via `amount`); `resolved_seller`
 * issues no refund.
 */
enum class DisputeResolution {
    @SerializedName("resolved_buyer") BUYER,
    @SerializedName("resolved_seller") SELLER,
}
