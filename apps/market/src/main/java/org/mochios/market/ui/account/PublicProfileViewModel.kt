package org.mochios.market.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.model.Account
import org.mochios.market.model.Review
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * UI state for [PublicProfileScreen]. Holds the [Account] projection
 * returned by `accounts/get?id=<entity>`, the running reviews list (filed
 * against this account), the per-star count breakdown derived from the
 * reviews, and the pagination cursor.
 */
data class PublicProfileUiState(
    val account: Account? = null,
    val reviews: List<Review> = emptyList(),
    val ratingBreakdown: IntArray = IntArray(5),
    val isLoading: Boolean = true,
    val isLoadingReviews: Boolean = false,
    val hasMore: Boolean = true,
    val error: MochiError? = null,
) {
    // IntArray equality is reference-based by default; data class generated
    // equals/hashCode would compare by identity. Override so two states with
    // the same breakdown compare equal — useful for state-flow distinct.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicProfileUiState) return false
        return account == other.account &&
            reviews == other.reviews &&
            ratingBreakdown.contentEquals(other.ratingBreakdown) &&
            isLoading == other.isLoading &&
            isLoadingReviews == other.isLoadingReviews &&
            hasMore == other.hasMore &&
            error == other.error
    }

    override fun hashCode(): Int {
        var result = account?.hashCode() ?: 0
        result = 31 * result + reviews.hashCode()
        result = 31 * result + ratingBreakdown.contentHashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isLoadingReviews.hashCode()
        result = 31 * result + hasMore.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

/**
 * ViewModel for [PublicProfileScreen]. Reads `accountId` from
 * [SavedStateHandle] (wired by the nav graph) and fires two parallel
 * loads: the account itself and the first page of reviews. Reviews paginate
 * via [loadMoreReviews] as the user scrolls.
 */
@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MarketRepository,
) : ViewModel() {

    val accountId: String = savedStateHandle.get<String>("accountId").orEmpty()

    private val _state = MutableStateFlow(PublicProfileUiState())
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    private var reviewsPage: Int = 1

    init {
        loadAccount()
        loadReviewsFirstPage()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val account = repo.getAccount(accountId)
                _state.value = _state.value.copy(
                    account = account,
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

    private fun loadReviewsFirstPage() {
        viewModelScope.launch {
            reviewsPage = 1
            _state.value = _state.value.copy(isLoadingReviews = true)
            try {
                val response = repo.accountReviews(
                    id = accountId,
                    page = reviewsPage,
                    limit = PAGE_LIMIT,
                )
                val merged = response.reviews
                _state.value = _state.value.copy(
                    reviews = merged,
                    ratingBreakdown = computeBreakdown(merged),
                    isLoadingReviews = false,
                    hasMore = merged.size.toLong() < response.total,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingReviews = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun loadMoreReviews() {
        val current = _state.value
        if (current.isLoadingReviews || !current.hasMore) return
        viewModelScope.launch {
            _state.value = current.copy(isLoadingReviews = true)
            reviewsPage += 1
            try {
                val response = repo.accountReviews(
                    id = accountId,
                    page = reviewsPage,
                    limit = PAGE_LIMIT,
                )
                val merged = current.reviews + response.reviews
                _state.value = _state.value.copy(
                    reviews = merged,
                    ratingBreakdown = computeBreakdown(merged),
                    isLoadingReviews = false,
                    hasMore = merged.size.toLong() < response.total,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingReviews = false)
            }
        }
    }

    /**
     * Tally up the 1–5 star buckets across the loaded review pages. Indexed
     * by `rating - 1` so the array's index 0 is one-star, index 4 is five.
     * Returns an all-zeros array when there are no reviews.
     */
    private fun computeBreakdown(reviews: List<Review>): IntArray {
        val counts = IntArray(5)
        for (review in reviews) {
            val r = review.rating.toInt().coerceIn(1, 5) - 1
            counts[r] += 1
        }
        return counts
    }

    companion object {
        private const val PAGE_LIMIT = 20
    }
}
