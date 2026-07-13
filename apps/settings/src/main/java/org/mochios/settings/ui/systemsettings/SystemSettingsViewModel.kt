// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.settings.api.SystemSetting
import org.mochios.settings.api.SystemSettingsApi
import retrofit2.Response
import javax.inject.Inject

data class SystemSettingsUiState(
    val isLoading: Boolean = true,
    val settings: List<SystemSetting> = emptyList(),
    val savingName: String? = null,
    val error: MochiError? = null,
)

@HiltViewModel
class SystemSettingsViewModel @Inject constructor(
    private val api: SystemSettingsApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemSettingsUiState())
    val uiState: StateFlow<SystemSettingsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.list().bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    settings = data.settings,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun setValue(name: String, value: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingName = name, error = null)
            try {
                val resp = api.set(name, value)
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code()}")
                // Apply locally for snappy UI; refresh to pick up any server-side
                // normalisation (e.g. trimmed whitespace, default fallback).
                _uiState.value = _uiState.value.copy(
                    settings = _uiState.value.settings.map {
                        if (it.name == name) it.copy(value = value) else it
                    },
                    savingName = null,
                )
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(savingName = null, error = e.toMochiError())
            }
        }
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }
}
