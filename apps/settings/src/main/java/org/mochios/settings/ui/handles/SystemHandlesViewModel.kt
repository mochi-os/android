// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.handles

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
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.AppContext
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.R
import org.mochios.settings.api.Availability
import org.mochios.settings.api.NetworksApi
import org.mochios.settings.api.RegistryAccount
import org.mochios.settings.api.RegistryHandle
import javax.inject.Inject

data class SystemHandlesUiState(
    val isLoading: Boolean = true,
    /** By domain, then each handle with the names under it. */
    val handles: List<RegistryHandle> = emptyList(),
    val domains: List<String> = emptyList(),
    val error: MochiError? = null,
    val busy: String? = null,
)

/** Handles by domain, each handle followed by the names under it, by name. */
internal fun registryOrder(handles: List<RegistryHandle>): List<RegistryHandle> =
    handles.sortedWith(
        compareBy<RegistryHandle, String>(NaturalCompare) { it.domain }
            .thenBy(NaturalCompare) { it.parent.ifEmpty { it.name } }
            .thenBy(NaturalCompare) { it.name },
    )

/**
 * The owner's list of every handle with its holder (the federation plan,
 * phase 10), where the owner releases handles, takes names, the protected
 * ones included, and takes a hosted Bluesky account down or restores it.
 */
@HiltViewModel
class SystemHandlesViewModel @Inject constructor(
    private val api: NetworksApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemHandlesUiState())
    val uiState: StateFlow<SystemHandlesUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { refresh() }

    fun refresh(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val answer = api.registry().unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    handles = registryOrder(answer.handles),
                    domains = answer.domains,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    suspend fun available(name: String, domain: String): Availability? =
        runCatching { api.available(name, domain).unwrapRaw() }.getOrNull()

    /** Take a name, through the same reservation the user's own Networks screen makes. */
    fun reserve(name: String, domain: String, onReserved: () -> Unit) =
        change("reserve", R.string.networks_reserve_failed) {
            api.reserve(name, domain).unwrapEmpty()
            say(R.string.networks_reserved)
            onReserved()
        }

    fun release(handle: RegistryHandle) = change("${handle.domain}/${handle.name}", R.string.networks_release_failed) {
        api.releaseAny(handle.name, handle.domain).unwrapEmpty()
        say(R.string.networks_released)
    }

    /**
     * Taking a Bluesky account down keeps it and its records but serves
     * nothing of it and tells the relays; restoring undoes that.
     */
    fun takedown(account: RegistryAccount, down: Boolean) = change(account.id, R.string.networks_change_failed) {
        api.takedown(account.did, down).unwrapEmpty()
        say(if (down) R.string.handles_taken_down_done else R.string.handles_restored)
    }

    private fun change(key: String, fallback: Int, block: suspend () -> Unit) {
        if (_uiState.value.busy != null) return
        _uiState.value = _uiState.value.copy(busy = key)
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                val error = e.toMochiError()
                _messages.emit(
                    if (error is MochiError.Unknown) error.message ?: AppContext.get().getString(fallback) else error.userMessage(),
                )
            } finally {
                _uiState.value = _uiState.value.copy(busy = null)
            }
            refresh(quiet = true)
        }
    }

    private suspend fun say(id: Int) {
        _messages.emit(AppContext.get().getString(id))
    }
}
