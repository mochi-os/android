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
import org.mochios.market.model.Order
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class MyPurchasesUiState(
    val isInitialLoading: Boolean = true,
    /** The next page of orders, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val orders: List<Order> = emptyList(),
    val paging: Paging = Paging(),
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

    /** The read under way, which a reload replaces. */
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = MyPurchasesUiState()
            try {
                val ordersResponse = repository.listPurchases(page = 1, limit = PAGE_SIZE)
                // Win-bid strip — best effort, surfaces empty on failure.
                val wonBids = runCatching {
                    repository.myBids(status = "won").bids
                }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    orders = ordersResponse.orders,
                    paging = Paging().advance(ordersResponse.orders.size, ordersResponse.total, ordersResponse.next),
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

    /** Pulls the next page of orders, from the list's foot or its retry. */
    fun loadMore() {
        val s = _uiState.value
        if (s.isInitialLoading || s.load == LoadState.Loading || !s.paging.more) return
        job = viewModelScope.launch {
            _uiState.value = s.copy(load = LoadState.Loading)
            try {
                val r = repository.listPurchases(page = s.paging.page, limit = PAGE_SIZE, after = s.paging.cursor)
                _uiState.value = _uiState.value.copy(
                    load = LoadState.Idle,
                    orders = _uiState.value.orders.append(r.orders) { order -> order.id },
                    paging = s.paging.advance(r.orders.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }
}
