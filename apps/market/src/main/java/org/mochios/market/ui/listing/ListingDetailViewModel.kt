// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.lib.RecentlyViewedStore
import org.mochios.market.lib.ReportedStore
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.Category
import org.mochios.market.model.Currency
import org.mochios.market.model.ListingDetailResponse
import org.mochios.market.model.Photo
import org.mochios.market.model.Review
import org.mochios.market.repository.MarketRepository
import org.mochios.market.repository.SavedRepository
import javax.inject.Inject

/**
 * UI state for [ListingDetailScreen]. Mirrors the loader-driven shape the
 * web listing page uses: loading, the [ListingDetailResponse] payload, or
 * an error. The view model loads the listing once on entry and again on
 * mutation (relist) so a fresh server view is always rendered.
 */
data class ListingDetailUiState(
    val isLoading: Boolean = true,
    val listing: ListingDetailResponse? = null,
    val photos: List<Photo> = emptyList(),
    val audit: List<AuditEvent> = emptyList(),
    val categories: List<Category> = emptyList(),
    val sellerReviews: List<Review> = emptyList(),
    val error: MochiError? = null,
)

/**
 * One-shot snackbar message emitted by [ListingDetailViewModel]. Carries a
 * string-resource id (and optional positional args) so the composable can
 * resolve localised text at render time.
 */
data class ListingDetailSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
)

/**
 * ViewModel for [ListingDetailScreen].
 *
 * Wraps [MarketRepository.getListing] for the initial load, exposes the
 * three on-device stores ([SavedRepository], [RecentlyViewedStore],
 * [ReportedStore]) the screen needs for the save / report / recently-viewed
 * affordances, and re-fetches the listing after a relist so the new status /
 * id surface in the UI.
 *
 * The repository raises typed [MochiError]s; every async path catches and
 * converts unexpected exceptions via [toMochiError] so the UI can render a
 * localised message instead of stack-trace text.
 */
@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val savedRepository: SavedRepository,
    private val recentStore: RecentlyViewedStore,
    private val reportedStore: ReportedStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = _state.asStateFlow()

    private val _snackbar = MutableSharedFlow<ListingDetailSnackbar>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<ListingDetailSnackbar> = _snackbar.asSharedFlow()

    /** Carries the new listing id after a successful relist so the screen can navigate to its editor. */
    private val _navigateToEdit = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToEdit: SharedFlow<String> = _navigateToEdit.asSharedFlow()

    private var currentId: String = ""

    init {
        loadCategories()
        // Hydrate the server-backed saved mirror so the bookmark toggle
        // reflects cross-device saved state on first render.
        viewModelScope.launch { savedRepository.refresh() }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(categories = repository.listCategories())
            } catch (_: Exception) {
                // Categories non-critical; category chip just hides itself.
            }
        }
    }

    fun load(id: String) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = MochiError.Unknown("Invalid listing id"),
            )
            return
        }
        currentId = id
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val resp = repository.getListing(id)
                // Photos are best-effort: the detail payload only embeds the
                // primary `photo`, so the full set comes from the public
                // `-/photos/list` endpoint. A failure just falls back to the
                // embedded photo (or an empty carousel). Sorted by rank so the
                // carousel order matches the seller's arrangement.
                val photos = runCatching {
                    repository.listPhotos(id).sortedBy { it.rank }
                }.getOrDefault(emptyList())
                // Audit fetch is best-effort: regular buyers don't have read
                // access, and a 403 must not blow up the detail render.
                val audit = runCatching {
                    repository.auditObject(kind = "listing", objectId = id).audit
                }.getOrDefault(emptyList())
                // Seller reviews are best-effort: a fetch failure (or a seller
                // with no public reviews) just hides the section. `role` is the
                // reviewer's perspective, so to show reviews *of* this seller we
                // want the ones written by buyers (buyer -> seller), not the
                // seller's own authored reviews.
                val reviews = runCatching {
                    val sellerId = resp.seller.id
                    if (sellerId.isBlank()) {
                        emptyList()
                    } else {
                        repository.accountReviews(id = sellerId, role = "buyer").reviews
                    }
                }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    isLoading = false,
                    listing = resp,
                    photos = photos,
                    audit = audit,
                    sellerReviews = reviews,
                    error = null,
                )
                // Record the visit for the "recently viewed" rail. Best-effort —
                // a write failure here must not surface as a load error.
                try {
                    recentStore.push(id)
                } catch (_: Exception) {
                    // ignore
                }
                savedRepository.refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun toggleSave() {
        val listing = _state.value.listing?.listing ?: return
        if (listing.id.isEmpty()) return
        viewModelScope.launch {
            try {
                savedRepository.toggle(listing)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.toMochiError())
            }
        }
    }

    fun reportListing(
        reason: String,
        details: String,
        onSuccess: () -> Unit = {},
    ) {
        val id = currentId
        if (id.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.createReport(
                    target = id,
                    type = "listing",
                    reason = reason,
                    details = details.ifBlank { null },
                )
                reportedStore.markReported(id)
                _snackbar.emit(
                    ListingDetailSnackbar(
                        org.mochios.market.R.string.market_report_dialog_success,
                    )
                )
                onSuccess()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.toMochiError())
                _snackbar.emit(
                    ListingDetailSnackbar(
                        org.mochios.market.R.string.market_report_dialog_failed,
                    )
                )
            }
        }
    }

    fun placeBid(
        amount: Long,
        ceiling: Long?,
        currency: Currency,
        onSuccess: () -> Unit = {},
        onInstantWin: () -> Unit = {},
    ) {
        val auctionId = _state.value.listing?.auction?.id ?: return
        viewModelScope.launch {
            try {
                val result = repository.placeBid(auctionId, amount, ceiling)
                if (result.instant == true) onInstantWin()
                // Branch on the server's bid outcome (matches the web listing
                // page): an instant buy-it-now win, an immediate outbid by an
                // existing proxy ceiling, or an ordinary accepted bid.
                val snackbar = when {
                    result.instant == true -> ListingDetailSnackbar(
                        org.mochios.market.R.string.market_bid_dialog_instant_win,
                    )
                    result.outbid == true -> {
                        val newHigh = formatPrice(result.currentBid ?: amount, currency)
                        ListingDetailSnackbar(
                            org.mochios.market.R.string.market_bid_dialog_outbid,
                            listOf(newHigh),
                        )
                    }
                    else -> ListingDetailSnackbar(
                        org.mochios.market.R.string.market_bid_dialog_success,
                    )
                }
                _snackbar.emit(snackbar)
                // Refresh so the new high bid and history surface immediately.
                load(currentId)
                onSuccess()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.toMochiError())
                _snackbar.emit(
                    ListingDetailSnackbar(
                        org.mochios.market.R.string.market_bid_dialog_failed,
                    )
                )
            }
        }
    }

    fun relistListing() {
        val id = currentId
        if (id.isEmpty()) return
        viewModelScope.launch {
            try {
                val resp = repository.relistListing(id)
                _navigateToEdit.emit(resp.listing.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.toMochiError())
                _snackbar.emit(
                    ListingDetailSnackbar(
                        org.mochios.market.R.string.market_listing_detail_relist_failed,
                    )
                )
            }
        }
    }

    fun isSaved(): Flow<Boolean> =
        savedRepository.observeIds().map { set -> currentId in set }

    fun isReported(): Flow<Boolean> =
        reportedStore.observe().map { set -> currentId.toString() in set }
}
