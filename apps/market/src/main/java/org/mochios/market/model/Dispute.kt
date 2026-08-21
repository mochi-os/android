// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Dispute` in `apps/market/web/src/api/disputes.ts`. `opener` is the
 * buyer id, or `"stripe"` for a chargeback, in which case `reason` is a Stripe
 * chargeback code; `fee` is Stripe's chargeback fee in minor units and
 * `evidence_due` the seller's evidence deadline.
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
    @SerializedName("evidence_due") val evidenceDue: Long = 0,
    @SerializedName("refund_amount") val refundAmount: Long = 0,
    val created: Long = 0,
    val resolved: Long = 0,
)

/**
 * Manual dispute reasons; source `DisputeReason` in web `types/common.ts`.
 * Stripe chargeback codes arrive as free-form `Dispute.reason` instead.
 */
enum class DisputeReason {
    @SerializedName("not_received") NOT_RECEIVED,
    @SerializedName("not_as_described") NOT_AS_DESCRIBED,
    @SerializedName("damaged") DAMAGED,
    @SerializedName("unauthorised") UNAUTHORISED,
    @SerializedName("other") OTHER,
}

/**
 * Known dispute statuses; the server's `status` is free-form, so callers must
 * tolerate unknown values.
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
 * Dispute evidence entry, drawn from the audit stream. File entries fill [name]
 * / [size] / [url] / [mime]; text notes use [body] only. [role] is `"buyer"`,
 * `"seller"` or `"staff"`.
 */
data class DisputeEvidence(
    val id: String = "",
    val dispute: String = "",
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
