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
import org.mochios.android.api.toMochiError
import org.mochios.market.lib.RecentlyViewedStore
import org.mochios.market.lib.SavedStore
import org.mochios.market.model.Listing
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * Backing ViewModel for [HomeScreen]. Drives the listing grid by paginating
 * through `listings/search` and merges in the saved/recently-viewed local
 * state stores so the UI can render the right Save toggle and recently-viewed
 * strip without a second round-trip.
 *
 * The search request fires whenever [query] or [filters] changes; the page
 * cursor resets to the first page each time and accumulates as the user
 * scrolls. We don't debounce inside the ViewModel itself — that's owned by
 * the screen, which lets us keep [setQuery] synchronous and easily testable.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: MarketRepository,
    private val savedStore: SavedStore,
    private val recentStore: RecentlyViewedStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(savedStateHandle))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * One-shot save-toggle results for the screen to surface as a toast.
     * `true` means the listing was just saved, `false` means it was removed.
     */
    private val _saveEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val saveEvents: SharedFlow<Boolean> = _saveEvents.asSharedFlow()

    /**
     * Cache of listings we've already fetched by ID. Used to render the
     * recently-viewed strip without re-issuing a network request every time
     * the home screen mounts.
     */
    private val listingCache = mutableMapOf<String, Listing>()

    private var searchJob: Job? = null
    private var currentPage: Int = 1
    private val pageLimit: Int = PAGE_LIMIT

    init {
        loadCategories()
        loadRecentlyViewed()
        observeSaved()
        loadAccount()
        runSearch(reset = true)
    }

    /** Mirror the locally-saved listing IDs into state so cards fill the bookmark. */
    private fun observeSaved() {
        viewModelScope.launch {
            savedStore.observe().collect { ids ->
                _state.value = _state.value.copy(savedIds = ids)
            }
        }
    }

    private fun initialState(handle: SavedStateHandle): HomeUiState {
        val filters = mutableMapOf<Filter, String>()
        handle.get<String>("tag")?.takeIf { it.isNotBlank() }?.let { filters[Filter.TAG] = it }
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

    fun setFilter(filter: Filter, value: String?) {
        val current = _state.value.filters.toMutableMap()
        if (value.isNullOrBlank()) current.remove(filter) else current[filter] = value
        _state.value = _state.value.copy(filters = current)
        runSearch(reset = true)
    }

    fun clearFilters() {
        if (_state.value.filters.isEmpty() && _state.value.query.isBlank()) return
        _state.value = _state.value.copy(filters = emptyMap(), query = "")
        runSearch(reset = true)
    }

    fun openFilterSheet(focused: Filter? = null) {
        _state.value = _state.value.copy(filterSheetOpen = true, focusedFilter = focused)
    }

    fun closeFilterSheet() {
        _state.value = _state.value.copy(filterSheetOpen = false, focusedFilter = null)
    }

    // -- Pagination -------------------------------------------------------

    fun loadMore() {
        if (_state.value.isLoading || !_state.value.hasMore) return
        runSearch(reset = false)
    }

    // -- Save / view ------------------------------------------------------

    fun toggleSave(listing: Listing) {
        viewModelScope.launch {
            val id = listing.id.toString()
            savedStore.toggle(id)
            _saveEvents.emit(savedStore.isSaved(id))
        }
    }

    fun viewListing(listing: Listing) {
        listingCache[listing.id.toString()] = listing
        viewModelScope.launch {
            recentStore.push(listing.id.toString())
        }
    }

    // -- Onboarding -------------------------------------------------------

    /**
     * Trigger `accounts/activate` and, on success, flip [HomeUiState
     * .accountActive] to true so the onboarding card disappears. Failures
     * leave the card in place so the user can retry.
     */
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
            } catch (_: Exception) {
                // The card stays so the user can retry; we deliberately don't
                // surface a screen-level error here — the snack would be
                // jarring on the cold-start home grid.
                _state.value = _state.value.copy(activatingAccount = false)
            }
        }
    }

    /**
     * Hide the onboarding card without activating. Returning users see it
     * for a single session — local-only so a fresh install or app data wipe
     * brings it back, which is fine.
     */
    fun dismissOnboarding() {
        _state.value = _state.value.copy(accountActive = true)
    }

    // -- Internals --------------------------------------------------------

    /**
     * Resolve the caller's market account so the home screen can decide
     * whether to render the activation onboarding card and the test-mode
     * banner. Treats the absence of an account row (404 / network failure)
     * the same as `inactive` — better to nudge the user than to silently
     * hide the card on a first visit when the call hadn't completed yet.
     * `stripe_testmode` is part of the own-account response and only set
     * when the Comptroller's Stripe secret key has the `sk_test_` prefix.
     */
    private fun loadAccount() {
        viewModelScope.launch {
            try {
                val account = repo.getAccount()
                _state.value = _state.value.copy(
                    accountActive = !isInactiveAccount(account.status),
                    testMode = account.stripeTestmode,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(accountActive = false)
            }
        }
    }

    /**
     * `true` when the server's reported [org.mochios.market.model.Account
     * .status] indicates a first-time / never-activated account. Empty
     * statuses are also treated as inactive — server-side accounts that
     * exist but haven't gone through `accounts/activate` come back with no
     * `status` field set.
     */
    private fun isInactiveAccount(status: String): Boolean {
        val key = status.trim().lowercase()
        return key.isEmpty() || key == "inactive"
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = repo.listCategories()
                _state.value = _state.value.copy(categories = categories)
            } catch (e: Exception) {
                // Categories are non-critical; the rest of the screen still
                // functions without them. Surface as a soft error.
                _state.value = _state.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun loadRecentlyViewed() {
        viewModelScope.launch {
            recentStore.observe().collect { ids ->
                // Split into cache hits (rendered immediately) and misses
                // (resolved in one concurrent fan-out via the repository's
                // batch helper). The previous loop walked the IDs serially
                // — 20 round-trips per home load on a freshly opened app.
                val resolved = arrayOfNulls<Listing>(ids.size)
                val missingIndexes = mutableListOf<Int>()
                val missingIds = mutableListOf<Long>()
                ids.forEachIndexed { index, id ->
                    val cached = listingCache[id]
                    if (cached != null) {
                        resolved[index] = cached
                    } else {
                        val longId = id.toLongOrNull() ?: return@forEachIndexed
                        missingIndexes += index
                        missingIds += longId
                    }
                }
                if (missingIds.isNotEmpty()) {
                    val fetched = repo.getListingsByIds(missingIds)
                    // The batch helper drops failed individual fetches, so we
                    // can't assume `fetched.size == missingIds.size`. Match by
                    // listing id rather than positional index.
                    val byId = fetched.associateBy { it.id.toString() }
                    missingIndexes.forEach { slot ->
                        val id = ids[slot]
                        val listing = byId[id]
                        if (listing != null) {
                            listingCache[id] = listing
                            resolved[slot] = listing
                        }
                    }
                }
                _state.value = _state.value.copy(
                    recentListings = resolved.filterNotNull(),
                )
            }
        }
    }

    private fun runSearch(reset: Boolean) {
        searchJob?.cancel()
        val current = _state.value
        if (reset) {
            currentPage = 1
            _state.value = current.copy(
                listings = emptyList(),
                isLoading = true,
                hasMore = false,
                error = null,
            )
        } else {
            currentPage += 1
            _state.value = current.copy(isLoading = true, error = null)
        }
        searchJob = viewModelScope.launch {
            // Tiny coalesce so the rapid setFilter/setQuery sequence at
            // session start fires one request instead of many.
            if (reset) delay(50L)
            val params = buildParams(_state.value, page = currentPage, limit = pageLimit)
            try {
                val response = repo.searchListings(params)
                val merged = if (reset) {
                    response.listings
                } else {
                    _state.value.listings + response.listings
                }
                _state.value = _state.value.copy(
                    listings = merged,
                    isLoading = false,
                    hasMore = merged.size.toLong() < response.total,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Undo the page advance so a retry re-requests this page
                // rather than skipping it.
                if (!reset) currentPage -= 1
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    private fun buildParams(
        state: HomeUiState,
        page: Int,
        limit: Int,
    ): Map<String, String?> {
        // Tag filters are server-side wildcards on the `query` parameter — the
        // server search index already matches tags against `query`, so falling
        // through here keeps the wire shape minimal.
        val tag = state.filters[Filter.TAG]?.takeIf { it.isNotBlank() }
        val query = state.query.takeIf { it.isNotBlank() } ?: tag
        return mapOf(
            "query" to query,
            "category" to state.filters[Filter.CATEGORY],
            "type" to state.filters[Filter.TYPE],
            "condition" to state.filters[Filter.CONDITION],
            "pricing" to state.filters[Filter.PRICING],
            "min" to state.filters[Filter.PRICE_MIN],
            "max" to state.filters[Filter.PRICE_MAX],
            "delivery" to state.filters[Filter.DELIVERY],
            "sort" to state.filters[Filter.SORT],
            "page" to page.toString(),
            "limit" to limit.toString(),
        )
    }

    companion object {
        private const val PAGE_LIMIT = 24
    }
}
