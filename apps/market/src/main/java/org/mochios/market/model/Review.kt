// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * A buyer-on-seller (or seller-on-buyer) review attached to an order.
 *
 * Mirrors `Review` in `apps/market/web/src/types/reviews.ts`. `role` is
 * `"buyer"` or `"seller"` — the perspective of the reviewer. `response` is
 * the subject's reply (empty string if none). `visible` is 0/1 indicating
 * whether the review is publicly visible (moderation gate).
 */
data class Review(
    val id: String = "",
    val order: String = "",
    val reviewer: String = "",
    val subject: String = "",
    val role: String = "",
    val rating: Long = 0,
    val text: String = "",
    val response: String = "",
    val visible: Int = 0,
    val status: String = "",
    val created: Long = 0,
    @SerializedName("reviewer_name") val reviewerName: String? = null,
    @SerializedName("subject_name") val subjectName: String? = null,
    @SerializedName("listing_title") val listingTitle: String? = null,
)
