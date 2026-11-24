// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.staff.model.ActivityListing
import org.mochios.staff.model.ActivityOrder
import org.mochios.staff.model.ActivitySignup
import org.mochios.staff.model.AuditEntry
import org.mochios.staff.model.MetricsOverview
import org.mochios.staff.model.ModerationEntry
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * Valid `tab` query-string values for the activity feed.
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
 * Aggregate UI state for the staff dashboard: the overview, and each tab's
 * rows, how far they have been read and how its reads stand, kept apart so a
 * tab switch needs no refetch once loaded and one tab failing leaves the rest
 * and the overview as they are. A tab with no [paging] entry has not been
 * read.
 */
data class DashboardUiState(
    val overviewLoading: Boolean = true,
    val overview: MetricsOverview? = null,
    /** Why the overview could not be read, while it has none to show. */
    val overviewError: MochiError? = null,
    val currentTab: String = DashboardTab.ORDERS,
    val orders: List<ActivityOrder> = emptyList(),
    val listings: List<ActivityListing> = emptyList(),
    val signups: List<ActivitySignup> = emptyList(),
    val moderation: List<ModerationEntry> = emptyList(),
    val audit: List<AuditEntry> = emptyList(),
    /** How far each tab has been read, by its cursor or page. */
    val paging: Map<String, Paging> = emptyMap(),
    /** Each tab's next page. */
    val load: Map<String, LoadState> = emptyMap(),
    /** The tabs whose first page is being read. */
    val loading: Set<String> = emptySet(),
    /** Why each tab's first page could not be read. */
    val errors: Map<String, MochiError> = emptyMap(),
) {
    /** Whether [tab] has another page to ask for: never before its first page has been read. */
    fun hasMore(tab: String): Boolean = paging[tab]?.more == true

    /** Whether [tab]'s first page is being read. */
    fun isLoading(tab: String): Boolean = tab in loading

    /** Where [tab]'s next page stands. */
    fun loadOf(tab: String): LoadState = load[tab] ?: LoadState.Idle

    /** Whether [tab]'s first page has been read. */
    fun loaded(tab: String): Boolean = tab in paging
}

private const val PAGE_SIZE = 20

/**
 * Backs [DashboardScreen]: one metrics overview plus a paged activity feed per
 * tab. The `audit` tab has no slot on `metrics/activity` and falls back to
 * `audit/list`.
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

    private val tabJobs: MutableMap<String, Job> = mutableMapOf()
    private var overviewJob: Job? = null

    init {
        loadOverview()
        loadFirstPage(_state.value.currentTab)
    }

    fun setTab(tab: String) {
        val normalised = DashboardTab.normalise(tab)
        if (normalised == _state.value.currentTab) return
        _state.update { s -> s.copy(currentTab = normalised) }
        handle["tab"] = normalised
        val s = _state.value
        if (!s.loaded(normalised) && !s.isLoading(normalised) && s.errors[normalised] == null) {
            loadFirstPage(normalised)
        }
    }

    /** Reads the overview again, as its error's retry does. */
    fun loadOverview() {
        overviewJob?.cancel()
        overviewJob = viewModelScope.launch {
            _state.update { s -> s.copy(overviewLoading = true, overviewError = null) }
            try {
                val o = repository.getMetricsOverview()
                _state.update { s -> s.copy(overviewLoading = false, overview = o) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(overviewLoading = false, overviewError = e.toMochiError()) }
            }
        }
    }

    /** Reads the current tab's first page again, as its error's retry does. */
    fun retryTab() = loadFirstPage(_state.value.currentTab)

    /**
     * Reads the current tab's next page, from the list's foot or its retry.
     * Nothing while a read of the tab is under way or the tab has no next
     * page; a tab whose first page failed has none until it is retried.
     */
    fun loadMore() {
        val s = _state.value
        val tab = s.currentTab
        val paging = s.paging[tab] ?: return
        if (s.isLoading(tab) || s.loadOf(tab) == LoadState.Loading || !paging.more) return
        tabJobs[tab]?.cancel()
        tabJobs[tab] = viewModelScope.launch {
            _state.update { state -> state.copy(load = state.load + (tab to LoadState.Loading)) }
            try {
                read(tab, paging, replace = false)
                _state.update { state -> state.copy(load = state.load + (tab to LoadState.Idle)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { state -> state.copy(load = state.load + (tab to LoadState.Failed(e.toMochiError()))) }
            }
        }
    }

    private fun loadFirstPage(tab: String) {
        tabJobs[tab]?.cancel()
        tabJobs[tab] = viewModelScope.launch {
            _state.update { s ->
                s.copy(loading = s.loading + tab, errors = s.errors - tab, load = s.load + (tab to LoadState.Idle))
            }
            try {
                read(tab, Paging(), replace = true)
                _state.update { s -> s.copy(loading = s.loading - tab) }
            } catch (e: CancellationException) {
                // The read that replaced this one owns the tab's state now.
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(loading = s.loading - tab, errors = s.errors + (tab to e.toMochiError())) }
            }
        }
    }

    /** Reads [tab]'s page after [paging], replacing its rows or adding to them. */
    private suspend fun read(tab: String, paging: Paging, replace: Boolean) {
        if (tab == DashboardTab.AUDIT) {
            val r = repository.listAudit(page = paging.page, limit = PAGE_SIZE, after = paging.cursor)
            _state.update { s ->
                s.copy(
                    audit = if (replace) r.audit else s.audit.append(r.audit) { entry -> entry.id },
                    paging = s.paging + (tab to paging.advance(r.audit.size, r.total, r.next)),
                )
            }
            return
        }
        val data = repository.getMetricsActivity(tab = tab, page = paging.page, limit = PAGE_SIZE, after = paging.cursor)
        _state.update { s ->
            when (tab) {
                DashboardTab.ORDERS -> {
                    val rows = data.orders.orEmpty()
                    s.copy(
                        orders = if (replace) rows else s.orders.append(rows) { order -> order.id },
                        paging = s.paging + (tab to paging.advance(rows.size, data.ordersTotal ?: 0, data.next)),
                    )
                }
                DashboardTab.LISTINGS -> {
                    val rows = data.listings.orEmpty()
                    s.copy(
                        listings = if (replace) rows else s.listings.append(rows) { listing -> listing.id },
                        paging = s.paging + (tab to paging.advance(rows.size, data.listingsTotal ?: 0, data.next)),
                    )
                }
                DashboardTab.SIGNUPS -> {
                    val rows = data.signups.orEmpty()
                    s.copy(
                        signups = if (replace) rows else s.signups.append(rows) { signup -> signup.id },
                        paging = s.paging + (tab to paging.advance(rows.size, data.signupsTotal ?: 0, data.next)),
                    )
                }
                DashboardTab.MODERATION -> {
                    val rows = data.moderation.orEmpty()
                    s.copy(
                        moderation = if (replace) rows else s.moderation.append(rows) { entry -> entry.id },
                        paging = s.paging + (tab to paging.advance(rows.size, data.moderationTotal ?: 0, data.next)),
                    )
                }
                else -> s
            }
        }
    }
}
