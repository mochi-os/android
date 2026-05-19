package org.mochios.market.ui.buying

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.market.model.Listing
import org.mochios.market.model.Subscription
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * UI state for [SubscriptionDetailScreen]. Holds the resolved
 * [Subscription] alongside the listing projection used by the summary
 * card. The Comptroller doesn't expose a dedicated
 * `subscriptions/get` endpoint yet — we resolve a single id by walking
 * the `subscriptions/mine` pages until we find a match, mirroring the
 * web side's in-memory lookup.
 */
data class SubscriptionDetailUiState(
    val isLoading: Boolean = true,
    val subscription: Subscription? = null,
    val listing: Listing? = null,
    val error: MochiError? = null,
    /** `true` while a pause / resume / reactivate / cancel call is in flight. */
    val mutating: Boolean = false,
)

sealed interface SubscriptionDetailEvent {
    data class Toast(val message: String) : SubscriptionDetailEvent
}

private const val LOOKUP_PAGE_SIZE = 50

@HiltViewModel
class SubscriptionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MarketRepository,
) : ViewModel() {

    val subscriptionId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: 0L

    private val _uiState = MutableStateFlow(SubscriptionDetailUiState())
    val uiState: StateFlow<SubscriptionDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SubscriptionDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SubscriptionDetailEvent> = _events.asSharedFlow()

    init {
        load()
    }

    /**
     * Resolve the subscription by id and, when found, fetch its parent
     * listing for the summary card's thumbnail + seller row. Walks
     * [MarketRepository.mySubscriptions] until the id appears or the
     * server reports no more pages, capped at a generous ceiling so a
     * malformed id can't trigger an unbounded scan.
     */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val sub = findSubscription(subscriptionId)
                val listing = sub?.listing?.takeIf { it > 0 }
                    ?.let {
                        runCatching { repository.getListing(it).listing }.getOrNull()
                    }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    subscription = sub,
                    listing = listing,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun pause() = mutate { repository.pauseSubscription(subscriptionId) }
    fun resume() = mutate { repository.resumeSubscription(subscriptionId) }
    fun reactivate() = mutate { repository.reactivateSubscription(subscriptionId) }
    fun cancel() = mutate { repository.cancelSubscription(subscriptionId) }

    /**
     * Walk the buyer's subscriptions list page by page until the row
     * with the requested id appears, or the cumulative total has been
     * reached. Returns `null` when the id can't be resolved — the
     * screen then renders the "not found" state.
     */
    private suspend fun findSubscription(id: Long): Subscription? {
        if (id <= 0L) return null
        var page = 1
        var seen = 0L
        var total = Long.MAX_VALUE
        // Guard against runaway loops on a malformed cursor; with a
        // 50-row page and 1 000 pages we'd already have scanned the
        // entire library of a power user before bailing.
        repeat(1_000) {
            if (seen >= total) return null
            val response = repository.mySubscriptions(page = page, limit = LOOKUP_PAGE_SIZE)
            total = response.total
            val match = response.subscriptions.firstOrNull { it.id == id }
            if (match != null) return match
            if (response.subscriptions.isEmpty()) return null
            seen += response.subscriptions.size
            page += 1
        }
        return null
    }

    private fun mutate(op: suspend () -> Subscription) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mutating = true)
            try {
                val updated = op()
                _uiState.value = _uiState.value.copy(
                    mutating = false,
                    subscription = updated,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(mutating = false)
                _events.tryEmit(SubscriptionDetailEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }
}
