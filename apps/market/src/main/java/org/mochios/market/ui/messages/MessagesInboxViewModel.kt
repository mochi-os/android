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
import org.mochios.android.auth.SessionManager
import org.mochios.market.model.MarketThread
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * UI state for [MessagesInboxScreen]. Holds the paginated inbox plus the
 * resolved server URL so list rows can build avatar URLs for the other
 * party without each row pulling them from the session manager themselves.
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
    sessionManager: SessionManager,
) : ViewModel() {

    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

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
