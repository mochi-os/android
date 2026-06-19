package org.mochios.market.ui.selling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.model.AccountFees
import org.mochios.market.model.Category
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.mochios.market.model.StripeStatus
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * Filter axis for [MyListingsScreen]. `null` (returned by the picker as a
 * sentinel) means "All". Each enum value maps to the wire string the
 * `-/listings/mine` endpoint expects (lowercase, matching
 * [ListingStatus]'s `@SerializedName`).
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
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val error: MochiError? = null,

    val statusFilter: ListingsStatusFilter = ListingsStatusFilter.ALL,
    /** Local title filter applied client-side. The server-side query param is
     *  not wired up yet because the comptroller does substring matching
     *  inefficiently; the SPA does the same client-side approach. */
    val searchQuery: String = "",

    /** Account fee disclosure rendered at the top of the screen. */
    val fees: AccountFees? = null,
    val stripeStatus: StripeStatus? = null,

    /** Category list used to resolve `Listing.category` ids into names for the badge chip. */
    val categories: List<Category> = emptyList(),

    /** Listing currently in-flight for a delete or relist mutation. */
    val mutatingId: String? = null,
    /** Per-action error message used for inline toast routing. */
    val mutationError: MochiError? = null,
)

/**
 * Side-effect events emitted alongside state changes. Used for snackbar +
 * navigation triggers that we don't want to bake into the persistent state.
 */
sealed class MyListingsEvent {
    data class NavigateToEdit(val listingId: String) : MyListingsEvent()
    data class Toast(val message: String) : MyListingsEvent()
}

@HiltViewModel
class MyListingsViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyListingsUiState())
    val state: StateFlow<MyListingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MyListingsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MyListingsEvent> = _events.asSharedFlow()

    private var page = 1
    private val pageLimit = PAGE_LIMIT
    private var loadJob: Job? = null

    init {
        loadFees()
        loadStripeStatus()
        loadCategories()
        loadFirstPage()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(categories = repo.listCategories())
            } catch (_: Exception) {
                // Categories are non-critical; cards just omit the chip.
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

    fun refresh() {
        page = 1
        _state.value = _state.value.copy(
            listings = emptyList(),
            isRefreshing = true,
            hasMore = false,
            error = null,
        )
        loadFirstPage()
    }

    private fun loadFirstPage() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = repo.mineListings(
                    status = _state.value.statusFilter.wire,
                    query = null,
                    page = page,
                    limit = pageLimit,
                )
                _state.value = _state.value.copy(
                    listings = response.listings,
                    isLoading = false,
                    isRefreshing = false,
                    hasMore = response.listings.size.toLong() < response.total,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || !current.hasMore) return
        viewModelScope.launch {
            _state.value = current.copy(isLoading = true)
            page += 1
            try {
                val response = repo.mineListings(
                    status = current.statusFilter.wire,
                    query = null,
                    page = page,
                    limit = pageLimit,
                )
                val merged = current.listings + response.listings
                _state.value = _state.value.copy(
                    listings = merged,
                    isLoading = false,
                    hasMore = merged.size.toLong() < response.total,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    // ---- Account fees + stripe ----

    private fun loadFees() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(fees = repo.getFees())
            } catch (_: Exception) {
                // Non-fatal — the fee disclosure card just hides itself.
            }
        }
    }

    private fun loadStripeStatus() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(stripeStatus = repo.stripeStatus())
            } catch (_: Exception) {
                // Non-fatal — the banner just hides itself.
            }
        }
    }

    // ---- Mutations ----

    fun deleteListing(listing: Listing, failureFallback: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(mutatingId = listing.id, mutationError = null)
            try {
                repo.deleteListing(listing.id)
                _state.value = _state.value.copy(
                    listings = _state.value.listings.filterNot { it.id == listing.id },
                    mutatingId = null,
                )
            } catch (e: Exception) {
                val err = e.toMochiError()
                _state.value = _state.value.copy(mutatingId = null, mutationError = err)
                val msg = errorMessageOrFallback(err, failureFallback)
                _events.emit(MyListingsEvent.Toast(msg))
            }
        }
    }

    fun relistListing(listing: Listing, failureFallback: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(mutatingId = listing.id, mutationError = null)
            try {
                val response = repo.relistListing(listing.id)
                _state.value = _state.value.copy(mutatingId = null)
                _events.emit(MyListingsEvent.NavigateToEdit(response.listing.id))
            } catch (e: Exception) {
                val err = e.toMochiError()
                _state.value = _state.value.copy(mutatingId = null, mutationError = err)
                val msg = errorMessageOrFallback(err, failureFallback)
                _events.emit(MyListingsEvent.Toast(msg))
            }
        }
    }

    // ---- Helpers ----

    private fun errorMessageOrFallback(error: MochiError, fallback: String): String {
        val server = when (error) {
            is MochiError.AuthError -> error.message
            is MochiError.ForbiddenError -> error.message
            is MochiError.NotFoundError -> error.message
            is MochiError.ServerError -> error.message
            is MochiError.Unknown -> error.message
            else -> null
        }
        return server?.takeIf { it.isNotBlank() } ?: fallback
    }

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
