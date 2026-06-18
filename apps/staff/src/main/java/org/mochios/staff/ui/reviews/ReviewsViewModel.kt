package org.mochios.staff.ui.reviews

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.android.auth.SessionManager
import org.mochios.staff.model.Review
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * Status filter for the reviews queue. Mirrors the web's `Select` options in
 * `apps/staff/web/src/features/reviews/reviews-page.tsx`. [ALL] sends no
 * `status` parameter so the Comptroller returns every status.
 */
enum class ReviewStatusFilter { ALL, PUBLISHED, HIDDEN, REMOVED }

/** Wire value the Comptroller expects for the status query (or null for ALL). */
fun ReviewStatusFilter.wireValue(): String? = when (this) {
    ReviewStatusFilter.ALL -> null
    ReviewStatusFilter.PUBLISHED -> "published"
    ReviewStatusFilter.HIDDEN -> "hidden"
    ReviewStatusFilter.REMOVED -> "removed"
}

/**
 * UI state for [ReviewsScreen]. Mirrors web's `ReviewsPage` local state shape
 * — the active filter, the loaded reviews, the running pagination cursor,
 * and the per-row submitting flag (kept off the network model so the table
 * doesn't blink when a single row updates).
 */
data class ReviewsUiState(
    val filter: ReviewStatusFilter = ReviewStatusFilter.ALL,
    val reviews: List<Review> = emptyList(),
    val total: Long = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: MochiError? = null,
    val hasMore: Boolean = false,
    val pendingRemove: Review? = null,
    val submitting: Boolean = false,
)

/** One-shot side effects forwarded to the snackbar host. */
sealed interface ReviewsEvent {
    data class Toast(val messageRes: Int) : ReviewsEvent
    data class Error(val error: MochiError) : ReviewsEvent
}

/**
 * Backing ViewModel for the staff reviews moderation screen. Loads the first
 * page on init, refetches on filter change, and exposes `hide` / `restore` /
 * `remove` actions that map to the Comptroller's `reviews/action` endpoint.
 *
 * [SessionManager] is injected only to keep the constructor consistent with
 * sibling staff ViewModels — the reviews screen renders avatars via the
 * Comptroller asset proxy (`/staff/-/user/:user/asset/avatar`) which is
 * constructed at the call site, not here.
 */
@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val repo: StaffRepository,
    sessionManager: SessionManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

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

    private var page: Int = 1

    init {
        loadFirstPage()
    }

    fun setFilter(filter: ReviewStatusFilter) {
        if (_state.value.filter == filter) return
        _state.value = _state.value.copy(filter = filter)
        savedStateHandle[KEY_FILTER] = filter.name
        loadFirstPage()
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                page += 1
                val resp = repo.listReviews(
                    status = s.filter.wireValue(),
                    page = page,
                    limit = PAGE_LIMIT,
                )
                val merged = _state.value.reviews + resp.reviews
                _state.value = _state.value.copy(
                    reviews = merged,
                    total = resp.total,
                    hasMore = merged.size.toLong() < resp.total,
                    isLoadingMore = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
                _events.send(ReviewsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun runAction(review: Review, action: String) {
        // Hide / restore both run inline (no confirmation). Remove flows through
        // [askRemove] -> [confirmRemove] so the user gets the ConfirmDialog.
        viewModelScope.launch {
            try {
                val updated = repo.actionReview(review.id, action)
                _state.value = _state.value.copy(
                    reviews = _state.value.reviews.map { if (it.id == updated.id) updated else it },
                )
                _events.send(
                    ReviewsEvent.Toast(org.mochios.staff.R.string.staff_reviews_toast_updated),
                )
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
                    reviews = _state.value.reviews.map { if (it.id == updated.id) updated else it },
                    pendingRemove = null,
                    submitting = false,
                )
                _events.send(
                    ReviewsEvent.Toast(org.mochios.staff.R.string.staff_reviews_toast_removed),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.send(ReviewsEvent.Error(e.toMochiError()))
            }
        }
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            page = 1
            try {
                val resp = repo.listReviews(
                    status = _state.value.filter.wireValue(),
                    page = page,
                    limit = PAGE_LIMIT,
                )
                _state.value = _state.value.copy(
                    reviews = resp.reviews,
                    total = resp.total,
                    hasMore = resp.reviews.size.toLong() < resp.total,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    companion object {
        private const val PAGE_LIMIT = 20

        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_FILTER = "filter_status"
    }
}
