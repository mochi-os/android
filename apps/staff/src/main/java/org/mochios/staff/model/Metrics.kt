// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Per-currency revenue row inside [MetricsOverview].
 *
 * Mirrors `RevenueByCurrency` in `apps/staff/web/src/types/metrics.ts`.
 * `total` is the retained platform fee in that currency's minor units —
 * computed as `sum(fee - fee_refunded)` across all non-cancelled,
 * non-refunded orders (`event_staff_metrics_overview` in
 * `apps/comptroller/starlark/staff.star`).
 */
data class RevenueByCurrency(
    val currency: String = "",
    val total: Long = 0,
)

/**
 * Marketplace overview metrics (`metrics/overview`).
 *
 * Mirrors `MetricsOverview` in `apps/staff/web/src/types/metrics.ts`. `revenue`
 * is per-currency because Stripe transactions stay in their original currency
 * (no server-side aggregation across currencies). `pendingModeration` counts
 * listings whose moderation is in (`hold`, `review`).
 */
data class MetricsOverview(
    val listings: Long = 0,
    val orders: Long = 0,
    val revenue: List<RevenueByCurrency> = emptyList(),
    val sellers: Long = 0,
    val buyers: Long = 0,
    val disputes: Long = 0,
    @SerializedName("pending_moderation") val pendingModeration: Long = 0,
)

/**
 * One row in the activity feed's `orders` tab. Mirrors `ActivityOrder` in
 * `apps/staff/web/src/types/metrics.ts`. `total` is in minor currency units.
 */
data class ActivityOrder(
    val id: String = "",
    val listing: String = "",
    val buyer: String = "",
    @SerializedName("buyer_name") val buyerName: String = "",
    val seller: String = "",
    @SerializedName("seller_name") val sellerName: String = "",
    val total: Long = 0,
    val currency: String = "",
    val status: String = "",
    val title: String = "",
    val created: Long = 0,
)

/**
 * One row in the activity feed's `listings` tab. Mirrors `ActivityListing`
 * in `apps/staff/web/src/types/metrics.ts`.
 */
data class ActivityListing(
    val id: String = "",
    val seller: String = "",
    @SerializedName("seller_name") val sellerName: String = "",
    val title: String = "",
    val status: String = "",
    val moderation: String = "",
    val score: Double = 0.0,
    val created: Long = 0,
)

/**
 * One row in the activity feed's `signups` tab. Mirrors `ActivitySignup` in
 * `apps/staff/web/src/types/metrics.ts`. `seller` is 0/1: 1 indicates the
 * account has gone through seller onboarding.
 */
data class ActivitySignup(
    val id: String = "",
    val name: String = "",
    val seller: Int = 0,
    val created: Long = 0,
)

/**
 * Combined activity feed payload (`metrics/activity`). Mirrors `ActivityData`
 * in `apps/staff/web/src/types/metrics.ts`.
 *
 * Only the requested tab's fields are populated; everything else is null.
 * When called without a `tab` query parameter the Comptroller returns every
 * tab in one response (used by the dashboard's combined view).
 */
data class ActivityData(
    val orders: List<ActivityOrder>? = null,
    @SerializedName("orders_total") val ordersTotal: Long? = null,
    val listings: List<ActivityListing>? = null,
    @SerializedName("listings_total") val listingsTotal: Long? = null,
    val signups: List<ActivitySignup>? = null,
    @SerializedName("signups_total") val signupsTotal: Long? = null,
    val moderation: List<ModerationEntry>? = null,
    @SerializedName("moderation_total") val moderationTotal: Long? = null,
)
