// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Listing row as surfaced by the staff `listings/pending` endpoint and the
 * activity feed.
 *
 * Mirrors `PendingListing` in `apps/staff/web/src/types/listings.ts`. `price`
 * is in minor currency units; `currency` is a free-form lowercase code (see
 * `Currency` on the market side); `status` and `moderation` are free-form
 * strings whose known values are enumerated in [ListingStatus] and
 * [ModerationState] respectively.
 *
 * Named `PendingListing` here (matching the TS source) because the SPA
 * endpoint and the Android api file both use that name; the row schema is
 * the same as `Listing` on the public market with a few extra denormalised
 * seller fields.
 */
data class PendingListing(
    val id: String = "",
    val seller: String = "",
    val title: String = "",
    val description: String = "",
    val type: String = "",
    val condition: String = "",
    val pricing: String = "",
    val price: Long = 0,
    val currency: String = "",
    val status: String = "",
    val moderation: String = "",
    val score: Double = 0.0,
    val factors: String = "",
    @SerializedName("seller_name") val sellerName: String = "",
    @SerializedName("seller_rating") val sellerRating: Double = 0.0,
    @SerializedName("seller_onboarded") val sellerOnboarded: Int = 0,
    val created: Long = 0,
    val updated: Long = 0,
)

/**
 * Result of `listings/pending`. Mirrors `PendingListingsResponse` in
 * `apps/staff/web/src/types/listings.ts`.
 */
data class PendingListingsResponse(
    val listings: List<PendingListing> = emptyList(),
    val total: Long = 0,
)

/**
 * Listing lifecycle status. Wire string is free-form (see the listings table
 * in `apps/comptroller/starlark/comptroller.star`); these are the values the
 * staff UI filters by. Unknown statuses must be tolerated.
 */
enum class ListingStatus {
    @SerializedName("draft") DRAFT,
    @SerializedName("active") ACTIVE,
    @SerializedName("sold") SOLD,
    @SerializedName("expired") EXPIRED,
    @SerializedName("rejected") REJECTED,
    @SerializedName("removed") REMOVED,
}

/**
 * Moderation state on a listing. Sourced from the listings table's
 * `moderation` column — the Comptroller writes the lowercase strings below.
 * Free-form on the wire; unknown states must be tolerated.
 */
enum class ModerationState {
    @SerializedName("pending") PENDING,
    @SerializedName("auto_approved") AUTO_APPROVED,
    @SerializedName("approved") APPROVED,
    @SerializedName("hold") HOLD,
    @SerializedName("review") REVIEW,
    @SerializedName("manual") MANUAL,
    @SerializedName("rejected") REJECTED,
    @SerializedName("appealed") APPEALED,
}
