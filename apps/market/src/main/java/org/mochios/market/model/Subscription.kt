// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Subscription` in web `types/subscriptions.ts`. `amount` in minor
 * units; `cancelled` is 0 while active.
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
 * Source: `SubscriptionStatus` in web `types/common.ts`. `pending` is the
 * window between Checkout and the first settled invoice; `past_due` means
 * Stripe is retrying a failed renewal.
 */
enum class SubscriptionStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("active") ACTIVE,
    @SerializedName("paused") PAUSED,
    @SerializedName("past_due") PAST_DUE,
    @SerializedName("cancelled") CANCELLED,
}
