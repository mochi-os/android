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
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.market.model.Subscription
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class MySubscriptionsUiState(
    val isLoading: Boolean = true,
    /** The next page of subscriptions, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val subscriptions: List<Subscription> = emptyList(),
    val paging: Paging = Paging(),
    val error: MochiError? = null,
    val mutatingId: String? = null,
)

sealed interface MySubscriptionsEvent {
    data class Toast(val message: String) : MySubscriptionsEvent
    /** Stripe's billing portal, which the screen opens in a browser tab. */
    data class OpenPortal(val url: String) : MySubscriptionsEvent
}

private const val PAGE_SIZE = 20

/**
 * Where Stripe's billing portal sends the buyer back: the app's own
 * subscriptions, which MainActivity reaches from a Checkout return of a
 * subscription.
 */
internal const val PORTAL_RETURN = "mochi://market/checkout/success?kind=subscription"

@HiltViewModel
class MySubscriptionsViewModel @Inject constructor(
    private val repository: MarketRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MySubscriptionsUiState())
    val uiState: StateFlow<MySubscriptionsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MySubscriptionsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MySubscriptionsEvent> = _events.asSharedFlow()

    /** The list read under way, which a reload replaces. */
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, load = LoadState.Idle)
            try {
                val r = repository.mySubscriptions(page = 1, limit = PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    subscriptions = r.subscriptions,
                    paging = Paging().advance(r.subscriptions.size, r.total, r.next),
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
                val r = repository.mySubscriptions(page = s.paging.page, limit = PAGE_SIZE, after = s.paging.cursor)
                _uiState.value = _uiState.value.copy(
                    load = LoadState.Idle,
                    subscriptions = _uiState.value.subscriptions.append(r.subscriptions) { subscription -> subscription.id },
                    paging = s.paging.advance(r.subscriptions.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    fun pause(id: String) = mutate(id) { repository.pauseSubscription(id) }
    fun resume(id: String) = mutate(id) { repository.resumeSubscription(id) }
    fun reactivate(id: String) = mutate(id) { repository.reactivateSubscription(id) }
    fun cancel(id: String) = mutate(id) { repository.cancelSubscription(id) }

    /**
     * Open Stripe's billing portal for a past-due subscription, where the
     * buyer gives a new card for the renewal that failed.
     */
    fun updatePayment(id: String) {
        if (_uiState.value.mutatingId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mutatingId = id)
            try {
                val portal = repository.subscriptionPortal(id, PORTAL_RETURN)
                _events.tryEmit(MySubscriptionsEvent.OpenPortal(portal.url))
            } catch (e: Exception) {
                _events.tryEmit(MySubscriptionsEvent.Toast(e.toMochiError().userMessage()))
            } finally {
                _uiState.value = _uiState.value.copy(mutatingId = null)
            }
        }
    }

    private fun mutate(id: String, op: suspend () -> Subscription) {
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
