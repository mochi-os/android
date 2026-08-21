// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Report` in `apps/staff/web/src/types/reports.ts`. `target` is a
 * listing id when `type == "listing"`, else an account fingerprint; `listing`
 * is filled server-side for listing reports.
 */
data class Report(
    val id: String = "",
    val target: String = "",
    val type: String = "",
    val reporter: String = "",
    @SerializedName("reporter_name") val reporterName: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "",
    val reviewer: String = "",
    val reviewed: Long = 0,
    val created: Long = 0,
    val listing: ReportListing? = null,
    @SerializedName("seller_name") val sellerName: String = "",
    @SerializedName("target_name") val targetName: String = "",
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

/**
 * What kind of object a report targets.
 */
enum class ReportType {
    @SerializedName("listing") LISTING,
    @SerializedName("user") USER,
}

/**
 * `reviewed` is a legacy value older Comptroller builds wrote; treat it as
 * `actioned`.
 */
enum class ReportStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("reviewed") REVIEWED,
    @SerializedName("actioned") ACTIONED,
    @SerializedName("dismissed") DISMISSED,
}

/**
 * Validated by `event_staff_reports_action`. `warn`/`remove` act on the
 * listing; `suspend`/`ban` act on the account (a listing target resolves to its
 * seller).
 */
enum class ReportAction {
    @SerializedName("dismiss") DISMISS,
    @SerializedName("warn") WARN,
    @SerializedName("remove") REMOVE,
    @SerializedName("suspend") SUSPEND,
    @SerializedName("ban") BAN,
}
