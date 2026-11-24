// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.staff.R
import org.mochios.staff.model.OrphanedPayment
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

data class PaymentsUiState(
    val payments: List<OrphanedPayment> = emptyList(),
    val total: Long = 0,
    val isLoading: Boolean = true,
    /** The next page, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    /** How far the list has been read. */
    val paging: Paging = Paging(),
    val error: MochiError? = null,
    /** The payment whose refund waits on the confirmation dialog. */
    val pendingRefund: OrphanedPayment? = null,
    val submitting: Boolean = false,
)

sealed interface PaymentsEvent {
    data class Toast(val messageRes: Int) : PaymentsEvent
    data class Error(val error: MochiError) : PaymentsEvent
}

/**
 * Backs the Payments screen: the payments Stripe settled with no order or
 * subscription, mirroring `apps/staff/web/src/features/payments`. A refund
 * runs only after [askRefund] and [confirmRefund], and only an admin is
 * offered one; the Comptroller refuses anyone else.
 */
@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val repo: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentsUiState())
    val state: StateFlow<PaymentsUiState> = _state.asStateFlow()

    private val _events = Channel<PaymentsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        load()
    }

    /** Reads the first page again, as the error state's retry does. */
    fun load() {
        loadJob?.cancel()
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, load = LoadState.Idle, error = null)
            try {
                val resp = repo.listOrphanedPayments(page = 1, limit = PAGE_LIMIT)
                _state.value = _state.value.copy(
                    payments = resp.payments,
                    total = resp.total,
                    paging = Paging().advance(resp.payments.size, resp.total, resp.next),
                    isLoading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /** Reads the next page, from the list's foot or its retry. */
    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.load == LoadState.Loading || !s.paging.more) return
        moreJob = viewModelScope.launch {
            _state.value = s.copy(load = LoadState.Loading)
            try {
                val resp = repo.listOrphanedPayments(page = s.paging.page, limit = PAGE_LIMIT, after = s.paging.cursor)
                _state.value = _state.value.copy(
                    payments = _state.value.payments.append(resp.payments) { payment -> payment.id },
                    total = resp.total,
                    paging = s.paging.advance(resp.payments.size, resp.total, resp.next),
                    load = LoadState.Idle,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
            }
        }
    }

    fun askRefund(payment: OrphanedPayment) {
        _state.value = _state.value.copy(pendingRefund = payment)
    }

    fun cancelRefund() {
        if (_state.value.submitting) return
        _state.value = _state.value.copy(pendingRefund = null)
    }

    fun confirmRefund() {
        val target = _state.value.pendingRefund ?: return
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            try {
                repo.refundPayment(target.id)
                _state.value = _state.value.copy(
                    payments = _state.value.payments.map { row ->
                        if (row.id == target.id) row.copy(refunded = true) else row
                    },
                    pendingRefund = null,
                    submitting = false,
                )
                _events.send(PaymentsEvent.Toast(R.string.staff_payments_toast_refunded))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.send(PaymentsEvent.Error(e.toMochiError()))
            }
        }
    }

    companion object {
        private const val PAGE_LIMIT = 20
    }
}
