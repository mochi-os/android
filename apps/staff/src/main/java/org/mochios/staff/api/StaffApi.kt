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
 * Retrofit interface for the staff app.
 *
 * The staff app is a stateless proxy in front of the Comptroller
 * marketplace backend — `apps/staff/staff.star` opens a P2P stream to
 * the Comptroller for every action and forwards the form / query inputs
 * verbatim. This interface mirrors the action paths declared in
 * `apps/staff/app.json`'s `actions` block (the `-/...` entries) so the
 * Android client speaks the same wire format as the staff SPA.
 *
 * URL composition: this interface is bound to a per-app Retrofit
 * (baseUrl `<server>/staff/`) built in `di/AppModule.kt`, so every path
 * in this file is relative to the staff namespace. Auth attaches the
 * staff-scoped JWT via a Bearer interceptor in that module — every
 * endpoint here requires an authenticated session that resolves to a
 * staff role server-side (admin / moderator / support); the Comptroller
 * itself enforces the per-role gates and returns 403 on shortfall.
 *
 * Encoding conventions:
 *   - GET endpoints are used for pure read paths (`me`, list / log /
 *     overview / thresholds-get / config-get). Filters land on the
 *     query string via `@Query`.
 *   - POST endpoints are used for mutations and command-style actions
 *     (suspend, ban, approve, reject, decide, ...). Inputs go on the
 *     wire as `application/x-www-form-urlencoded` (`@FormUrlEncoded` +
 *     `@Field`); the Starlark proxy reads them via `a.input(...)` which
 *     accepts both form-encoded and JSON bodies indistinguishably.
 *
 * Omissions: the static-asset routes declared in app.json are resolved
 * by Android via plain URL strings (built from the per-app Retrofit
 * baseUrl) rather than via Retrofit calls, so they are not modelled
 * here:
 *   - `""` (SPA mount point)
 *   - `"assets"` (built SPA bundle)
 *   - `"images"` (icon set)
 *   - `"-/user/:user/asset/:asset"` (person-entity asset proxy —
 *     avatar / banner / favicon / style / information, fetched directly
 *     as URLs by Coil/Glide)
 *
 * The P2P event `message_notify` in staff.star is a notification stream
 * coming the other way (Comptroller -> staff), not an HTTP action, so
 * it is also not part of this interface.
 */
interface StaffApi {

    // ---- Me ----

    /**
     * Caller's own staff record (id + role). Used by the layout to
     * decide which admin-only items to surface. Returns role="" for
     * non-staff identities.
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
     * Search the local directory for people. Used by the team add
     * picker. Sandboxed apps can't call `/people/-/users/search`
     * directly (their JWT is scoped to staff), so staff exposes a
     * same-app proxy that delegates to `mochi.directory.*`.
     */
    @GET("-/directory/search")
    suspend fun searchDirectory(
        @Query("search") search: String,
    ): Response<ApiResponse<DirectorySearchResponse>>

    // ---- Accounts ----

    /**
     * List marketplace accounts with optional filters: `status`
     * (`active`/`suspended`/`banned`), `seller` (only Stripe-onboarded
     * sellers), `query` (substring match on biography / location /
     * resolved name).
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
     * Create a category. `digital` and `physical` are wire-level 0/1
     * ints — pass `1` for true, `0` for false. `parent` is the uid
     * of the parent category (omit for a top-level category).
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
     * Update a category. `digital`, `physical`, `active` are wire-level
     * 0/1 ints. All non-id fields are optional — only the provided
     * fields are touched server-side.
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
     * Listings awaiting moderator action. Filters: `status`
     * (`draft`/`pending`/`active`/`...`), `moderation`
     * (`pending`/`auto_approved`/`held`/`approved`/`rejected`/`appealed`),
     * `query` (substring on title).
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

    /**
     * Get current moderation score thresholds — listings scoring below
     * `low` are auto-approved, listings scoring above `high` are held
     * for review.
     */
    @GET("-/moderation/thresholds")
    suspend fun getModerationThresholds(): Response<ApiResponse<Thresholds>>

    /**
     * Set moderation score thresholds. Wire field names are `low` and
     * `high` (the Comptroller scoring is monotone: low score = safe,
     * high score = risky).
     */
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
     * Take action on a report (`dismiss` / `warn` / `remove` /
     * `suspend` / `ban`; staff.star forwards the action verbatim and
     * the Comptroller validates it).
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

    /**
     * Review a dispute. `resolution` is the resolution code; `notes`
     * carries the staff-visible writeup; `refund_amount` is the
     * (optional) partial-refund amount in minor currency units — the
     * Comptroller forwards it as `amount` to its dispute engine.
     */
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
     * Marketplace activity log feed. `tab` selects the activity view
     * (`listings` / `orders` / `disputes` / `reports`); pagination is
     * `page` / `limit` — staff.star forwards `["tab", "page", "limit"]`, so
     * the offset field must be `page` (the handler ignores anything else).
     */
    @GET("-/metrics/activity")
    suspend fun getMetricsActivity(
        @Query("tab") tab: String? = null,
        @Query("page") skip: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ApiResponse<ActivityData>>

    // ---- Audit ----

    /**
     * Filterable audit feed across apps. `app` / `kind` / `action`
     * scope the row class; `actor` filters by acting entity; `since` /
     * `until` are unix seconds; `dedupe` collapses identical
     * consecutive rows when set to `1`.
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

    /**
     * Per-object audit timeline. Used for the per-listing /
     * per-order / per-dispute history drawers.
     */
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
     * Decide on a listing appeal. `decision` is `approve` /
     * `reject` / `escalate` (the Comptroller validates). The field is sent as
     * `id` (the listing uid the appeal is filed against) — staff.star
     * forwards `["id", "decision", "notes"]` verbatim, so it must be `id`, not
     * `listing_id` (which the handler would silently drop).
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
     * Take action on a review (`approve` / `hide` / `remove`;
     * staff.star forwards the action verbatim and the Comptroller
     * validates it).
     */
    @FormUrlEncoded
    @POST("-/reviews/action")
    suspend fun actionReview(
        @Field("id") id: String,
        @Field("action") action: String,
    ): Response<ApiResponse<Review>>
}
