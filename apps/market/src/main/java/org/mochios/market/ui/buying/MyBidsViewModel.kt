// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

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
import org.mochios.market.model.Bid
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * `wire` is the `status` param for `bids/mine`; `null` sends none.
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
    /** The next page of bids, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val bids: List<Bid> = emptyList(),
    val paging: Paging = Paging(),
    val error: MochiError? = null,
)

private const val PAGE_SIZE = 20

@HiltViewModel
class MyBidsViewModel @Inject constructor(
    private val repository: MarketRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyBidsUiState())
    val uiState: StateFlow<MyBidsUiState> = _uiState.asStateFlow()

    /** The read under way; a change of tab replaces it, so a page of another tab is not appended. */
    private var job: Job? = null

    init {
        load(BidsFilter.ALL)
    }

    fun setFilter(filter: BidsFilter) {
        if (_uiState.value.filter == filter) return
        load(filter)
    }

    /** Reads [filter]'s first page again, as its error state's retry does. */
    fun retry() = load(_uiState.value.filter)

    private fun load(filter: BidsFilter) {
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = MyBidsUiState(filter = filter, isLoading = true)
            try {
                val r = repository.myBids(status = filter.wire, page = 1, limit = PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    bids = r.bids,
                    paging = Paging().advance(r.bids.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Reads the next page, from the list's foot or its retry. */
    fun loadMore() {
        val s = _uiState.value
        if (s.isLoading || s.load == LoadState.Loading || !s.paging.more) return
        job = viewModelScope.launch {
            _uiState.value = s.copy(load = LoadState.Loading)
            try {
                val r = repository.myBids(
                    status = s.filter.wire,
                    page = s.paging.page,
                    limit = PAGE_SIZE,
                    after = s.paging.cursor,
                )
                _uiState.value = _uiState.value.copy(
                    load = LoadState.Idle,
                    bids = _uiState.value.bids.append(r.bids) { bid -> bid.id },
                    paging = s.paging.advance(r.bids.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }
}
