// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.api

import org.mochios.android.api.ApiResponse
import org.mochios.staff.model.AccountSummary
import org.mochios.staff.model.AccountsListResponse
import org.mochios.staff.model.ActivityData
import org.mochios.staff.model.AppealsListResponse
import org.mochios.staff.model.AuditListResponse
import org.mochios.staff.model.Category
import org.mochios.staff.model.ConfigEntry
import org.mochios.staff.model.DirectorySearchResponse
import org.mochios.staff.model.Dispute
import org.mochios.staff.model.DisputesListResponse
import org.mochios.staff.model.Me
import org.mochios.staff.model.MetricsOverview
import org.mochios.staff.model.ModerationLogResponse
import org.mochios.staff.model.OkResponse
import org.mochios.staff.model.PendingListingsResponse
import org.mochios.staff.model.Report
import org.mochios.staff.model.ReportsListResponse
import org.mochios.staff.model.Review
import org.mochios.staff.model.ReviewsListResponse
import org.mochios.staff.model.StaffMember
import org.mochios.staff.model.Thresholds
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for the staff app, which proxies every action verbatim to
 * the Comptroller. Paths mirror the `-/...` actions in `apps/staff/app.json`,
 * relative to the per-app `<server>/staff/` base URL; mutations are
 * form-encoded POSTs. Static asset routes are fetched as plain URLs, not
 * modelled here.
 */
interface StaffApi {

    // ---- Me ----

    /**
     * Caller's staff record; `role` is "" for a non-staff identity.
     */
    @GET("-/me")
    suspend fun getMe(): Response<ApiResponse<Me>>

    // ---- Team ----

    /** List staff team members (resolved names + addedby_name included). */
    @GET("-/team/list")
    suspend fun listTeam(): Response<ApiResponse<List<StaffMember>>>

    /** Add a staff team member. */
    @FormUrlEncoded
    @POST("-/team/add")
    suspend fun addTeamMember(
        @Field("id") id: String,
        @Field("role") role: String,
    ): Response<ApiResponse<StaffMember>>

    /** Remove a staff team member. */
    @FormUrlEncoded
    @POST("-/team/remove")
    suspend fun removeTeamMember(
        @Field("id") id: String,
    ): Response<ApiResponse<OkResponse>>

    /** Update a staff team member's role. */
    @FormUrlEncoded
    @POST("-/team/role")
    suspend fun setTeamRole(
        @Field("id") id: String,
        @Field("role") role: String,
    ): Response<ApiResponse<StaffMember>>

    // ---- Directory ----

    /**
     * Search the directory for people (proxied by `action_directory_search` in
     * staff.star).
     */
    @GET("-/directory/search")
    suspend fun searchDirectory(
        @Query("search") search: String,
    ): Response<ApiResponse<DirectorySearchResponse>>

    // ---- Accounts ----

