// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * A market listing.
 *
 * Mirrors `Listing` in `apps/market/web/src/types/listings.ts`. Money values
 * are minor units (pence/cents); `tags` / `location` arrive as JSON strings.
 *
 * Optional flat seller fields (`seller_name`, `seller_rating`, …) only appear
 * on search/list responses and are absent on the canonical create/update
 * payload — keep them nullable so Gson deserialises both shapes.
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
 * Listing photo returned by `photos/list` / embedded in listings.
 *
 * Mirrors `Photo` in `apps/market/web/src/types/listings.ts`. `id` is the
 * comptroller-issued uid (string), not a numeric row id.
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
 * A digital asset attached to a listing.
 *
 * Mirrors `Asset` in `apps/market/web/src/types/listings.ts`. `hosting` is
 * either `"mochi"` (file streamed via comptroller) or `"external"` (reference
 * URL stored in the asset row).
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
 * Per-region shipping option configured by the seller for a physical listing.
 *
 * Mirrors `ShippingOption` in `apps/market/web/src/types/listings.ts`. `days`
 * is a free-text estimate (e.g. "3-5 working days"), not a parsed number.
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
 * Request DTO posted to `shipping/set`. Mirrors the inline `ShippingOptionInput`
 * type in `apps/market/web/src/api/shipping.ts`. Fields are stringly typed to
 * match what the Starlark handler reads via `a.input(...)`; the comptroller
 * coerces them to numbers before persisting.
 */
data class ShippingOptionInput(
    val region: String = "",
    val price: String = "",
    val currency: String = "",
    val days: String = "",
    val notes: String = "",
)

/**
 * Category entry from `categories/list`.
 *
 * Mirrors `Category` in `apps/market/web/src/types/listings.ts`. `digital` /
 * `physical` / `active` are 0/1 ints (Mochi server convention); `children` is
 * the count of direct sub-categories.
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
