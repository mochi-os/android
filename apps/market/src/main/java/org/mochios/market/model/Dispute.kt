package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Refund dispute opened against an order.
 *
 * Mirrors `Dispute` in `apps/market/web/src/api/disputes.ts` (defined there
 * rather than under `/types/` because disputes share the orders surface).
 *
 * `opener` is either the buyer entity id (manual dispute) or the literal
 * string `"stripe"` when surfaced from a Stripe chargeback — in the latter
 * case `reason` is one of Stripe's chargeback codes (see
 * `useStripeChargebackReasons()` on the web side). `fee` is Stripe's flat
 * chargeback fee in minor units; `fee_refunded` is 1 if Stripe later
 * refunded the fee because the dispute was won. `evidence_due` is the
 * unix-seconds deadline by which the seller must submit evidence on Stripe.
 */
data class Dispute(
    val id: Long = 0,
    val order: Long = 0,
    val opener: String = "",
    val reason: String = "",
    val description: String = "",
    val status: String = "",
    val response: String = "",
    val resolution: String = "",
    val resolver: String = "",
    val fee: Long = 0,
    @SerializedName("fee_refunded") val feeRefunded: Int = 0,
    @SerializedName("evidence_due") val evidenceDue: Long = 0,
    @SerializedName("refund_amount") val refundAmount: Long = 0,
    val created: Long = 0,
    val resolved: Long = 0,
)

/**
 * Reason a buyer cites when opening a manual (non-chargeback) dispute.
 *
 * Source: `DisputeReason` in `apps/market/web/src/types/common.ts`. Stripe
 * chargeback codes are not part of this enum — they arrive as free-form
 * strings on `Dispute.reason` when `opener == "stripe"`.
 */
enum class DisputeReason {
    @SerializedName("not_received") NOT_RECEIVED,
    @SerializedName("not_as_described") NOT_AS_DESCRIBED,
    @SerializedName("damaged") DAMAGED,
    @SerializedName("unauthorised") UNAUTHORISED,
    @SerializedName("other") OTHER,
}

/**
 * Dispute lifecycle status.
 *
 * The server treats `status` as a free-form string (see `Dispute.status`
 * above) so this enum is non-authoritative — it covers the values the web
 * UI currently switches on. Unknown statuses must be tolerated by callers.
 */
enum class DisputeStatus {
    @SerializedName("open") OPEN,
    @SerializedName("responded") RESPONDED,
    @SerializedName("resolved_refund") RESOLVED_REFUND,
    @SerializedName("resolved_partial") RESOLVED_PARTIAL,
    @SerializedName("resolved_seller") RESOLVED_SELLER,
    @SerializedName("escalated") ESCALATED,
    @SerializedName("cancelled") CANCELLED,
}

/**
 * Evidence document/note attached to a dispute by either party. The server
 * stores evidence in the audit stream rather than a dedicated table; this
 * struct mirrors the per-entry shape used when the audit timeline is
 * rendered as a dispute conversation.
 *
 * When the entry represents an uploaded file, [name]/[size]/[url]/[mime]
 * are populated. Pure text notes leave the file fields blank and put the
 * note in [body]. [role] is `"buyer"`, `"seller"`, or `"staff"`.
 */
data class DisputeEvidence(
    val id: Long = 0,
    val dispute: Long = 0,
    val actor: String = "",
    @SerializedName("actor_name") val actorName: String = "",
    val role: String = "",
    val body: String = "",
    val name: String = "",
    val size: Long = 0,
    val url: String = "",
    val mime: String = "",
    val created: Long = 0,
)
