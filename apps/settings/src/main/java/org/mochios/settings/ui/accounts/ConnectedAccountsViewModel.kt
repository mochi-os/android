// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.accounts

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
import org.mochios.settings.api.ConnectedAccount
import org.mochios.settings.api.ConnectedAccountsApi
import org.mochios.settings.api.Provider
import retrofit2.Response
import javax.inject.Inject

data class ConnectedAccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<ConnectedAccount> = emptyList(),
    val providers: List<Provider> = emptyList(),
    val error: MochiError? = null,
)

@HiltViewModel
class ConnectedAccountsViewModel @Inject constructor(
    private val api: ConnectedAccountsApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectedAccountsUiState())
    val uiState: StateFlow<ConnectedAccountsUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val provs = api.providers().bodyOrThrow()
                val accs = api.list().bodyOrThrow()
                _uiState.value = ConnectedAccountsUiState(
                    isLoading = false,
                    providers = provs,
                    accounts = accs,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun addAccount(type: String, fields: Map<String, String>) = mutate {
        val payload = HashMap<String, String>(fields.size + 1)
        payload["type"] = type
        for ((k, v) in fields) if (v.isNotBlank()) payload[k] = v
        api.add(payload).bodyOrThrow()
    }

    fun remove(id: String) = mutate { api.remove(id).bodyOrThrow() }

    fun update(id: String, fields: Map<String, String>) = mutate {
        val payload = HashMap<String, String>(fields.size + 1)
        payload["id"] = id
        payload.putAll(fields)
        api.update(payload).bodyOrThrow()
    }

    fun toggleNotifyDefault(id: String, enabled: Boolean) = mutate {
        api.update(mapOf("id" to id, "enabled" to if (enabled) "1" else "0")).bodyOrThrow()
    }

    fun setAiDefault(id: String) = mutate {
        api.setDefault(account = id, type = "ai").bodyOrThrow()
    }

    fun clearAiDefault(id: String) = mutate {
        api.setDefault(account = id, type = "").bodyOrThrow()
    }

    fun verify(id: String, code: String) {
        viewModelScope.launch {
            try {
                val resp = api.verify(id = id, code = code).bodyOrThrow()
                val ok = resp["ok"] == true || resp["verified"] == true
                _toasts.emit(if (ok) "Account verified" else "Invalid verification code")
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun resend(id: String) {
        viewModelScope.launch {
            try {
                api.verify(id = id, code = null).bodyOrThrow()
                _toasts.emit("Verification code sent")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun test(id: String) {
        viewModelScope.launch {
            try {
                val result = api.test(id).bodyOrThrow()
                _toasts.emit(result.message.ifBlank { if (result.success) "Test sent" else "Test failed" })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        @Suppress("UNCHECKED_CAST")
        return body() ?: (Unit as T)
    }
}
