// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.market.model.Order
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class MySalesUiState(
    val orders: List<Order> = emptyList(),
    /** The first page while nothing is listed, else the next page below the list. */
    val load: LoadState = LoadState.Idle,
    val paging: Paging = Paging(),
)

@HiltViewModel
class MySalesViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MySalesUiState())
    val state: StateFlow<MySalesUiState> = _state.asStateFlow()

    /** The read under way, which a refresh replaces. */
    private var job: Job? = null

    init {
        loadMore()
    }

    /** Reads the list again from its first page. */
    fun refresh() {
        job?.cancel()
        _state.value = MySalesUiState()
        loadMore()
    }

    /**
     * Reads the next page, from the list's foot or its retry; the first page
     * when nothing is listed yet.
     */
    fun loadMore() {
        val current = _state.value
        if (current.load == LoadState.Loading || !current.paging.more) return
        _state.value = current.copy(load = LoadState.Loading)
        job = viewModelScope.launch {
            try {
                val response = repo.listSales(page = current.paging.page, limit = PAGE_LIMIT, after = current.paging.cursor)
                _state.value = _state.value.copy(
                    orders = _state.value.orders.append(response.orders) { order -> order.id },
                    load = LoadState.Idle,
                    paging = current.paging.advance(response.orders.size, response.total, response.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    companion object {
        private const val PAGE_LIMIT = 30
    }
}
