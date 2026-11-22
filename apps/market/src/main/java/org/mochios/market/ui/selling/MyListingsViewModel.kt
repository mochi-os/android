// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.messageOrFallback
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.Format
import org.mochios.android.share.Shared
import org.mochios.market.ui.components.listingShare
import org.mochios.market.model.Account
import org.mochios.market.model.Listing
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * [wire] is the status value `-/listings/mine` expects; null sends no filter.
 */
enum class ListingsStatusFilter(val wire: String?) {
    ALL(null),
    DRAFT("draft"),
    ACTIVE("active"),
    SOLD("sold"),
    EXPIRED("expired"),
    REJECTED("rejected"),
    REMOVED("removed");
}

data class MyListingsUiState(
    /** Source-of-truth listings as returned by the server (unfiltered). */
    val listings: List<Listing> = emptyList(),
    /** The first page while nothing is listed, else the next page below the list. */
    val load: LoadState = LoadState.Idle,
    val paging: Paging = Paging(),

    val statusFilter: ListingsStatusFilter = ListingsStatusFilter.ALL,
    /** Local title filter applied client-side. The server-side query param is
     *  not wired up yet because the comptroller does substring matching
     *  inefficiently; the SPA does the same client-side approach. */
    val searchQuery: String = "",

    /**
     * The seller's own account, once read. A seller whose payments are set up
     * (`onboarded`) can create listings; one whose are not is shown the setup
     * banner.
     */
    val account: Account? = null,
) {
    /** Whether New listing is offered. */
    val listable: Boolean get() = account?.onboarded == 1

    /** Whether the payment setup banner is shown. */
    val onboarding: Boolean get() = account?.seller == 1 && account.onboarded != 1
}

/**
 * Side-effect events emitted alongside state changes. Used for snackbar +
 * navigation triggers that we don't want to bake into the persistent state.
 */
sealed class MyListingsEvent {
    data class NavigateToEdit(val listingId: String) : MyListingsEvent()
    data class Toast(val message: String) : MyListingsEvent()
    /** The account is not a seller's: seller settings is where one becomes a seller. */
    data object NotSeller : MyListingsEvent()
}

@HiltViewModel
class MyListingsViewModel @Inject constructor(
    private val repo: MarketRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MyListingsUiState())
    val state: StateFlow<MyListingsUiState> = _state.asStateFlow()

    private val _shares = MutableSharedFlow<Shared>(extraBufferCapacity = 1)

    /** A listing, readied for the system share sheet. */
    val shares: SharedFlow<Shared> = _shares.asSharedFlow()

    /** Share [listing], its price in [format]. */
    fun share(listing: Listing, format: Format) {
        _shares.tryEmit(listingShare(listing, session.getServerUrlBlocking(), format))
    }

    private val _events = MutableSharedFlow<MyListingsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MyListingsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadAccount()
        loadMore()
    }

    // ---- Account ----

    /**
     * Reads the seller's own account, held briefly and shared with the other
     * screens; [fresh] reads it again, after the seller has finished setting
     * up payments. A failed read leaves the account unknown: neither New
     * listing nor the banner, and a refresh asks again.
     */
    fun loadAccount(fresh: Boolean = false) {
        viewModelScope.launch {
            try {
                val account = repo.getAccount(fresh = fresh)
                _state.value = _state.value.copy(account = account)
                if (account.seller != 1) _events.emit(MyListingsEvent.NotSeller)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // ---- Filter / query ----

    fun setStatusFilter(filter: ListingsStatusFilter) {
        if (_state.value.statusFilter == filter) return
        _state.value = _state.value.copy(statusFilter = filter)
        refresh()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    // ---- Pagination ----

    /** Reads the list again from its first page, and the account if it is still unknown. */
    fun refresh() {
        loadJob?.cancel()
        _state.value = _state.value.copy(
            listings = emptyList(),
            load = LoadState.Idle,
            paging = Paging(),
        )
        if (_state.value.account == null) loadAccount()
        loadMore()
    }

    /**
     * Reads the next page, from the list's foot or its retry; the first page
     * when nothing is listed yet.
     */
    fun loadMore() {
        val current = _state.value
        if (current.load == LoadState.Loading || !current.paging.more) return
        _state.value = current.copy(load = LoadState.Loading)
        // Tracked in loadJob so a status-tab switch cancels it: an untracked
        // page fetch finishing after refresh() would merge the previous tab's
        // rows on top of the new tab's list.
        loadJob = viewModelScope.launch {
            try {
                val response = repo.mineListings(
                    status = current.statusFilter.wire,
                    query = null,
                    page = current.paging.page,
                    limit = PAGE_LIMIT,
                    after = current.paging.cursor,
                )
                _state.value = _state.value.copy(
                    listings = _state.value.listings.append(response.listings) { listing -> listing.id },
                    load = LoadState.Idle,
                    paging = current.paging.advance(response.listings.size, response.total, response.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    // ---- Mutations ----

    fun deleteListing(listing: Listing, failureFallback: String) {
        viewModelScope.launch {
            try {
                repo.deleteListing(listing.id)
                _state.value = _state.value.copy(
                    listings = _state.value.listings.filterNot { it.id == listing.id },
                )
            } catch (e: Exception) {
                val msg = e.toMochiError().messageOrFallback(failureFallback)
                _events.emit(MyListingsEvent.Toast(msg))
            }
        }
    }

    fun relistListing(listing: Listing, failureFallback: String) {
        viewModelScope.launch {
            try {
                val response = repo.relistListing(listing.id)
                _events.emit(MyListingsEvent.NavigateToEdit(response.listing.id))
            } catch (e: Exception) {
                val msg = e.toMochiError().messageOrFallback(failureFallback)
                _events.emit(MyListingsEvent.Toast(msg))
            }
        }
    }

    // ---- Helpers ----

    /**
     * Apply the local title substring filter on top of the server-supplied
     * listings list. Case-insensitive; an empty query matches everything.
     */
    fun visibleListings(): List<Listing> {
        val q = _state.value.searchQuery.trim()
        if (q.isEmpty()) return _state.value.listings
        return _state.value.listings.filter { it.title.contains(q, ignoreCase = true) }
    }

    companion object {
        private const val PAGE_LIMIT = 30
    }
}
