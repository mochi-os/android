package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * One row from the staff disputes queue (`disputes/list`).
 *
 * Mirrors `Dispute` in `apps/staff/web/src/types/disputes.ts`. The `opener`
 * field is either the buyer's entity id (manual dispute opened from the
 * order detail page) or the literal string `"stripe"` when the dispute was
 * surfaced from a Stripe chargeback webhook. When `opener == "stripe"`,
 * `reason` carries Stripe's chargeback reason code (e.g.
 * `"product_not_received"`, `"fraudulent"`, `"unrecognized"`) and the
 * dispute is read-only in the staff UI — the seller must respond on Stripe
 * directly before the [evidenceDue] deadline.
 *
 * Money values are minor currency units; timestamps are unix seconds.
 *
 * [chargebackFee] and [chargebackReason] are convenience aliases for [fee]
 * and the Stripe-coded reason — exposed as nullable so the chargeback view
 * can render a "no fee charged" state cleanly when [fee] is 0.
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
     * Convenience: chargeback fee in minor units when [opener] is `"stripe"`,
     * else null. The chargeback view branches on this rather than checking
     * [fee] directly so a non-chargeback dispute with a zero fee remains
     * distinguishable from a chargeback that incurred no flat fee.
     */
    val chargebackFee: Long?
        get() = if (opener == "stripe") fee else null

    /**
     * Convenience: Stripe-coded chargeback reason (e.g. `"fraudulent"`,
     * `"product_not_received"`) when this dispute came from a chargeback
     * webhook, else null.
     */
    val chargebackReason: String?
        get() = if (opener == "stripe") reason else null
}

/**
 * Result of `disputes/list`. Mirrors `DisputesResponse` in
 * `apps/staff/web/src/types/disputes.ts`. Named `DisputesListResponse` here
 * to match the existing api file imports.
 */
data class DisputesListResponse(
    val disputes: List<Dispute> = emptyList(),
    val total: Long = 0,
)

/**
 * Dispute lifecycle status. Sourced from `event_staff_disputes_review` in
 * `apps/comptroller/starlark/disputes.star`. The status field is free-form
 * on the wire; these are the values the staff UI switches on.
 */
enum class DisputeStatus {
    @SerializedName("open") OPEN,
    @SerializedName("responded") RESPONDED,
    @SerializedName("resolved_buyer") RESOLVED_BUYER,
    @SerializedName("resolved_seller") RESOLVED_SELLER,
}

/**
 * Resolution decision a moderator submits when reviewing a dispute. The
 * Comptroller validates against this exact set (see
 * `event_staff_disputes_review` in `apps/comptroller/starlark/disputes.star`);
 * the corresponding `status` is `resolved_buyer` or `resolved_seller`.
 *
 * - [BUYER] refunds the buyer (full or partial via `refund_amount`).
 * - [SELLER] resolves in the seller's favour; no refund issued.
 */
enum class DisputeResolution {
    @SerializedName("resolved_buyer") BUYER,
    @SerializedName("resolved_seller") SELLER,
}
