package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * One row from the staff reports queue (`reports/list`).
 *
 * Mirrors `Report` in `apps/staff/web/src/types/reports.ts`. `target` is
 * either a listing id (when `type == "listing"`) or an account fingerprint
 * (when `type == "user"`). `listing` is populated server-side for listing
 * reports — the small projection in [ReportListing] is rendered inline in
 * the queue without a second fetch.
 */
data class Report(
    val id: Long = 0,
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
    val id: Long = 0,
    val title: String = "",
    val seller: String = "",
    val price: Long = 0,
    val currency: String = "",
)

/**
 * Result of `reports/list`. Mirrors `ReportsResponse` in
 * `apps/staff/web/src/types/reports.ts`. Named `ReportsListResponse` here to
 * match the existing api file imports and the Android list-response naming
 * convention.
 */
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
 * Report lifecycle status. `pending` reports are queue items; staff transition
 * them to `actioned` (any of dismiss/warn/remove/suspend/ban created a
 * downstream effect) or `dismissed` (explicit no-action). Source:
 * `event_staff_reports_action` in `apps/comptroller/starlark/reports.star`.
 *
 * `REVIEWED` is kept for legacy compatibility (older Comptroller builds wrote
 * this value) and is treated as equivalent to `ACTIONED` by the UI.
 */
enum class ReportStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("reviewed") REVIEWED,
    @SerializedName("actioned") ACTIONED,
    @SerializedName("dismissed") DISMISSED,
}

/**
 * Actions a moderator can take from the report action dialog. The action is
 * forwarded verbatim to `event_staff_reports_action`, which validates it
 * against this exact set (see `apps/comptroller/starlark/reports.star`).
 *
 * - [DISMISS] closes the report without effect.
 * - [WARN] adds a moderation warning to the targeted listing.
 * - [REMOVE] takes down the targeted listing.
 * - [SUSPEND] suspends the targeted account (listing target -> seller).
 * - [BAN] bans the targeted account (listing target -> seller).
 */
enum class ReportAction {
    @SerializedName("dismiss") DISMISS,
    @SerializedName("warn") WARN,
    @SerializedName("remove") REMOVE,
    @SerializedName("suspend") SUSPEND,
    @SerializedName("ban") BAN,
}
