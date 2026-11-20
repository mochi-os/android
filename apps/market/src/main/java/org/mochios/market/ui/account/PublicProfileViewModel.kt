// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.market.model.Account
import org.mochios.market.model.Review
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class PublicProfileUiState(
    val account: Account? = null,
    val reviews: List<Review> = emptyList(),
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    /** The reviews' first page while none is listed, else the next page below them. */
    val load: LoadState = LoadState.Idle,
    val paging: Paging = Paging(),
)

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MarketRepository,
) : ViewModel() {

    val accountId: String = savedStateHandle.get<String>("accountId").orEmpty()

    private val _state = MutableStateFlow(PublicProfileUiState())
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    /** The reviews' read under way, which a retry replaces. */
    private var job: Job? = null

    init {
        loadAccount()
        loadMoreReviews()
    }

    /** Re-runs the initial load for the error state's retry button. */
    fun retry() {
        loadAccount()
        job?.cancel()
        _state.value = _state.value.copy(reviews = emptyList(), load = LoadState.Idle, paging = Paging())
        loadMoreReviews()
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

    /**
     * Reads the next page of reviews, from the list's foot or its retry; the
     * first page when none is listed yet.
     */
    fun loadMoreReviews() {
        val current = _state.value
        if (current.load == LoadState.Loading || !current.paging.more) return
        _state.value = current.copy(load = LoadState.Loading)
        job = viewModelScope.launch {
            try {
                val response = repo.accountReviews(
                    id = accountId,
                    page = current.paging.page,
                    limit = PAGE_LIMIT,
                    after = current.paging.cursor,
                )
                _state.value = _state.value.copy(
                    reviews = _state.value.reviews.append(response.reviews) { review -> review.id },
                    load = LoadState.Idle,
                    paging = current.paging.advance(response.reviews.size, response.total, response.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    companion object {
        private const val PAGE_LIMIT = 20
    }
}
