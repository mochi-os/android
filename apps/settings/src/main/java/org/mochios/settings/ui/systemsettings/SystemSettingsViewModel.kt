// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemsettings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.files.FileStore
import org.mochios.settings.R
import org.mochios.settings.api.SystemSetting
import org.mochios.settings.api.SystemSettingsApi
import org.mochios.settings.api.SystemTheme
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController
import javax.inject.Inject

data class SystemSettingsUiState(
    val isLoading: Boolean = true,
    val settings: List<SystemSetting> = emptyList(),
    val themes: List<SystemTheme> = emptyList(),
    val savingName: String? = null,
    val error: MochiError? = null,
    /**
     * Values asked for and not yet saved, by setting name. A control that
     * saves on the tap shows its pending value, and shows the stored one again
     * once the step-up is cancelled or the server refuses.
     */
    val pending: Map<String, String> = emptyMap(),
)

@HiltViewModel
class SystemSettingsViewModel @Inject constructor(
    private val api: SystemSettingsApi,
    stepUpClient: SettingsStepUpClient,
    private val files: FileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemSettingsUiState())
    val uiState: StateFlow<SystemSettingsUiState> = _uiState.asStateFlow()

    /** Step-up gate for the administrator mutations: a stolen session must
     *  re-verify a login factor before it can change the server. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.update { it.copy(error = e.toMochiError()) } },
    )

    init { refresh() }

    /** Read the settings. The spinner is for the first read only: a read after
     *  a save keeps the list, and the reader's place in it, on screen. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.settings.isEmpty(), error = null) }
            try {
                val data = api.list().unwrapRaw()
                _uiState.update {
                    it.copy(isLoading = false, settings = data.settings, themes = data.themes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.toMochiError()) }
            }
        }
    }

    fun setValue(name: String, value: String) {
        _uiState.update { it.copy(pending = it.pending + (name to value)) }
        stepUp.request(onCancel = { forget(name, value) }) { token ->
            _uiState.update { it.copy(savingName = name, error = null) }
            try {
                // unwrapEmpty carries the server's translated refusal into
                // the error, where a bare status code told the user nothing.
                api.set(name, value, token).unwrapEmpty()
                // Apply locally for snappy UI; refresh to pick up any server-side
                // normalisation (e.g. trimmed whitespace, default fallback).
                _uiState.update { state ->
                    state.copy(
                        settings = state.settings.map { if (it.name == name) it.copy(value = value) else it },
                        savingName = null,
                        pending = state.pending.forgetting(name, value),
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                forget(name, value)
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(savingName = null, error = e.toMochiError(), pending = it.pending.forgetting(name, value))
                }
            }
        }
    }

    /** Set a file setting to the text of the document at [uri], as the web
     *  uploads one. */
    fun upload(name: String, uri: Uri) {
        viewModelScope.launch {
            val text = files.readText(uri)
            if (text == null) {
                _uiState.update { it.copy(error = MochiError.Local(R.string.system_settings_read_failed)) }
            } else {
                setValue(name, text)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun forget(name: String, value: String) {
        _uiState.update { it.copy(pending = it.pending.forgetting(name, value)) }
    }
}

/** [this] without [name]'s pending value, unless a later change replaced it. */
private fun Map<String, String>.forgetting(name: String, value: String): Map<String, String> =
    if (this[name] == value) this - name else this
