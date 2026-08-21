// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Review` in web `types/reviews.ts`. `role` is the reviewer's side;
 * `response` is the subject's reply; `visible` is the moderation gate.
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
