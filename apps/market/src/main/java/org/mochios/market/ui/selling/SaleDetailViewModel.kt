// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.OrderDetailResponse
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class SaleDetailUiState(
    val order: OrderDetailResponse? = null,
    val audit: List<AuditEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: MochiError? = null,

    /** True while one of the seller-action mutations is in flight. */
    val shipSubmitting: Boolean = false,
    val refundSubmitting: Boolean = false,
    val disputeSubmitting: Boolean = false,
    val reviewResponseSubmitting: Boolean = false,

    val shipError: String? = null,
    val refundError: String? = null,
    val disputeError: String? = null,
    val reviewError: String? = null,
)

sealed class SaleDetailEvent {
    data class Toast(val message: String) : SaleDetailEvent()
}

@HiltViewModel
class SaleDetailViewModel @Inject constructor(
    private val repo: MarketRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orderId: String = savedStateHandle.get<String>("orderId").orEmpty()

    private val _state = MutableStateFlow(SaleDetailUiState())
    val state: StateFlow<SaleDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SaleDetailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SaleDetailEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun load() {
        if (orderId.isEmpty()) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = MochiError.NotFoundError("Order id is missing"),
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val detail = repo.getOrder(orderId)
                val audit = try {
                    repo.auditObject(kind = "order", objectId = orderId).audit
                } catch (_: Exception) {
                    emptyList()
                }
                _state.value = _state.value.copy(
                    order = detail,
                    audit = audit,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun shipOrder(carrier: String, tracking: String, url: String, fallback: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(shipSubmitting = true, shipError = null)
            try {
                val updated = repo.shipOrder(
                    id = orderId,
                    carrier = carrier.ifBlank { null },
                    tracking = tracking.ifBlank { null },
                    url = url.ifBlank { null },
                )
                val current = _state.value.order ?: return@launch
                _state.value = _state.value.copy(
                    order = current.copy(order = updated),
                    shipSubmitting = false,
                )
                _events.emit(SaleDetailEvent.Toast(fallback))
            } catch (e: Exception) {
                val err = e.toMochiError()
                _state.value = _state.value.copy(
                    shipSubmitting = false,
                    shipError = err.message() ?: fallback,
                )
            }
        }
    }

    fun refundOrder(amount: Long?, reason: String, description: String, fallback: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(refundSubmitting = true, refundError = null)
            try {
                val result = repo.refundOrder(
                    id = orderId,
                    amount = amount,
                    reason = (reason + if (description.isNotBlank()) ": $description" else "")
                        .takeIf { it.isNotBlank() },
                )
                val current = _state.value.order ?: return@launch
                _state.value = _state.value.copy(
                    order = current.copy(
                        order = result.order,
                        dispute = result.dispute ?: current.dispute,
                    ),
                    refundSubmitting = false,
                )
                // Description text is appended onto `reason` because the
                // comptroller surface accepts a single combined reason field.
                // No need to thread `description` separately into the repo.
            } catch (e: Exception) {
                val err = e.toMochiError()
                _state.value = _state.value.copy(
                    refundSubmitting = false,
                    refundError = err.message() ?: fallback,
                )
            }
        }
    }

    fun respondToDispute(body: String, fallback: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(disputeSubmitting = true, disputeError = null)
            try {
                val current = _state.value.order ?: return@launch
                val dispute = current.dispute ?: return@launch
                val updated = repo.respondToDispute(dispute.id, body)
                _state.value = _state.value.copy(
                    order = current.copy(dispute = updated),
                    disputeSubmitting = false,
                )
            } catch (e: Exception) {
                val err = e.toMochiError()
                _state.value = _state.value.copy(
                    disputeSubmitting = false,
                    disputeError = err.message() ?: fallback,
                )
            }
        }
    }

    fun respondToReview(response: String, fallback: String) {
        viewModelScope.launch {
            val current = _state.value.order ?: return@launch
            val review = current.review ?: return@launch
            _state.value = _state.value.copy(reviewResponseSubmitting = true, reviewError = null)
            try {
                val updated = repo.respondToReview(review.id, response)
                _state.value = _state.value.copy(
                    order = current.copy(review = updated),
                    reviewResponseSubmitting = false,
                )
            } catch (e: Exception) {
                val err = e.toMochiError()
                _state.value = _state.value.copy(
                    reviewResponseSubmitting = false,
                    reviewError = err.message() ?: fallback,
                )
            }
        }
    }

    private fun MochiError.message(): String? = when (this) {
        is MochiError.AuthError -> message
        is MochiError.ForbiddenError -> message
        is MochiError.NotFoundError -> message
        is MochiError.ServerError -> message
        is MochiError.Unknown -> message
        else -> null
    }
}
