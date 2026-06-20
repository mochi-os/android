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
import org.mochios.market.model.Order
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * UI state for [MyPurchasesScreen]. Orders are paginated; the won-bid
 * strip is loaded once and rendered above the order list as a quick way
 * for the buyer to complete a pending auction purchase.
 *
 * Pagination state mirrors `useLoadMore` on the web — we track the
 * total count separately from the slice we've fetched so the loader can
 * decide whether to ask for more.
 */
data class MyPurchasesUiState(
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val orders: List<Order> = emptyList(),
    val total: Long = 0,
    val wonBids: List<Bid> = emptyList(),
    val error: MochiError? = null,
)

private const val PAGE_SIZE = 20

@HiltViewModel
class MyPurchasesViewModel @Inject constructor(
    private val repository: MarketRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPurchasesUiState())
    val uiState: StateFlow<MyPurchasesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isInitialLoading = true,
                error = null,
                orders = emptyList(),
                wonBids = emptyList(),
            )
            try {
                val ordersResponse = repository.listPurchases(page = 1, limit = PAGE_SIZE)
                // Win-bid strip — best effort, surfaces empty on failure.
                val wonBids = runCatching {
                    repository.myBids(status = "won").bids
                }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    orders = ordersResponse.orders,
                    total = ordersResponse.total,
                    wonBids = wonBids,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Pulls the next page of orders. Triggered by the InfiniteList scroll watcher. */
    fun loadMore() {
        val s = _uiState.value
        if (s.isLoadingMore || s.orders.size >= s.total) return
        val nextPage = (s.orders.size / PAGE_SIZE) + 1
        viewModelScope.launch {
            _uiState.value = s.copy(isLoadingMore = true)
            try {
                val r = repository.listPurchases(page = nextPage, limit = PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    orders = _uiState.value.orders + r.orders,
                    total = r.total,
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }
}
