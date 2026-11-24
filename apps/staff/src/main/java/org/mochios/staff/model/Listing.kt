// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName
import org.json.JSONArray
import org.mochios.android.api.OwnedAddress
import org.mochios.android.util.segment

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
 * `apps/staff/web/src/types/listings.ts`; [next] as on
 * [AccountsListResponse].
 */
data class PendingListingsResponse(
    val listings: List<PendingListing> = emptyList(),
    val total: Long = 0,
    val next: String? = null,
)

/**
 * Result of `listings/get`: the listing row (its seller fields blank, the
 * seller being in [seller]), its photos in order, and its moderation history
 * as `moderation/log` rows, newest first.
 */
data class ListingView(
    val listing: PendingListing = PendingListing(),
    val seller: ListingSeller = ListingSeller(),
    val photos: List<ListingPhoto> = emptyList(),
    val history: List<ModerationEntry> = emptyList(),
)

data class ListingSeller(
    val id: String = "",
    val name: String = "",
    val fingerprint: String = "",
)

data class ListingPhoto(
    val id: String = "",
    val position: Long = 0,
)

/**
 * The staff console's address of [listing]'s photo [photo], its [thumbnail]
 * or the full image, fetched with the staff app's asset credential: the
 * market's own photo route would serve a held or rejected listing's photo to
 * nobody but its seller. Null when an id, a server's, cannot be written into
 * the address ([segment]).
 */
fun listingPhotoAddress(listing: String, photo: String, thumbnail: Boolean = false): OwnedAddress? {
    val listingPart = segment(listing) ?: return null
    val photoPart = segment(photo) ?: return null
    val size = if (thumbnail) "&size=thumbnail" else ""
    return OwnedAddress.of("/staff/-/listings/photo?listing=$listingPart&photo=$photoPart$size")
}

/**
 * One risk factor behind a listing's moderation score, as the scorer wrote it
 * into the listing's `factors`: [reason] is its own words.
 */
data class ListingFactor(
    val name: String = "",
    val score: Double = 0.0,
    val reason: String = "",
)

/** A listing's `factors` column read as its factors; none for one never scored or not readable. */
fun parseFactors(factors: String): List<ListingFactor> {
    if (factors.isBlank()) return emptyList()
    val array = try {
        JSONArray(factors)
    } catch (_: Exception) {
        return emptyList()
    }
    return (0 until array.length()).mapNotNull { index ->
        val factor = array.optJSONObject(index) ?: return@mapNotNull null
        ListingFactor(
            name = factor.optString("name"),
            score = factor.optDouble("score", 0.0),
            reason = factor.optString("reason"),
        ).takeIf { read -> read.reason.isNotBlank() }
    }
}
