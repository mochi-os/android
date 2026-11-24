// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reviews

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.staff.R
import org.mochios.staff.model.Review
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

enum class ReviewStatusFilter { ALL, PUBLISHED, REMOVED }

/** Wire value the Comptroller expects for the status query (or null for ALL). */
fun ReviewStatusFilter.wireValue(): String? = when (this) {
    ReviewStatusFilter.ALL -> null
    ReviewStatusFilter.PUBLISHED -> "published"
    ReviewStatusFilter.REMOVED -> "removed"
}

data class ReviewsUiState(
    val filter: ReviewStatusFilter = ReviewStatusFilter.ALL,
    val reviews: List<Review> = emptyList(),
    val total: Long = 0,
    val isLoading: Boolean = true,
    /** The next page, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    /** How far the list has been read. */
    val paging: Paging = Paging(),
    val error: MochiError? = null,
    val pendingRemove: Review? = null,
    val submitting: Boolean = false,
)

/** One-shot side effects forwarded to the snackbar host. */
sealed interface ReviewsEvent {
    data class Toast(val messageRes: Int) : ReviewsEvent
    data class Error(val error: MochiError) : ReviewsEvent
}

/**
 * [row] as a review action answered it: the answer whole when it is a list
 * row, names and listing title with it; only its new status from a server
 * that answers the bare review, which would otherwise blank the card's names.
 */
internal fun Review.after(updated: Review): Review =
    if (updated.reviewerName != null && updated.subjectName != null) updated else copy(status = updated.status)

/**
 * Backs the reviews moderation screen; `restore` / `remove` map to the
 * Comptroller's `reviews/action` endpoint.
 */
@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val repo: StaffRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReviewsUiState(
            filter = savedStateHandle.get<String>(KEY_FILTER)
                ?.let { runCatching { ReviewStatusFilter.valueOf(it) }.getOrNull() }
                ?: ReviewStatusFilter.ALL,
        ),
    )
    val state: StateFlow<ReviewsUiState> = _state.asStateFlow()

    private val _events = Channel<ReviewsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        load()
    }

    fun setFilter(filter: ReviewStatusFilter) {
        if (_state.value.filter == filter) return
        _state.value = _state.value.copy(filter = filter)
        savedStateHandle[KEY_FILTER] = filter.name
        load()
    }

    /** Reads the first page again, as the error state's retry does. */
    fun load() {
        loadJob?.cancel()
        // A load-more still in flight belongs to the filter being replaced;
        // left running, it would splice that filter's rows into the new list.
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, load = LoadState.Idle, error = null)
            try {
                val resp = repo.listReviews(
                    status = _state.value.filter.wireValue(),
                    page = 1,
                    limit = PAGE_LIMIT,
                )
                _state.value = _state.value.copy(
                    reviews = resp.reviews,
                    total = resp.total,
                    paging = Paging().advance(resp.reviews.size, resp.total, resp.next),
                    isLoading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Reads the next page, from the list's foot or its retry. */
    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.load == LoadState.Loading || !s.paging.more) return
        moreJob = viewModelScope.launch {
            _state.value = s.copy(load = LoadState.Loading)
            try {
                val resp = repo.listReviews(
                    status = s.filter.wireValue(),
                    page = s.paging.page,
                    limit = PAGE_LIMIT,
                    after = s.paging.cursor,
                )
                _state.value = _state.value.copy(
                    reviews = _state.value.reviews.append(resp.reviews) { review -> review.id },
                    total = resp.total,
                    paging = s.paging.advance(resp.reviews.size, resp.total, resp.next),
                    load = LoadState.Idle,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    fun runAction(review: Review, action: String) {
        // Restore runs inline (no confirmation). Remove flows through
        // [askRemove] -> [confirmRemove] so the user gets the confirmation dialog.
        viewModelScope.launch {
            try {
                val updated = repo.actionReview(review.id, action)
                _state.value = _state.value.copy(
                    reviews = _state.value.reviews.map { row -> if (row.id == updated.id) row.after(updated) else row },
                )
                _events.send(ReviewsEvent.Toast(R.string.staff_reviews_toast_updated))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ReviewsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun askRemove(review: Review) {
        _state.value = _state.value.copy(pendingRemove = review)
    }

    fun cancelRemove() {
        _state.value = _state.value.copy(pendingRemove = null)
    }

    fun confirmRemove() {
        val target = _state.value.pendingRemove ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            try {
                val updated = repo.actionReview(target.id, "remove")
                _state.value = _state.value.copy(
                    reviews = _state.value.reviews.map { row -> if (row.id == updated.id) row.after(updated) else row },
                    pendingRemove = null,
                    submitting = false,
                )
                _events.send(ReviewsEvent.Toast(R.string.staff_reviews_toast_removed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.send(ReviewsEvent.Error(e.toMochiError()))
            }
        }
    }

    companion object {
        private const val PAGE_LIMIT = 20

        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_FILTER = "filter_status"
    }
}
