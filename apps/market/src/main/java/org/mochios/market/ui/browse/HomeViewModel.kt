// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.format.toMinorUnits
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.android.util.append
import org.mochios.market.lib.RecentlyViewedStore
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.mochios.market.repository.MarketRepository
import org.mochios.market.repository.SavedRepository
import javax.inject.Inject

/**
 * Search debounce for the query lives in the screen - [setQuery] runs the
 * search at once; the price range is debounced here, since its fields change
 * a filter on every keystroke.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: MarketRepository,
    private val savedRepository: SavedRepository,
    private val recentStore: RecentlyViewedStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(savedStateHandle))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * Save-toggle results for the screen's toast: `true` saved, `false`
     * removed.
     */
    private val _saveEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val saveEvents: SharedFlow<Boolean> = _saveEvents.asSharedFlow()

    /** Actions the server refused, for the screen's snackbar. */
    private val _failures = MutableSharedFlow<MochiError>(extraBufferCapacity = 4)
    val failures: SharedFlow<MochiError> = _failures.asSharedFlow()

    private var searchJob: Job? = null

    init {
        loadCategories()
        loadRecentlyViewed()
        observeSaved()
        loadAccount()
        runSearch(reset = true)
    }

    /** Mirror the server-saved listing IDs into state so cards fill the bookmark. */
    private fun observeSaved() {
        // Read once so a fresh session shows the right bookmarks; the collect
        // below then tracks live toggles.
        viewModelScope.launch { savedRepository.refresh() }
        viewModelScope.launch {
            savedRepository.observeIds().collect { ids ->
                _state.value = _state.value.copy(savedIds = ids)
            }
        }
    }

    private fun initialState(handle: SavedStateHandle): HomeUiState {
        val filters = mutableMapOf<Filter, String>()
        handle.get<String>("category")?.takeIf { it.isNotBlank() }?.let {
            filters[Filter.CATEGORY] = it
        }
        return HomeUiState(filters = filters)
    }

    // -- Query / filter intents -------------------------------------------

    fun setQuery(q: String) {
        if (_state.value.query == q) return
        _state.value = _state.value.copy(query = q)
        runSearch(reset = true)
    }

    /**
     * Set or clear one filter. A change the search does not send (the
     * currency while no price is set) asks for nothing, and a price bound
     * waits for typing to pause.
     */
    fun setFilter(filter: Filter, value: String?) {
        val before = _state.value
        val current = before.filters.toMutableMap()
        if (value.isNullOrBlank()) current.remove(filter) else current[filter] = value
        _state.value = before.copy(filters = current)
        if (buildParams(before, Paging()) == buildParams(_state.value, Paging())) return
        val price = filter == Filter.PRICE_MIN || filter == Filter.PRICE_MAX
        runSearch(reset = true, wait = if (price) SEARCH_DEBOUNCE.inWholeMilliseconds else COALESCE)
    }

    fun clearFilters() {
        if (_state.value.filters.isEmpty() && _state.value.query.isBlank()) return
        _state.value = _state.value.copy(filters = emptyMap(), query = "")
        runSearch(reset = true)
    }

    /** Clear the on-device recently-viewed history; the observe() flow then
     *  emits empty and drops the strip. Mirrors web's clearRecentlyViewed. */
    fun clearRecentlyViewed() {
        viewModelScope.launch { recentStore.clear() }
    }

    fun openFilterSheet(focused: Filter? = null) {
        _state.value = _state.value.copy(filterSheetOpen = true, focusedFilter = focused)
    }

    fun closeFilterSheet() {
        _state.value = _state.value.copy(filterSheetOpen = false, focusedFilter = null)
    }

    // -- Pagination -------------------------------------------------------

    /** The next page, from the grid's foot or its retry. */
    fun loadMore() {
        val current = _state.value
        if (current.load == LoadState.Loading || !current.paging.more) return
        if (current.listings.isEmpty()) runSearch(reset = true, wait = 0L) else runSearch(reset = false)
    }

    /**
     * Try again after a failure: the search from its first page when nothing
     * is listed, else the page that failed; and the categories, if they did
     * not load.
     */
    fun retry() {
        if (_state.value.categories.isEmpty()) loadCategories()
        if (_state.value.listings.isEmpty()) runSearch(reset = true, wait = 0L) else loadMore()
    }

    // -- Save / view ------------------------------------------------------

    fun toggleSave(listing: Listing) {
        viewModelScope.launch {
            try {
                val nowSaved = savedRepository.toggle(listing)
                _saveEvents.emit(nowSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The optimistic change has already been undone, so the
                // bookmark is back as it was; say why.
                _failures.emit(e.toMochiError())
            }
        }
    }

    fun viewListing(listing: Listing) {
        repo.remember(listing)
        viewModelScope.launch {
            recentStore.push(listing.id)
        }
    }

    // -- Onboarding -------------------------------------------------------

    fun activateAccount() {
        if (_state.value.activatingAccount) return
        viewModelScope.launch {
            _state.value = _state.value.copy(activatingAccount = true)
            try {
                val account = repo.activateAccount()
                _state.value = _state.value.copy(
                    activatingAccount = false,
                    accountActive = !isInactiveAccount(account.status),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The card stays so the user can try again.
                _state.value = _state.value.copy(activatingAccount = false)
                _failures.emit(e.toMochiError())
            }
        }
    }

    fun dismissOnboarding() {
        _state.value = _state.value.copy(accountActive = true)
    }

    // -- Internals --------------------------------------------------------

    /**
     * Whether the account is active, once read. A read that fails leaves it
     * unknown, so an established account is not greeted as a newcomer; the
     * search's own failure tells the user the market is unreachable.
     */
    private fun loadAccount() {
        viewModelScope.launch {
            try {
                val account = repo.getAccount()
                _state.value = _state.value.copy(accountActive = !isInactiveAccount(account.status))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /**
     * An empty status is inactive too: accounts that never ran
     * `accounts/activate` carry no `status`.
     */
    private fun isInactiveAccount(status: String): Boolean {
        val key = status.trim().lowercase()
        return key.isEmpty() || key == "inactive"
    }

    /**
     * The categories name the category filter; without them the filter offers
     * only "All", and [retry] asks again. Their failure is not the search's.
     */
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = repo.listCategories()
                _state.value = _state.value.copy(categories = categories)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /**
     * The listings the account opened lately, resolved in one call. An id the
     * server no longer answers, or a listing no longer on sale, is dropped
     * from the store too, so it is not asked for again; a failed call drops
     * nothing.
     */
    private fun loadRecentlyViewed() {
        viewModelScope.launch {
            recentStore.observe().collect { ids ->
                val listings = try {
                    repo.getListingsByIds(ids)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@collect
                }
                val active = listings.filter { listing -> listing.status == ListingStatus.ACTIVE }
                val kept = active.mapTo(HashSet()) { listing -> listing.id }
                recentStore.remove(ids.filter { id -> id !in kept })
                _state.value = _state.value.copy(recentListings = active)
            }
        }
    }

    /**
     * Read the search's first page ([reset]) or its next page. A first page
     * waits [wait] milliseconds, so a burst of changes makes one request.
     */
    private fun runSearch(reset: Boolean, wait: Long = COALESCE) {
        searchJob?.cancel()
        val current = _state.value
        val paging = if (reset) Paging() else current.paging
        _state.value = if (reset) {
            current.copy(listings = emptyList(), load = LoadState.Loading, paging = paging)
        } else {
            current.copy(load = LoadState.Loading)
        }
        searchJob = viewModelScope.launch {
            if (reset && wait > 0L) delay(wait)
            val params = buildParams(_state.value, paging)
            try {
                val response = repo.searchListings(params)
                _state.value = _state.value.copy(
                    listings = _state.value.listings.append(response.listings) { listing -> listing.id },
                    load = LoadState.Idle,
                    paging = paging.advance(response.listings.size, response.total, response.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    private fun buildParams(
        state: HomeUiState,
        paging: Paging,
    ): Map<String, String?> {
        // The server compares min/max against the minor-unit price column of
        // one currency, so the typed major-unit bounds are converted here and
        // scoped to the chosen currency, as web's applyPriceRange does.
        val currency = state.filters[Filter.CURRENCY]?.takeIf { it.isNotBlank() } ?: DEFAULT_CURRENCY
        val minimum = state.filters[Filter.PRICE_MIN]?.takeIf { it.isNotBlank() }
        val maximum = state.filters[Filter.PRICE_MAX]?.takeIf { it.isNotBlank() }
        val ranged = minimum != null || maximum != null
        return mapOf(
            "query" to state.query.takeIf { it.isNotBlank() },
            "category" to state.filters[Filter.CATEGORY],
            "type" to state.filters[Filter.TYPE],
            "condition" to state.filters[Filter.CONDITION],
            "pricing" to state.filters[Filter.PRICING],
            "min" to minimum?.let { toMinorUnits(it, currency).toString() },
            "max" to maximum?.let { toMinorUnits(it, currency).toString() },
            "currency" to currency.takeIf { ranged },
            "delivery" to state.filters[Filter.DELIVERY],
            "sort" to state.filters[Filter.SORT],
            "page" to paging.page.toString(),
            "limit" to PAGE_LIMIT.toString(),
            "after" to paging.cursor,
        )
    }

    companion object {
        private const val PAGE_LIMIT = 24

        /**
         * How long a new search waits, so the rapid setFilter/setQuery
         * sequence at session start fires one request instead of many.
         */
        private const val COALESCE = 50L

        /** Web's default for an unscoped price range. */
        const val DEFAULT_CURRENCY = "usd"
    }
}
