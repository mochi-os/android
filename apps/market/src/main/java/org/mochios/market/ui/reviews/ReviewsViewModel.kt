// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.market.model.Review
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * Per-tab pagination state for [ReviewsScreen]. The screen owns one of
 * these per tab so each side maintains its own cursor and list.
 */
data class ReviewsTabState(
    val reviews: List<Review> = emptyList(),
    /** The first page while nothing is listed, else the next page below the list. */
    val load: LoadState = LoadState.Idle,
    val paging: Paging = Paging(),
    /** Per-review response drafts keyed by review id. */
    val responseDrafts: Map<String, String> = emptyMap(),
)

data class ReviewsUiState(
    val selectedTab: ReviewsTab = ReviewsTab.RECEIVED,
    val received: ReviewsTabState = ReviewsTabState(),
    val sent: ReviewsTabState = ReviewsTabState(),
)

/** Tab identifier used by both the UI and the ViewModel. */
enum class ReviewsTab { RECEIVED, SENT }

/** One-shot events for the snackbar host. */
sealed interface ReviewsEvent {
    data class Error(val error: MochiError) : ReviewsEvent

    /** The listing a review's order was for, read so its title can open it. */
    data class OpenListing(val listingId: String, val title: String) : ReviewsEvent
}

@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewsUiState())
    val state: StateFlow<ReviewsUiState> = _state.asStateFlow()

    private val _events = Channel<ReviewsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Each tab's read under way, which a retry of its first page replaces. */
    private val jobs = mutableMapOf<ReviewsTab, Job>()

    init {
        loadMore(ReviewsTab.RECEIVED)
    }

    /** Reads the selected tab again from its first page. */
    fun retry() {
        val tab = _state.value.selectedTab
        jobs.remove(tab)?.cancel()
        updateTab(tab) { ReviewsTabState(responseDrafts = it.responseDrafts) }
        loadMore(tab)
    }

    fun selectTab(tab: ReviewsTab) {
        if (_state.value.selectedTab == tab) return
        _state.value = _state.value.copy(selectedTab = tab)
        val tabState = currentTabState(tab)
        if (tabState.reviews.isEmpty() && tabState.load == LoadState.Idle) {
            loadMore(tab)
        }
    }

    /**
     * Reads [tab]'s next page, from the list's foot or its retry; the first
     * page when nothing is listed yet.
     */
    fun loadMore(tab: ReviewsTab) {
        val tabState = currentTabState(tab)
        if (tabState.load == LoadState.Loading || !tabState.paging.more) return
        val paging = tabState.paging
        updateTab(tab) { it.copy(load = LoadState.Loading) }
        jobs[tab] = viewModelScope.launch {
            try {
                val (page, total, next) = when (tab) {
                    ReviewsTab.RECEIVED -> {
                        val r = repo.inboxReviews(page = paging.page, limit = PAGE_LIMIT, after = paging.cursor)
                        Triple(r.reviews, r.total, r.next)
                    }
                    ReviewsTab.SENT -> {
                        val r = repo.sentReviews(page = paging.page, limit = PAGE_LIMIT, after = paging.cursor)
                        Triple(r.reviews, r.total, r.next)
                    }
                }
                updateTab(tab) {
                    it.copy(
                        reviews = it.reviews.append(page) { review -> review.id },
                        load = LoadState.Idle,
                        paging = paging.advance(page.size, total, next),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateTab(tab) { it.copy(load = LoadState.Failed(e.toMochiError())) }
            }
        }
    }

    fun setResponseDraft(reviewId: String, value: String) {
        updateTab(ReviewsTab.RECEIVED) { state ->
            val updated = state.responseDrafts.toMutableMap().apply {
                if (value.isEmpty()) remove(reviewId) else put(reviewId, value)
            }
            state.copy(responseDrafts = updated)
        }
    }

    fun submitResponse(reviewId: String) {
        val draft = _state.value.received.responseDrafts[reviewId]?.trim().orEmpty()
        if (draft.isEmpty()) return
        viewModelScope.launch {
            try {
                val updated = repo.respondToReview(reviewId, draft)
                updateTab(ReviewsTab.RECEIVED) { state ->
                    val newList = state.reviews.map { r ->
                        if (r.id == reviewId) updated else r
                    }
                    val newDrafts = state.responseDrafts.toMutableMap()
                    newDrafts.remove(reviewId)
                    state.copy(reviews = newList, responseDrafts = newDrafts)
                }
            } catch (e: Exception) {
                _events.send(ReviewsEvent.Error(e.toMochiError()))
            }
        }
    }

    /**
     * Opens the listing a review is on. A review names only its [order], so
     * the order is read for the listing it was placed on.
     */
    fun openListing(order: String) {
        if (order.isEmpty()) {
            return
        }
        viewModelScope.launch {
            try {
                val listing = repo.getOrder(order).listing
                if (listing.id.isNotEmpty()) {
                    _events.send(ReviewsEvent.OpenListing(listing.id, listing.title))
                }
            } catch (e: Exception) {
                _events.send(ReviewsEvent.Error(e.toMochiError()))
            }
        }
    }

    private fun currentTabState(tab: ReviewsTab): ReviewsTabState =
        when (tab) {
            ReviewsTab.RECEIVED -> _state.value.received
            ReviewsTab.SENT -> _state.value.sent
        }

    private fun updateTab(tab: ReviewsTab, transform: (ReviewsTabState) -> ReviewsTabState) {
        _state.value = when (tab) {
            ReviewsTab.RECEIVED -> _state.value.copy(received = transform(_state.value.received))
            ReviewsTab.SENT -> _state.value.copy(sent = transform(_state.value.sent))
        }
    }

    companion object {
        private const val PAGE_LIMIT = 20
    }
}
