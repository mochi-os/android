// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.staff.model.ActivityListing
import org.mochios.staff.model.ActivityOrder
import org.mochios.staff.model.ActivitySignup
import org.mochios.staff.model.AuditEntry
import org.mochios.staff.model.MetricsOverview
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * Valid `tab` query-string values for the activity feed. Mirrors the
 * `id` union in `useActivityTabs()` on the web (orders / listings /
 * signups / moderation / audit).
 */
object DashboardTab {
    const val ORDERS = "orders"
    const val LISTINGS = "listings"
    const val SIGNUPS = "signups"
    const val MODERATION = "moderation"
    const val AUDIT = "audit"

    /** Returns [ORDERS] for blank / unknown inputs (matches web fallback). */
    fun normalise(raw: String?): String = when (raw) {
        ORDERS, LISTINGS, SIGNUPS, MODERATION, AUDIT -> raw
        else -> ORDERS
    }
}

/**
 * Aggregate UI state for the staff dashboard screen.
 *
 * Per-tab item lists are kept in separate maps so switching tabs is free
 * once each tab's first page has loaded — matching the web behaviour where
 * navigating between tabs preserves scroll position and pagination cursor.
 *
 *  - [overview] is the marketplace KPI snapshot (top section).
 *  - [currentTab] is one of [DashboardTab] (defaults to `orders`).
 *  - [perTabOrders] / [perTabListings] / … hold the loaded rows.
 *  - [perTabTotal] holds the server-reported total per tab so the load-more
 *    sentinel knows when to stop. Missing key ⇒ tab not yet loaded.
 *  - [error] surfaces the first load failure to the screen as an inline
 *    error banner; subsequent successful loads clear it.
 */
data class DashboardUiState(
    val overviewLoading: Boolean = true,
    val overview: MetricsOverview? = null,
    val currentTab: String = DashboardTab.ORDERS,
    val perTabOrders: List<ActivityOrder> = emptyList(),
    val perTabListings: List<ActivityListing> = emptyList(),
    val perTabSignups: List<ActivitySignup> = emptyList(),
    val perTabModeration: List<ModerationEntry> = emptyList(),
    val perTabAudit: List<AuditEntry> = emptyList(),
    val perTabTotal: Map<String, Long> = emptyMap(),
    val perTabLoading: Map<String, Boolean> = emptyMap(),
    val perTabLoaded: Set<String> = emptySet(),
    val error: MochiError? = null,
) {
    /**
     * Number of rows currently held for [tab]. Drives the `skip` parameter
     * on follow-up page fetches and the load-more enable predicate.
     */
    fun rowCount(tab: String): Int = when (tab) {
        DashboardTab.ORDERS -> perTabOrders.size
        DashboardTab.LISTINGS -> perTabListings.size
        DashboardTab.SIGNUPS -> perTabSignups.size
        DashboardTab.MODERATION -> perTabModeration.size
        DashboardTab.AUDIT -> perTabAudit.size
        else -> 0
    }

    /** True iff the server claims more rows are available for [tab]. */
    fun hasMore(tab: String): Boolean {
        val loaded = rowCount(tab)
        val total = perTabTotal[tab] ?: return !perTabLoaded.contains(tab)
        return loaded.toLong() < total
    }

    /** True iff a fetch is in flight for [tab] right now. */
    fun isLoading(tab: String): Boolean = perTabLoading[tab] == true
}

private const val PAGE_SIZE = 20

