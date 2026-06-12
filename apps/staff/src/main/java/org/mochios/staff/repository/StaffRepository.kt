package org.mochios.staff.repository

import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrap
import org.mochios.staff.api.StaffApi
import org.mochios.staff.model.Account
import org.mochios.staff.model.AccountSummary
import org.mochios.staff.model.ActivityData
import org.mochios.staff.model.AppealsListResponse
import org.mochios.staff.model.AuditListResponse
import org.mochios.staff.model.Category
import org.mochios.staff.model.ConfigEntry
import org.mochios.staff.model.Dispute
import org.mochios.staff.model.DisputesListResponse
import org.mochios.staff.model.DirectorySearchResult
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
 * Thin wrapper around [StaffApi]. Mirrors the structure of
 * [org.mochios.market.repository.MarketRepository]: every method calls
 * `.unwrap()` on the Retrofit response and re-throws any exception as a
 * typed [org.mochios.android.api.MochiError] via [toMochiError] so
 * ViewModels can render localised messages.
 *
 * The staff app is a stateless proxy to the Comptroller — every action is
 * a class-context route (`-/<group>/<verb>`) with no entity scope. The
 * Comptroller enforces the staff-role gate (admin / moderator / support);
 * a non-staff caller gets 403 on most endpoints, with [getMe] always
 * succeeding so the layout can decide which admin-only items to surface.
 *
 * Numeric IDs (listings, categories, reports, disputes, reviews, appeals)
 * are typed as [Int] on the Retrofit side to match the staff Comptroller
 * (`event_staff_*` handlers parse the wire-decimal ids back to integers)
 * — the repository's `Long` parameters serialise via `.toInt()`.
 */
@Singleton
class StaffRepository @Inject constructor(
    private val api: StaffApi,
) {

    // ---- Me ----

    suspend fun getMe(): Me {
        return try {
            api.getMe().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Team ----

    suspend fun listTeam(): List<StaffMember> {
        return try {
            api.listTeam().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun addTeamMember(id: String, role: String): StaffMember {
        return try {
            api.addTeamMember(id, role).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun removeTeamMember(id: String) {
        try {
            api.removeTeamMember(id).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setTeamRole(id: String, role: String): StaffMember {
        return try {
            api.setTeamRole(id, role).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Directory ----

    suspend fun searchDirectory(query: String): List<DirectorySearchResult> {
        return try {
            api.searchDirectory(query).unwrap().results
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Accounts ----

    suspend fun listAccounts(
        status: String? = null,
        seller: String? = null,
        query: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ) = try {
        api.listAccounts(status, seller, query, page, limit).unwrap()
    } catch (e: Exception) {
        throw e.toMochiError()
    }

    suspend fun suspendAccount(id: String, reason: String? = null, notes: String? = null): AccountSummary {
        return try {
            api.suspendAccount(id, reason, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun unsuspendAccount(id: String, notes: String? = null): AccountSummary {
        return try {
            api.unsuspendAccount(id, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun banAccount(id: String, reason: String? = null, notes: String? = null): AccountSummary {
        return try {
            api.banAccount(id, reason, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun unbanAccount(id: String, notes: String? = null): AccountSummary {
        return try {
            api.unbanAccount(id, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Categories ----

    suspend fun listCategories(): List<Category> {
        return try {
            api.listCategories().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun createCategory(
        name: String,
        slug: String,
        parent: Int? = null,
        icon: String? = null,
        position: Int? = null,
        digital: Boolean? = null,
        physical: Boolean? = null,
    ): Category {
        return try {
            api.createCategory(
                name = name,
                slug = slug,
                parent = parent,
                icon = icon,
                position = position,
                digital = digital?.let { if (it) 1 else 0 },
                physical = physical?.let { if (it) 1 else 0 },
            ).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun updateCategory(
        id: Int,
        name: String? = null,
        slug: String? = null,
        parent: Int? = null,
        icon: String? = null,
        position: Int? = null,
        digital: Boolean? = null,
        physical: Boolean? = null,
        active: Boolean? = null,
    ): Category {
        return try {
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
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun deleteCategory(id: Int) {
        try {
            api.deleteCategory(id).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Listings moderation ----

    suspend fun listPendingListings(
        status: String? = null,
        moderation: String? = null,
        query: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): PendingListingsResponse {
        return try {
            api.listPendingListings(status, moderation, query, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun approveListing(id: Int, notes: String? = null) {
        try {
            api.approveListing(id, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun rejectListing(id: Int, reason: String? = null, notes: String? = null) {
        try {
            api.rejectListing(id, reason, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun removeListing(id: Int, reason: String? = null, notes: String? = null) {
        try {
            api.removeListing(id, reason, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Moderation ----

    suspend fun getModerationLog(
        listingId: Int? = null,
        page: Int? = null,
        limit: Int? = null,
    ): ModerationLogResponse {
        return try {
            api.getModerationLog(listingId, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getModerationThresholds(): Thresholds {
        return try {
            api.getModerationThresholds().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setModerationThresholds(low: Int? = null, high: Int? = null): Thresholds {
        return try {
            api.setModerationThresholds(low, high).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Reports ----

    suspend fun listReports(
        type: String? = null,
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): ReportsListResponse {
        return try {
            api.listReports(type, status, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun actionReport(id: Int, action: String, notes: String? = null): Report {
        return try {
            api.actionReport(id, action, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Disputes ----

    suspend fun listDisputes(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): DisputesListResponse {
        return try {
            api.listDisputes(status, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun reviewDispute(
        id: Int,
        status: String,
        resolution: String? = null,
        refundAmount: Long? = null,
    ): Dispute {
        return try {
            api.reviewDispute(id, status, resolution, refundAmount?.toString()).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Metrics ----

    suspend fun getMetricsOverview(): MetricsOverview {
        return try {
            api.getMetricsOverview().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getMetricsActivity(
        tab: String? = null,
        skip: Int? = null,
        limit: Int? = null,
    ): ActivityData {
        return try {
            api.getMetricsActivity(tab, skip, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

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
    ): AuditListResponse {
        return try {
            api.listAudit(app, kind, action, actor, since, until, dedupe, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun getObjectAudit(
        kind: String,
        objectId: String,
        limit: Int? = null,
    ): AuditListResponse {
        return try {
            api.getObjectAudit(kind, objectId, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Config ----

    suspend fun getConfig(): List<ConfigEntry> {
        return try {
            api.getConfig().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun setConfig(key: String, value: String) {
        try {
            api.setConfig(key, value).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Appeals ----

    suspend fun listAppeals(): AppealsListResponse {
        return try {
            api.listAppeals().unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun decideAppeal(listingId: Int, decision: String, notes: String? = null) {
        try {
            api.decideAppeal(listingId, decision, notes).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    // ---- Reviews ----

    suspend fun listReviews(
        status: String? = null,
        page: Int? = null,
        limit: Int? = null,
    ): ReviewsListResponse {
        return try {
            api.listReviews(status, page, limit).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }

    suspend fun actionReview(id: Int, action: String): Review {
        return try {
            api.actionReview(id, action).unwrap()
        } catch (e: Exception) {
            throw e.toMochiError()
        }
    }
}
