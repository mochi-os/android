// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * A recurring subscription against a subscription-type listing.
 *
 * Mirrors `Subscription` in `apps/market/web/src/types/subscriptions.ts`.
 * `amount` is the recurring charge in minor units; `interval` is `monthly`
 * or `yearly` (see [Interval] in `Enums.kt`). `cancelled` is the timestamp
 * the subscription was cancelled (0 if active).
 */
data class Subscription(
    val id: String = "",
    val listing: String = "",
    val buyer: String = "",
    val seller: String = "",
    val stripe: String = "",
    val interval: Interval? = null,
    val amount: Long = 0,
    val currency: Currency? = null,
    val status: SubscriptionStatus? = null,
    val starts: Long = 0,
    val ends: Long = 0,
    val created: Long = 0,
    val cancelled: Long = 0,
    val title: String? = null,
    @SerializedName("listing_type") val listingType: String? = null,
    @SerializedName("buyer_name") val buyerName: String? = null,
)

/**
 * Subscription lifecycle status. Source: `SubscriptionStatus` in
 * `apps/market/web/src/types/common.ts`.
 *
 * `pending` covers the brief window after Stripe Checkout completes but
 * before the first invoice has been settled; `past_due` indicates Stripe is
 * retrying a failed renewal charge.
 */
enum class SubscriptionStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("active") ACTIVE,
    @SerializedName("paused") PAUSED,
    @SerializedName("past_due") PAST_DUE,
    @SerializedName("cancelled") CANCELLED,
}
