// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.model.Bid
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * Tab filters for [MyBidsScreen]. The `wire` value is what the server's
 * `bids/mine` endpoint expects in the `status` param; `null` means "no
 * filter" and is sent as an absent query.
 */
enum class BidsFilter(val wire: String?) {
    ALL(null),
    ACTIVE("active"),
    OUTBID("outbid"),
    WON("won"),
    LOST("lost"),
}

data class MyBidsUiState(
    val filter: BidsFilter = BidsFilter.ALL,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val bids: List<Bid> = emptyList(),
    val total: Long = 0,
    val error: MochiError? = null,
)

private const val PAGE_SIZE = 20

@HiltViewModel
class MyBidsViewModel @Inject constructor(
    private val repository: MarketRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyBidsUiState())
    val uiState: StateFlow<MyBidsUiState> = _uiState.asStateFlow()

    init {
        load(BidsFilter.ALL)
    }

    fun setFilter(filter: BidsFilter) {
        if (_uiState.value.filter == filter) return
        load(filter)
    }

    private fun load(filter: BidsFilter) {
        viewModelScope.launch {
            _uiState.value = MyBidsUiState(filter = filter, isLoading = true)
            try {
                val r = repository.myBids(status = filter.wire, page = 1, limit = PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    bids = r.bids,
                    total = r.total,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoadingMore || s.bids.size >= s.total) return
        val nextPage = (s.bids.size / PAGE_SIZE) + 1
        viewModelScope.launch {
            _uiState.value = s.copy(isLoadingMore = true)
            try {
                val r = repository.myBids(status = s.filter.wire, page = nextPage, limit = PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    bids = _uiState.value.bids + r.bids,
                    total = r.total,
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }
}
