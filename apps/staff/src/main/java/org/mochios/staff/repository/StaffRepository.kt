// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.repository

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.android.api.unwrapEmpty
import org.mochios.staff.api.StaffApi
import org.mochios.staff.model.AccountSummary
import org.mochios.staff.model.AccountsListResponse
import org.mochios.staff.model.ActivityData
import org.mochios.staff.model.AppealsListResponse
import org.mochios.staff.model.AuditListResponse
import org.mochios.staff.model.Category
import org.mochios.staff.model.ConfigEntry
import org.mochios.staff.model.ConversationMessage
import org.mochios.staff.model.DirectorySearchResult
import org.mochios.staff.model.Dispute
import org.mochios.staff.model.DisputesListResponse
import org.mochios.staff.model.ListingView
import org.mochios.staff.model.Me
import org.mochios.staff.model.MetricsOverview
import org.mochios.staff.model.ModerationLogResponse
import org.mochios.staff.model.PendingListingsResponse
import org.mochios.staff.model.Report
import org.mochios.staff.model.ReportsListResponse
import org.mochios.staff.model.Review
import org.mochios.staff.model.ReviewsListResponse
import org.mochios.staff.model.StaffMember
import org.mochios.staff.model.Thresholds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [StaffApi] like [org.mochios.market.repository.MarketRepository]:
 * unwrap the response, rethrow as [org.mochios.android.api.MochiError]. Row
 * ids are opaque Comptroller uids forwarded verbatim; [getMe] succeeds for any
 * authenticated identity, everything else is 403 for non-staff.
 */
