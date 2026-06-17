package org.mochios.staff.ui.disputes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.staff.model.Dispute
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEvent
import org.mochios.staff.ws.StaffEventsBus
import javax.inject.Inject

/**
 * UI state for [DisputesScreen]. Mirrors the same axes as the web
 * `DisputesPage`:
 *
 *   - `status` — one of `open` / `responded` / `reviewing` /
 *                `resolved_buyer` / `resolved_seller` / `escalated`,
 *                or null for any.
 *
 * Pagination is page-based; the repository's `listDisputes` accepts
 * `page` / `limit`.
 */
data class DisputesUiState(
    val status: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val disputes: List<Dispute> = emptyList(),
    val total: Long = 0,
    val error: MochiError? = null,
    val reviewDialog: Dispute? = null,
    val submitting: Boolean = false,
)

/** One-shot events emitted to the screen (toasts). */
sealed class DisputesEvent {
    /** Marker emitted on successful resolution — screen substitutes localised text. */
    object Resolved : DisputesEvent()

    /** Pre-localised error / validation message. */
    data class Toast(val message: String) : DisputesEvent()

    /** Validation: refund amount must be > 0. */
    object RefundMustBePositive : DisputesEvent()

    /** Validation: refund amount > order total. */
    object RefundExceedsTotal : DisputesEvent()
}

private const val PAGE_SIZE = 20

@HiltViewModel
class DisputesViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val eventsBus: StaffEventsBus,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DisputesUiState(
            status = savedStateHandle.get<String>(KEY_STATUS),
        ),
    )
    val state: StateFlow<DisputesUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DisputesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DisputesEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        reload()
        // Refresh whenever a new dispute / chargeback lands on the server.
        viewModelScope.launch {
            eventsBus.events
                .filter { it is StaffEvent.NewDispute }
                .collect { reload() }
        }
    }

    fun setStatus(status: String?) {
        if (_state.value.status == status) return
        _state.value = _state.value.copy(status = status)
        savedStateHandle[KEY_STATUS] = status
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val r = repository.listDisputes(
                    status = s.status,
                    page = 1,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    disputes = r.disputes,
                    total = r.total,
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
        val s = _state.value
        if (s.isLoadingMore || s.isLoading || s.disputes.size >= s.total) return
        val nextPage = (s.disputes.size / PAGE_SIZE) + 1
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val r = repository.listDisputes(
                    status = s.status,
                    page = nextPage,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoadingMore = false,
                    disputes = _state.value.disputes + r.disputes,
                    total = r.total,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    fun openReview(dispute: Dispute) {
        _state.value = _state.value.copy(reviewDialog = dispute)
    }

    fun dismissReview() {
        _state.value = _state.value.copy(reviewDialog = null, submitting = false)
    }

    /**
     * Review a dispute. `resolution` is `resolved_buyer` or
     * `resolved_seller`. `refundAmountMinor` is the optional partial
     * refund in minor currency units; null means "full refund of
     * dispute.total" (when resolving for the buyer). Validation:
     *   - amount > 0 (rejected via [DisputesEvent.RefundMustBePositive]),
     *   - amount <= dispute.total (rejected via [DisputesEvent.RefundExceedsTotal]).
     */
    fun reviewDispute(
        status: String,
        resolution: String,
        refundAmountMinor: Long?,
    ) {
        val current = _state.value.reviewDialog ?: return
        if (_state.value.submitting) return

        if (status == "resolved_buyer" && refundAmountMinor != null) {
            if (refundAmountMinor <= 0) {
                _events.tryEmit(DisputesEvent.RefundMustBePositive)
                return
            }
            if (refundAmountMinor > current.total) {
                _events.tryEmit(DisputesEvent.RefundExceedsTotal)
                return
            }
        }

        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            try {
                repository.reviewDispute(
                    id = current.id,
                    status = status,
                    resolution = resolution.takeIf { it.isNotBlank() },
                    refundAmount = refundAmountMinor,
                )
                val before = _state.value.disputes
                val after = before.filter { it.id != current.id }
                val deltaTotal = if (before.size != after.size) 1L else 0L
                _state.value = _state.value.copy(
                    submitting = false,
                    reviewDialog = null,
                    disputes = after,
                    total = (_state.value.total - deltaTotal).coerceAtLeast(0L),
                )
                _events.tryEmit(DisputesEvent.Resolved)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(DisputesEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }

    companion object {
        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_STATUS = "filter_status"
    }
}
