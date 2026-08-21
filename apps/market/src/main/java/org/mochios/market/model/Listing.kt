// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Listing` in `apps/market/web/src/types/listings.ts`. Money in minor
 * units; `tags` / `location` are JSON strings; the `seller_*` fields appear
 * only on search/list responses.
 */
data class Listing(
    val id: String = "",
    val seller: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val tags: String = "",
    val condition: Condition? = null,
    val type: ListingType? = null,
    val pricing: PricingModel? = null,
    val price: Long = 0,
    val currency: Currency? = null,
    val interval: Interval? = null,
    val pickup: Long = 0,
    val shipping: Long = 0,
    val location: String = "",
    val information: String = "",
    val quantity: Long = 0,
    val score: Double = 0.0,
    val factors: String = "",
    val moderation: String = "",
    val moderator: String = "",
    val moderated: Long = 0,
    val notes: String = "",
    val status: ListingStatus? = null,
    val created: Long = 0,
    val updated: Long = 0,
    val photo: Photo? = null,
    @SerializedName("seller_name") val sellerName: String? = null,
    @SerializedName("seller_rating") val sellerRating: Double? = null,
    @SerializedName("seller_reviews") val sellerReviews: Long? = null,
    @SerializedName("seller_onboarded") val sellerOnboarded: Long? = null,
)

/**
 * Mirrors `Photo` in web `types/listings.ts`; `id` is a comptroller uid, not a
 * row number.
 */
data class Photo(
    val id: String = "",
    val `object`: String = "",
    val name: String = "",
    val size: Long = 0,
    @SerializedName("content_type") val contentType: String = "",
    val rank: Long = 0,
    val created: Long = 0,
    val image: Boolean = false,
)

/**
 * Mirrors `Asset` in web `types/listings.ts`; `hosting` is `"mochi"` (streamed
 * via the comptroller) or `"external"`.
 */
data class Asset(
    val id: String = "",
    val listing: String = "",
    val hosting: String = "",
    val filename: String = "",
    val size: Long = 0,
    val mime: String = "",
    val position: Long = 0,
)

/**
 * Mirrors `ShippingOption` in web `types/listings.ts`; `days` is free text.
 */
data class ShippingOption(
    val id: String = "",
    val listing: String = "",
    val region: String = "",
    val price: Long = 0,
    val currency: String = "",
    val days: String = "",
    val notes: String = "",
)

/**
 * Request body for `shipping/set`; stringly typed because the Starlark handler
 * reads `a.input(...)`.
 */
data class ShippingOptionInput(
    val region: String = "",
    val price: String = "",
    val currency: String = "",
    val days: String = "",
    val notes: String = "",
)

/**
 * Mirrors `Category` in web `types/listings.ts`; `children` is the count of
 * direct sub-categories.
 */
data class Category(
    val id: String = "",
    val parent: String = "",
    val name: String = "",
    val slug: String = "",
    val icon: String = "",
    val digital: Int = 0,
    val physical: Int = 0,
    val position: Long = 0,
    val active: Int = 0,
    val children: Long = 0,
)
