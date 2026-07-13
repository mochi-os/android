// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

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
import org.mochios.android.api.userMessage
import org.mochios.market.model.AuditEvent
import org.mochios.market.model.OrderDetailResponse
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class PurchaseDetailUiState(
    val isLoading: Boolean = true,
    val detail: OrderDetailResponse? = null,
    val audit: List<AuditEvent> = emptyList(),
    val error: MochiError? = null,
    val submitting: Boolean = false,
)

sealed interface PurchaseDetailEvent {
    data class Toast(val message: String) : PurchaseDetailEvent
    data class OpenUrl(val url: String) : PurchaseDetailEvent
    data class DownloadAsset(val assetId: String) : PurchaseDetailEvent
}

@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MarketRepository,
) : ViewModel() {

    val orderId: String = savedStateHandle.get<String>("orderId").orEmpty()

    private val _uiState = MutableStateFlow(PurchaseDetailUiState())
    val uiState: StateFlow<PurchaseDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PurchaseDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PurchaseDetailEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val detail = repository.getOrder(orderId)
                val audit = runCatching {
                    repository.auditObject(kind = "order", objectId = orderId).audit
                }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    detail = detail,
                    audit = audit,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun confirmDelivery() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(submitting = true)
            try {
                val updated = repository.confirmOrder(orderId)
                val current = _uiState.value.detail
                _uiState.value = _uiState.value.copy(
                    submitting = false,
                    detail = current?.copy(order = updated),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(submitting = false)
                _events.tryEmit(PurchaseDetailEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }

    fun requestRefund(amount: Long, reason: String, description: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(submitting = true)
            try {
                // Buyer-initiated dispute → server treats as refund request.
                repository.disputeOrder(orderId, reason = reason, description = description)
                // Reload so the dispute card appears.
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(submitting = false)
                _events.tryEmit(PurchaseDetailEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }

    fun submitReview(rating: Int, body: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(submitting = true)
            try {
                val review = repository.createReview(orderId, rating, body)
                val current = _uiState.value.detail
                _uiState.value = _uiState.value.copy(
                    submitting = false,
                    detail = current?.copy(review = review, canReview = false),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(submitting = false)
                _events.tryEmit(PurchaseDetailEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }

    fun requestDownload(assetId: String) {
        _events.tryEmit(PurchaseDetailEvent.DownloadAsset(assetId))
    }
}
