// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Review` in `apps/staff/web/src/types/reviews.ts`. `role` says which
 * party wrote it (`buyer` or `seller`); `visible` is 0/1; the nullable name
 * fields are only populated on some list shapes.
 */
data class Review(
    val id: String = "",
    val order: String = "",
    val reviewer: String = "",
    @SerializedName("reviewer_name") val reviewerName: String? = null,
    @SerializedName("reviewer_fingerprint") val reviewerFingerprint: String? = null,
    val subject: String = "",
    @SerializedName("subject_name") val subjectName: String? = null,
    @SerializedName("subject_fingerprint") val subjectFingerprint: String? = null,
    val buyer: String? = null,
    @SerializedName("buyer_name") val buyerName: String? = null,
    val seller: String? = null,
    @SerializedName("seller_name") val sellerName: String? = null,
    val listing: String? = null,
    @SerializedName("listing_title") val listingTitle: String? = null,
    val role: String = "",
    val rating: Int = 0,
    val text: String = "",
    val response: String = "",
    val visible: Int = 0,
    val status: String = "",
    val created: Long = 0,
)

data class ReviewsListResponse(
    val reviews: List<Review> = emptyList(),
    val total: Long = 0,
)