    /**
     * `status` is `active`/`suspended`/`banned`; `seller` limits to onboarded
     * sellers; `query` substring-matches biography, location and name.
     */
    @GET("-/accounts/list")
    suspend fun listAccounts(
        @Query("status") status: String? = null,
        @Query("seller") seller: String? = null,
        @Query("query") query: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<AccountsListResponse>>

    /** Suspend an account (temporary; reversible via [unsuspendAccount]). */
    @FormUrlEncoded
    @POST("-/accounts/suspend")
    suspend fun suspendAccount(
        @Field("id") id: String,
        @Field("reason") reason: String? = null,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<AccountSummary>>

    /** Lift a suspension. */
    @FormUrlEncoded
    @POST("-/accounts/unsuspend")
    suspend fun unsuspendAccount(
        @Field("id") id: String,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<AccountSummary>>

    /** Ban an account (permanent until an admin lifts via [unbanAccount]). */
    @FormUrlEncoded
    @POST("-/accounts/ban")
    suspend fun banAccount(
        @Field("id") id: String,
        @Field("reason") reason: String? = null,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<AccountSummary>>

    /** Lift a ban. */
    @FormUrlEncoded
    @POST("-/accounts/unban")
    suspend fun unbanAccount(
        @Field("id") id: String,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<AccountSummary>>

    // ---- Categories ----

    /**
     * All marketplace categories (staff sees inactive ones too, unlike
     * the public `/market/-/categories/list`).
     */
    @GET("-/categories/list")
    suspend fun listCategories(): Response<ApiResponse<List<Category>>>

    /**
     * `digital` and `physical` are 0/1 on the wire; omit `parent` for a
     * top-level category.
     */
    @FormUrlEncoded
    @POST("-/categories/create")
    suspend fun createCategory(
        @Field("name") name: String,
        @Field("slug") slug: String,
        @Field("parent") parent: String? = null,
        @Field("icon") icon: String? = null,
        @Field("position") position: Int? = null,
        @Field("digital") digital: Int? = null,
        @Field("physical") physical: Int? = null,
    ): Response<ApiResponse<Category>>

    /**
     * `digital`, `physical`, `active` are 0/1 on the wire; only the fields sent
     * are changed.
     */
    @FormUrlEncoded
    @POST("-/categories/update")
    suspend fun updateCategory(
        @Field("id") id: String,
        @Field("name") name: String? = null,
        @Field("slug") slug: String? = null,
        @Field("parent") parent: String? = null,
        @Field("icon") icon: String? = null,
        @Field("position") position: Int? = null,
        @Field("digital") digital: Int? = null,
        @Field("physical") physical: Int? = null,
        @Field("active") active: Int? = null,
    ): Response<ApiResponse<Category>>

    /** Delete a category by id. */
    @FormUrlEncoded
    @POST("-/categories/delete")
    suspend fun deleteCategory(
        @Field("id") id: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Listings moderation ----

    /**
     * Listings awaiting moderation; `status` and `moderation` take the wire
     * strings in [ListingStatus] and [ModerationState], `query`
     * substring-matches the title.
     */
    @GET("-/listings/pending")
    suspend fun listPendingListings(
        @Query("status") status: String? = null,
        @Query("moderation") moderation: String? = null,
        @Query("query") query: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<PendingListingsResponse>>

    /** Approve a pending listing. */
    @FormUrlEncoded
    @POST("-/listings/approve")
    suspend fun approveListing(
        @Field("id") id: String,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<OkResponse>>

    /** Reject a pending listing. */
    @FormUrlEncoded
    @POST("-/listings/reject")
    suspend fun rejectListing(
        @Field("id") id: String,
        @Field("reason") reason: String? = null,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<OkResponse>>

    /** Take down an already-published listing. */
    @FormUrlEncoded
    @POST("-/listings/remove")
    suspend fun removeListing(
        @Field("id") id: String,
        @Field("reason") reason: String? = null,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<OkResponse>>

    // ---- Moderation ----

    /** Moderation log, optionally filtered to a single listing id. */
    @GET("-/moderation/log")
    suspend fun getModerationLog(
        @Query("listing") listing: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ModerationLogResponse>>

    @GET("-/moderation/thresholds")
    suspend fun getModerationThresholds(): Response<ApiResponse<Thresholds>>

    @FormUrlEncoded
    @POST("-/moderation/set_thresholds")
    suspend fun setModerationThresholds(
        @Field("low") low: Int? = null,
        @Field("high") high: Int? = null,
    ): Response<ApiResponse<Thresholds>>

    // ---- Reports ----

    /** List user reports (listings / accounts / messages). */
    @GET("-/reports/list")
    suspend fun listReports(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ReportsListResponse>>

    /**
     * `action` is one of [ReportAction]'s wire values; the Comptroller
     * validates it.
     */
    @FormUrlEncoded
    @POST("-/reports/action")
    suspend fun actionReport(
        @Field("id") id: String,
        @Field("action") action: String,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<Report>>

    // ---- Disputes ----

    /** List buyer-seller order disputes. */
    @GET("-/disputes/list")
    suspend fun listDisputes(
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<DisputesListResponse>>

    @FormUrlEncoded
    @POST("-/disputes/review")
    suspend fun reviewDispute(
        // staff.star forwards ["id", "status", "resolution", "amount"].
        @Field("id") id: String,
        // status is the OUTCOME — "resolved_buyer" or "resolved_seller" (required).
        @Field("status") status: String,
        // resolution is the free-text writeup (optional, <=5000 chars).
        @Field("resolution") resolution: String? = null,
        // amount is the optional partial refund (minor units) for resolved_buyer.
        @Field("amount") refundAmount: String? = null,
    ): Response<ApiResponse<Dispute>>

    // ---- Metrics ----

    /** Marketplace overview metrics (totals, GMV, fees, active sellers). */
    @GET("-/metrics/overview")
    suspend fun getMetricsOverview(): Response<ApiResponse<MetricsOverview>>

    /**
     * The offset must be sent as `page` - staff.star forwards only `tab`,
     * `page`, `limit`.
     */
    @GET("-/metrics/activity")
    suspend fun getMetricsActivity(
        @Query("tab") tab: String? = null,
        @Query("page") skip: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ActivityData>>

    // ---- Audit ----

    /**
     * `since`/`until` are unix seconds; `dedupe=1` collapses identical
     * consecutive rows.
     */
    @GET("-/audit/list")
    suspend fun listAudit(
        @Query("app") app: String? = null,
        @Query("kind") kind: String? = null,
        @Query("action") action: String? = null,
        @Query("actor") actor: String? = null,
        @Query("since") since: Long? = null,
        @Query("until") until: Long? = null,
        @Query("dedupe") dedupe: Int? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<AuditListResponse>>

    @GET("-/audit/object")
    suspend fun getObjectAudit(
        @Query("kind") kind: String,
        @Query("object") `object`: String,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<AuditListResponse>>

    // ---- Config ----

    /** Get marketplace configuration (list of key/value entries). */
    @GET("-/config/get")
    suspend fun getConfig(): Response<ApiResponse<List<ConfigEntry>>>

    /** Set a marketplace configuration value. */
    @FormUrlEncoded
    @POST("-/config/set")
    suspend fun setConfig(
        @Field("key") key: String,
        @Field("value") value: String,
    ): Response<ApiResponse<OkResponse>>

    // ---- Appeals ----

    /** List pending listing appeals. */
    @GET("-/appeals/list")
    suspend fun listAppeals(): Response<ApiResponse<AppealsListResponse>>

    /**
     * `decision` is `upheld` or `denied`. The listing uid is sent as `id` -
     * staff.star forwards only `id`, `decision`, `notes`.
     */
    @FormUrlEncoded
    @POST("-/appeals/decide")
    suspend fun decideAppeal(
        @Field("id") listingId: String,
        @Field("decision") decision: String,
        @Field("notes") notes: String? = null,
    ): Response<ApiResponse<OkResponse>>

    // ---- Reviews ----

    /** List user-submitted reviews awaiting moderation. */
    @GET("-/reviews/list")
    suspend fun listReviews(
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ReviewsListResponse>>

    /**
     * Take action on a review (`remove` / `restore`; staff.star forwards
     * the action verbatim and the Comptroller validates it).
     */
    @FormUrlEncoded
    @POST("-/reviews/action")
    suspend fun actionReview(
        @Field("id") id: String,
        @Field("action") action: String,
    ): Response<ApiResponse<Review>>
}
