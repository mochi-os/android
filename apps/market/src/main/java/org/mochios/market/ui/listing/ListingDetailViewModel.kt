// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.util.AttachmentOpener
import org.mochios.market.R
import org.mochios.market.lib.RecentlyViewedStore
import org.mochios.market.lib.ReportedStore
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.AssetDownload
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.Category
import org.mochios.market.model.Currency
import org.mochios.market.model.ListingDetailResponse
import org.mochios.market.model.Photo
import org.mochios.market.model.Review
import org.mochios.market.repository.MarketRepository
import org.mochios.market.repository.SavedRepository
import javax.inject.Inject

data class ListingDetailUiState(
    val isLoading: Boolean = true,
    val listing: ListingDetailResponse? = null,
    val photos: List<Photo> = emptyList(),
    val audit: List<AuditEvent> = emptyList(),
    val categories: List<Category> = emptyList(),
    val sellerReviews: List<Review> = emptyList(),
    val error: MochiError? = null,
    /** True while a digital asset is being fetched. */
    val downloadingAsset: Boolean = false,
    /** True while the caller's reservation on this listing is being released. */
    val cancellingReservation: Boolean = false,
)

data class ListingDetailSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
)

/** One-shot outcomes of a digital-asset download. */
sealed interface ListingDetailEvent {
    data class Toast(val message: String) : ListingDetailEvent
    /** Hosted elsewhere: the screen opens this externally. */
    data class OpenUrl(val url: String) : ListingDetailEvent
    /** Bytes cached and ready; the screen hands them to a viewer. */
    data class OpenFile(val fileName: String, val mime: String) : ListingDetailEvent
}

@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val savedRepository: SavedRepository,
    private val recentStore: RecentlyViewedStore,
    private val reportedStore: ReportedStore,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ListingDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ListingDetailEvent> = _events.asSharedFlow()

    /**
     * Fetches through the authenticated client: the asset action is not public,
     * so a Custom Tab (no token or cookie) gets a 401.
     */
    fun downloadAsset(assetId: String) {
        if (_state.value.downloadingAsset) return
        viewModelScope.launch {
            _state.value = _state.value.copy(downloadingAsset = true)
            try {
                when (val outcome = repository.downloadAsset(assetId)) {
                    is AssetDownload.External ->
                        _events.tryEmit(ListingDetailEvent.OpenUrl(outcome.url))
                    is AssetDownload.Bytes -> {
                        val saved = withContext(Dispatchers.IO) {
                            AttachmentOpener.cacheBytes(context, outcome.fileName, outcome.body)
                        }
                        _events.tryEmit(ListingDetailEvent.OpenFile(saved.name, outcome.mime))
                    }
                }
            } catch (e: Exception) {
                _events.tryEmit(ListingDetailEvent.Toast(e.toMochiError().userMessage()))
            } finally {
                _state.value = _state.value.copy(downloadingAsset = false)
            }
        }
    }

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
                // Await the categories before reading the state to copy from —
                // inline, the receiver is captured before the suspend and a slow
                // response writes back a pre-load snapshot.
                val categories = repository.listCategories()
                _state.value = _state.value.copy(categories = categories)
            } catch (_: Exception) {
                // Categories non-critical; category chip just hides itself.
            }
        }
    }

    fun load(id: String) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = MochiError.Local(R.string.market_listing_detail_not_found),
            )
            return
        }
        currentId = id
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val resp = repository.getListing(id)
                // The detail payload embeds only the primary photo; the full
                // set is best-effort from `-/photos/list`.
                val photos = runCatching {
                    repository.listPhotos(id).sortedBy { it.rank }
                }.getOrDefault(emptyList())
                // Audit fetch is best-effort: regular buyers don't have read
                // access, and a 403 must not blow up the detail render.
                val audit = runCatching {
                    repository.auditObject(kind = "listing", objectId = id).audit
                }.getOrDefault(emptyList())
                // Best-effort. `role` is the reviewer's perspective: reviews of
                // this seller are those written by buyers.
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

    /**
     * Release the caller's in-progress checkout, so the listing offers Buy now
     * again. Reloads on success: the reservation is part of the detail payload,
     * so the button has to disappear with it.
     */
    fun cancelReservation() {
        val id = currentId
        if (id.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(cancellingReservation = true)
            try {
                repository.cancelReservation(id)
                _snackbar.emit(
                    ListingDetailSnackbar(
                        org.mochios.market.R.string.market_listing_checkout_cancelled,
                    )
                )
                load(id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.toMochiError())
                _snackbar.emit(
                    ListingDetailSnackbar(
                        org.mochios.market.R.string.market_listing_checkout_cancel_failed,
                    )
                )
            } finally {
                _state.value = _state.value.copy(cancellingReservation = false)
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
