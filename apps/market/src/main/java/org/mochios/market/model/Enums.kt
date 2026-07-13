// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Server-side enum string values. Each constant uses `@SerializedName` so Gson
 * round-trips the lowercase wire value while Kotlin keeps an idiomatic
 * UPPER_SNAKE_CASE name.
 *
 * Source of truth: `apps/market/web/src/types/common.ts`.
 */
enum class Currency {
    @SerializedName("gbp") GBP,
    @SerializedName("usd") USD,
    @SerializedName("eur") EUR,
    @SerializedName("jpy") JPY,
}

enum class Condition {
    @SerializedName("new") NEW,
    @SerializedName("used") USED,
    @SerializedName("refurbished") REFURBISHED,
}

enum class ListingType {
    @SerializedName("physical") PHYSICAL,
    @SerializedName("digital") DIGITAL,
}

enum class PricingModel {
    @SerializedName("fixed") FIXED,
    @SerializedName("pwyw") PWYW,
    @SerializedName("subscription") SUBSCRIPTION,
    @SerializedName("auction") AUCTION,
}

enum class DeliveryMethod {
    @SerializedName("shipping") SHIPPING,
    @SerializedName("pickup") PICKUP,
    @SerializedName("download") DOWNLOAD,
}

enum class Interval {
    @SerializedName("monthly") MONTHLY,
    @SerializedName("yearly") YEARLY,
}

enum class ListingStatus {
    @SerializedName("draft") DRAFT,
    @SerializedName("active") ACTIVE,
    @SerializedName("sold") SOLD,
    @SerializedName("expired") EXPIRED,
    @SerializedName("rejected") REJECTED,
    @SerializedName("removed") REMOVED,
}

enum class SortOrder {
    @SerializedName("recent") RECENT,
    @SerializedName("price_low") PRICE_LOW,
    @SerializedName("price_high") PRICE_HIGH,
    @SerializedName("rating") RATING,
}

enum class ReportReason {
    @SerializedName("prohibited") PROHIBITED,
    @SerializedName("counterfeit") COUNTERFEIT,
    @SerializedName("misleading") MISLEADING,
    @SerializedName("inappropriate") INAPPROPRIATE,
    @SerializedName("spam") SPAM,
    @SerializedName("other") OTHER,
}

enum class ReportType {
    @SerializedName("listing") LISTING,
    @SerializedName("user") USER,
}