@Singleton
class StaffRepository @Inject constructor(
    private val api: StaffApi,
) {

    /**
     * [block]'s answer, a failure rethrown as a MochiError. A cancellation
     * passes through as it is: turned into an error, a read its caller
     * replaced would report a failure that did not happen.
     */
    private suspend inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toMochiError()
    }

    // ---- Me ----

    /** The last [getMe] answer and when it was read, on the [SystemClock.elapsedRealtime] clock. */
    private class HeldMe(val me: Me, val read: Long)

    @Volatile
    private var held: HeldMe? = null

    /** One [getMe] read at a time: a caller arriving while one is under way takes its answer. */
    private val meLock = Mutex()

    /**
     * The signed-in identity's staff record. An answer read within [ME_LIFETIME]
     * is reused, so the access check at start and the staff screens that open
     * after it make one request between them. [fresh] reads it again regardless.
     */
    suspend fun getMe(fresh: Boolean = false): Me = meLock.withLock {
        val last = held
        if (!fresh && last != null && SystemClock.elapsedRealtime() - last.read < ME_LIFETIME) {
            return@withLock last.me
        }
        val me = try {
            api.getMe().unwrap()
        } catch (e: CancellationException) {
            held = null
            throw e
        } catch (e: Exception) {
            held = null
            throw e.toMochiError()
        }
        held = HeldMe(me, SystemClock.elapsedRealtime())
        me
    }

    /** Drops the held [getMe] answer, for an identity that changed. */
    fun forgetMe() {
        held = null
    }

    // ---- Team ----

    suspend fun listTeam(): List<StaffMember> = call { api.listTeam().unwrap() }

    suspend fun addTeamMember(id: String, role: String): StaffMember = call { api.addTeamMember(id, role).unwrap() }

    suspend fun removeTeamMember(id: String) = call { api.removeTeamMember(id).unwrapEmpty() }

    suspend fun setTeamRole(id: String, role: String): StaffMember = call { api.setTeamRole(id, role).unwrap() }

    // ---- Directory ----

    suspend fun searchDirectory(query: String): List<DirectorySearchResult> =
        call { api.searchDirectory(query).unwrap().results }

    // ---- Accounts ----

    suspend fun listAccounts(
        status: String? = null,
        seller: String? = null,
        query: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): AccountsListResponse = call { api.listAccounts(status, seller, query, page, limit, after).unwrap() }

    suspend fun suspendAccount(id: String, reason: String? = null, notes: String? = null): AccountSummary =
        call { api.suspendAccount(id, reason, notes).unwrap() }

    suspend fun unsuspendAccount(id: String, notes: String? = null): AccountSummary =
        call { api.unsuspendAccount(id, notes).unwrap() }

    suspend fun banAccount(id: String, reason: String? = null, notes: String? = null): AccountSummary =
        call { api.banAccount(id, reason, notes).unwrap() }

    suspend fun unbanAccount(id: String, notes: String? = null): AccountSummary =
        call { api.unbanAccount(id, notes).unwrap() }

    // ---- Categories ----

    suspend fun listCategories(): List<Category> = call { api.listCategories().unwrap() }

    suspend fun createCategory(
        name: String,
        slug: String,
        parent: String? = null,
        icon: String? = null,
        position: Int? = null,
        digital: Boolean? = null,
        physical: Boolean? = null,
    ): Category = call {
        api.createCategory(
            name = name,
            slug = slug,
            parent = parent,
            icon = icon,
            position = position,
            digital = digital?.let { if (it) 1 else 0 },
            physical = physical?.let { if (it) 1 else 0 },
        ).unwrap()
    }

    suspend fun updateCategory(
        id: String,
        name: String? = null,
        slug: String? = null,
        parent: String? = null,
        icon: String? = null,
        position: Int? = null,
        digital: Boolean? = null,
        physical: Boolean? = null,
        active: Boolean? = null,
    ): Category = call {
        api.updateCategory(
            id = id,
            name = name,
            slug = slug,
            parent = parent,
            icon = icon,
            position = position,
            digital = digital?.let { if (it) 1 else 0 },
            physical = physical?.let { if (it) 1 else 0 },
            active = active?.let { if (it) 1 else 0 },
        ).unwrap()
    }

    suspend fun deleteCategory(id: String) = call { api.deleteCategory(id).unwrapEmpty() }

    // ---- Listings moderation ----

    suspend fun listPendingListings(
        status: String? = null,
        moderation: String? = null,
        query: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): PendingListingsResponse = call { api.listPendingListings(status, moderation, query, page, limit, after).unwrap() }

    suspend fun getListing(id: String): ListingView = call { api.getListing(id).unwrap() }

    suspend fun approveListing(id: String, notes: String? = null) {
        call { api.approveListing(id, notes).unwrap() }
    }

    suspend fun rejectListing(id: String, reason: String? = null, notes: String? = null) {
        call { api.rejectListing(id, reason, notes).unwrap() }
    }

    suspend fun removeListing(id: String, reason: String? = null, notes: String? = null) {
        call { api.removeListing(id, reason, notes).unwrap() }
    }

    // ---- Moderation ----

    suspend fun getModerationLog(
        listing: String? = null,
        query: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): ModerationLogResponse = call { api.getModerationLog(listing, query, page, limit, after).unwrap() }

    suspend fun getModerationThresholds(): Thresholds = call { api.getModerationThresholds().unwrap() }

    suspend fun setModerationThresholds(low: Int? = null, high: Int? = null): Thresholds =
        call { api.setModerationThresholds(low, high).unwrap() }

    // ---- Reports ----

    suspend fun listReports(
        type: String? = null,
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): ReportsListResponse = call { api.listReports(type, status, page, limit, after).unwrap() }

    suspend fun actionReport(id: String, action: String, notes: String? = null): Report =
        call { api.actionReport(id, action, notes).unwrap() }

    suspend fun getConversation(thread: String): List<ConversationMessage> =
        call { api.getConversation(thread).unwrap().messages }

    // ---- Disputes ----

    suspend fun listDisputes(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): DisputesListResponse = call { api.listDisputes(status, page, limit, after).unwrap() }

    suspend fun reviewDispute(
        id: String,
        status: String,
        resolution: String? = null,
        refundAmount: Long? = null,
    ): Dispute = call { api.reviewDispute(id, status, resolution, refundAmount?.toString()).unwrap() }

    // ---- Metrics ----

    suspend fun getMetricsOverview(): MetricsOverview = call { api.getMetricsOverview().unwrap() }

    suspend fun getMetricsActivity(
        tab: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): ActivityData = call { api.getMetricsActivity(tab, page, limit, after).unwrap() }

    // ---- Audit ----

    suspend fun listAudit(
        app: String? = null,
        kind: String? = null,
        action: String? = null,
        actor: String? = null,
        since: Long? = null,
        until: Long? = null,
        dedupe: Int? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): AuditListResponse = call { api.listAudit(app, kind, action, actor, since, until, dedupe, page, limit, after).unwrap() }

    suspend fun getObjectAudit(
        kind: String,
        objectId: String,
        limit: Int? = null,
        after: String? = null,
    ): AuditListResponse = call { api.getObjectAudit(kind, objectId, limit, after).unwrap() }

    // ---- Config ----

    suspend fun getConfig(): List<ConfigEntry> = call { api.getConfig().unwrap() }

    suspend fun setConfig(key: String, value: String) = call { api.setConfig(key, value).unwrapEmpty() }

    // ---- Appeals ----

    suspend fun listAppeals(page: Int? = null, limit: Int? = null, after: String? = null): AppealsListResponse =
        call { api.listAppeals(page, limit, after).unwrap() }

    suspend fun decideAppeal(listingId: String, decision: String, notes: String? = null) {
        call { api.decideAppeal(listingId, decision, notes).unwrap() }
    }

    // ---- Reviews ----

    suspend fun listReviews(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
        after: String? = null,
    ): ReviewsListResponse = call { api.listReviews(status, page, limit, after).unwrap() }

    suspend fun actionReview(id: String, action: String): Review = call { api.actionReview(id, action).unwrap() }

    private companion object {
        /** How long a [getMe] answer is reused, in milliseconds. */
        const val ME_LIFETIME = 60_000L
    }
}