/**
 * Backs [DashboardScreen]. Owns one [MetricsOverview] fetch plus a
 * paginated activity feed per tab; each tab caches its own row buffer so
 * tab switches are instant once loaded.
 *
 * URL state: the `tab` query parameter is round-tripped via the
 * SavedStateHandle so deep links and process-death restoration land on the
 * same tab.
 *
 * The repository's `getMetricsActivity(tab, skip, limit)` returns the
 * combined [org.mochios.staff.model.ActivityData] payload but only the
 * requested tab's slot is populated. For the `audit` tab the server has
 * no first-class entry on `metrics/activity`; the screen falls back to
 * the dedicated `audit/list` endpoint in [loadAuditPage].
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DashboardUiState(
            currentTab = DashboardTab.normalise(handle.get<String>("tab")),
        ),
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val tabLoadJobs: MutableMap<String, Job> = mutableMapOf()
    private var overviewJob: Job? = null

    init {
        loadOverview()
        loadFirstPage(_state.value.currentTab)
    }

    /**
     * Switch to [tab]. Persists the choice in the SavedStateHandle so it
     * survives process death, and kicks off the first page load when this
     * tab hasn't been visited yet.
     */
    fun setTab(tab: String) {
        val normalised = DashboardTab.normalise(tab)
        if (normalised == _state.value.currentTab) return
        _state.value = _state.value.copy(currentTab = normalised)
        handle["tab"] = normalised
        if (!_state.value.perTabLoaded.contains(normalised)) {
            loadFirstPage(normalised)
        }
    }

    /**
     * Fetch the next page for the current tab. No-op when a fetch is
     * already in flight or the server has reported the tab as exhausted.
     */
    fun loadMore() {
        val tab = _state.value.currentTab
        val s = _state.value
        if (s.isLoading(tab) || !s.hasMore(tab)) return
        loadPage(tab, skip = s.rowCount(tab), replace = false)
    }

    private fun loadOverview() {
        overviewJob?.cancel()
        overviewJob = viewModelScope.launch {
            _state.value = _state.value.copy(overviewLoading = true)
            try {
                val o = repository.getMetricsOverview()
                _state.value = _state.value.copy(
                    overviewLoading = false,
                    overview = o,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    overviewLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    private fun loadFirstPage(tab: String) {
        loadPage(tab, skip = 0, replace = true)
    }

    private fun loadPage(tab: String, skip: Int, replace: Boolean) {
        tabLoadJobs[tab]?.cancel()
        tabLoadJobs[tab] = viewModelScope.launch {
            markLoading(tab, true)
            try {
                if (tab == DashboardTab.AUDIT) {
                    loadAuditPage(skip = skip, replace = replace)
                } else {
                    loadActivityPage(tab = tab, skip = skip, replace = replace)
                }
                _state.value = _state.value.copy(
                    perTabLoaded = _state.value.perTabLoaded + tab,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.toMochiError())
            } finally {
                markLoading(tab, false)
            }
        }
    }

    private suspend fun loadActivityPage(tab: String, skip: Int, replace: Boolean) {
        val data = repository.getMetricsActivity(tab = tab, skip = skip, limit = PAGE_SIZE)
        val s = _state.value
        val next = when (tab) {
            DashboardTab.ORDERS -> {
                val rows = data.orders.orEmpty()
                val merged = if (replace) rows else s.perTabOrders + rows
                s.copy(
                    perTabOrders = merged,
                    perTabTotal = s.perTabTotal + (tab to (data.ordersTotal ?: merged.size.toLong())),
                )
            }
            DashboardTab.LISTINGS -> {
                val rows = data.listings.orEmpty()
                val merged = if (replace) rows else s.perTabListings + rows
                s.copy(
                    perTabListings = merged,
                    perTabTotal = s.perTabTotal + (tab to (data.listingsTotal ?: merged.size.toLong())),
                )
            }
            DashboardTab.SIGNUPS -> {
                val rows = data.signups.orEmpty()
                val merged = if (replace) rows else s.perTabSignups + rows
                s.copy(
                    perTabSignups = merged,
                    perTabTotal = s.perTabTotal + (tab to (data.signupsTotal ?: merged.size.toLong())),
                )
            }
            DashboardTab.MODERATION -> {
                val rows = data.moderation.orEmpty()
                val merged = if (replace) rows else s.perTabModeration + rows
                s.copy(
                    perTabModeration = merged,
                    perTabTotal = s.perTabTotal + (tab to (data.moderationTotal ?: merged.size.toLong())),
                )
            }
            else -> s
        }
        _state.value = next
    }

    private suspend fun loadAuditPage(skip: Int, replace: Boolean) {
        // Audit pagination is `page` / `limit` server-side; convert the
        // common `skip` cursor into the equivalent page number.
        val page = (skip / PAGE_SIZE) + 1
        val r = repository.listAudit(page = page, limit = PAGE_SIZE)
        val s = _state.value
        val merged = if (replace) r.audit else s.perTabAudit + r.audit
        _state.value = s.copy(
            perTabAudit = merged,
            perTabTotal = s.perTabTotal + (DashboardTab.AUDIT to r.total),
        )
    }

    private fun markLoading(tab: String, loading: Boolean) {
        val current = _state.value.perTabLoading
        _state.value = _state.value.copy(perTabLoading = current + (tab to loading))
    }
}
