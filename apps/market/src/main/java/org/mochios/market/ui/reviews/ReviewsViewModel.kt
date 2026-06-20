// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.model.Review
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * Per-tab pagination state for [ReviewsScreen]. The screen owns one of
 * these per tab so each side maintains its own cursor and list.
 */
data class ReviewsTabState(
    val reviews: List<Review> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: MochiError? = null,
    /** Per-review response drafts keyed by review id. */
    val responseDrafts: Map<String, String> = emptyMap(),
)

/**
 * Aggregate state for [ReviewsScreen] — the two tabs plus the currently
 * selected one. Mirrors web's `apps/market/web/src/features/reviews/
 * ReviewsTabs` shape.
 */
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
}

/**
 * ViewModel for the My Reviews screen. Loads `inbox` and `sent` on demand
 * — the first page for the active tab fires on init; the second tab waits
 * until the user actually flips to it.
 */
@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewsUiState())
    val state: StateFlow<ReviewsUiState> = _state.asStateFlow()

    private val _events = Channel<ReviewsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var receivedPage: Int = 1
    private var sentPage: Int = 1

    init {
        loadFirstPage(ReviewsTab.RECEIVED)
    }

    fun selectTab(tab: ReviewsTab) {
        if (_state.value.selectedTab == tab) return
        _state.value = _state.value.copy(selectedTab = tab)
        val tabState = currentTabState(tab)
        if (tabState.reviews.isEmpty() && !tabState.isLoading) {
            loadFirstPage(tab)
        }
    }

    fun loadMore(tab: ReviewsTab) {
        val tabState = currentTabState(tab)
        if (tabState.isLoading || !tabState.hasMore) return
        viewModelScope.launch {
            updateTab(tab) { it.copy(isLoading = true) }
            try {
                val (page, next) = when (tab) {
                    ReviewsTab.RECEIVED -> {
                        receivedPage += 1
                        val r = repo.inboxReviews(page = receivedPage, limit = PAGE_LIMIT)
                        Pair(r.reviews, r.total)
                    }
                    ReviewsTab.SENT -> {
                        sentPage += 1
                        val r = repo.sentReviews(page = sentPage, limit = PAGE_LIMIT)
                        Pair(r.reviews, r.total)
                    }
                }
                updateTab(tab) {
                    val merged = it.reviews + page
                    it.copy(
                        reviews = merged,
                        isLoading = false,
                        hasMore = merged.size.toLong() < next,
                    )
                }
            } catch (e: Exception) {
                updateTab(tab) {
                    it.copy(isLoading = false, error = e.toMochiError())
                }
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

    private fun loadFirstPage(tab: ReviewsTab) {
        viewModelScope.launch {
            updateTab(tab) { it.copy(isLoading = true, error = null) }
            try {
                val (page, next) = when (tab) {
                    ReviewsTab.RECEIVED -> {
                        receivedPage = 1
                        val r = repo.inboxReviews(page = receivedPage, limit = PAGE_LIMIT)
                        Pair(r.reviews, r.total)
                    }
                    ReviewsTab.SENT -> {
                        sentPage = 1
                        val r = repo.sentReviews(page = sentPage, limit = PAGE_LIMIT)
                        Pair(r.reviews, r.total)
                    }
                }
                updateTab(tab) {
                    it.copy(
                        reviews = page,
                        isLoading = false,
                        hasMore = page.size.toLong() < next,
                    )
                }
            } catch (e: Exception) {
                updateTab(tab) {
                    it.copy(isLoading = false, error = e.toMochiError())
                }
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
