package org.mochios.market.ui.buying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.market.model.Subscription
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class MySubscriptionsUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val subscriptions: List<Subscription> = emptyList(),
    val total: Long = 0,
    val error: MochiError? = null,
    val mutatingId: Long? = null,
)

sealed interface MySubscriptionsEvent {
    data class Toast(val message: String) : MySubscriptionsEvent
}

private const val PAGE_SIZE = 20

@HiltViewModel
class MySubscriptionsViewModel @Inject constructor(
    private val repository: MarketRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MySubscriptionsUiState())
    val uiState: StateFlow<MySubscriptionsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MySubscriptionsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MySubscriptionsEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val r = repository.mySubscriptions(page = 1, limit = PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    subscriptions = r.subscriptions,
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
        if (s.isLoadingMore || s.subscriptions.size >= s.total) return
        val nextPage = (s.subscriptions.size / PAGE_SIZE) + 1
        viewModelScope.launch {
            _uiState.value = s.copy(isLoadingMore = true)
            try {
                val r = repository.mySubscriptions(page = nextPage, limit = PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    subscriptions = _uiState.value.subscriptions + r.subscriptions,
                    total = r.total,
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun pause(id: Long) = mutate(id) { repository.pauseSubscription(id) }
    fun resume(id: Long) = mutate(id) { repository.resumeSubscription(id) }
    fun reactivate(id: Long) = mutate(id) { repository.reactivateSubscription(id) }
    fun cancel(id: Long) = mutate(id) { repository.cancelSubscription(id) }

    private fun mutate(id: Long, op: suspend () -> Subscription) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mutatingId = id)
            try {
                val updated = op()
                _uiState.value = _uiState.value.copy(
                    mutatingId = null,
                    subscriptions = _uiState.value.subscriptions.map {
                        if (it.id == updated.id) updated else it
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(mutatingId = null)
                _events.tryEmit(MySubscriptionsEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }
}
