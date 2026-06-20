// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.model.MarketThread
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * UI state for [MessagesInboxScreen]. Holds the paginated inbox; list rows
 * build the other party's avatar URL as a server-relative path the avatar
 * component resolves against the session server.
 */
data class MessagesInboxUiState(
    val threads: List<MarketThread> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: MochiError? = null,
)

/**
 * ViewModel for the marketplace inbox. Paginates through
 * [MarketRepository.myThreads] (the repository wrapper around
 * `-/threads/mine`). Mirrors the structure of [HomeViewModel] —
 * page cursor accumulates, the UI calls [loadMore] from
 * [InfiniteList].
 */
@HiltViewModel
class MessagesInboxViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MessagesInboxUiState())
    val state: StateFlow<MessagesInboxUiState> = _state.asStateFlow()

    private var page: Int = 1
    private val limit: Int = PAGE_LIMIT

    init {
        loadFirstPage()
    }

    fun refresh() {
        page = 1
        _state.value = MessagesInboxUiState()
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = repo.myThreads(page = page, limit = limit)
                _state.value = _state.value.copy(
                    threads = response.threads,
                    isLoading = false,
                    hasMore = response.threads.size.toLong() < response.total,
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
                val response = repo.myThreads(page = page, limit = limit)
                val merged = current.threads + response.threads
                _state.value = _state.value.copy(
                    threads = merged,
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
