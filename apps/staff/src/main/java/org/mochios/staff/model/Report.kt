// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Report` in `apps/staff/web/src/types/reports.ts`. `target` is a
 * listing id when `type == "listing"`, else an account id; `listing` and the
 * names and fingerprints are filled server-side.
 */
data class Report(
    val id: String = "",
    val target: String = "",
    val type: String = "",
    val reporter: String = "",
    @SerializedName("reporter_name") val reporterName: String = "",
    @SerializedName("reporter_fingerprint") val reporterFingerprint: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "",
    val reviewer: String = "",
    val reviewed: Long = 0,
    val created: Long = 0,
    val listing: ReportListing? = null,
    @SerializedName("seller_name") val sellerName: String = "",
    @SerializedName("seller_fingerprint") val sellerFingerprint: String = "",
    @SerializedName("target_name") val targetName: String = "",
    @SerializedName("target_fingerprint") val targetFingerprint: String = "",
)

/**
 * Listing projection embedded on [Report] when `type == "listing"`.
 * Mirrors `ReportListing` in `apps/staff/web/src/types/reports.ts`.
 */
data class ReportListing(
    val id: String = "",
    val title: String = "",
    val seller: String = "",
    val price: Long = 0,
    val currency: String = "",
)

data class ReportsListResponse(
    val reports: List<Report> = emptyList(),
    val total: Long = 0,
)
