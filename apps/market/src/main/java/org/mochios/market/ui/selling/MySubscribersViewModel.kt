// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.model.Subscription
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class MySubscribersUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: MochiError? = null,
)

@HiltViewModel
class MySubscribersViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MySubscribersUiState())
    val state: StateFlow<MySubscribersUiState> = _state.asStateFlow()

    private var page = 1
    private val limit = PAGE_LIMIT

    init {
        loadFirstPage()
    }

    fun refresh() {
        page = 1
        _state.value = MySubscribersUiState()
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = repo.listSubscribers(page = page, limit = limit)
                _state.value = _state.value.copy(
                    subscriptions = response.subscriptions,
                    isLoading = false,
                    hasMore = response.subscriptions.size.toLong() < response.total,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || !current.hasMore) return
        viewModelScope.launch {
            _state.value = current.copy(isLoading = true)
            page += 1
            try {
                val response = repo.listSubscribers(page = page, limit = limit)
                val merged = current.subscriptions + response.subscriptions
                _state.value = _state.value.copy(
                    subscriptions = merged,
                    isLoading = false,
                    hasMore = merged.size.toLong() < response.total,
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
        private const val PAGE_LIMIT = 30
    }
}
