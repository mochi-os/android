// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `PendingListing` in `apps/staff/web/src/types/listings.ts` (the
 * public `Listing` plus denormalised seller fields). `price` is in minor units;
 * `status` and `moderation` are free-form on the wire.
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
    @SerializedName("seller_fingerprint") val sellerFingerprint: String = "",
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
