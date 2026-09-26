// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.NaturalCompare
import org.mochios.people.model.DeviceToken
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/** The server rejects a device name longer than this. */
const val DEVICE_NAME_MAXIMUM = 100

/**
 * The connect-a-device screen. [token] is the new device's password, held
 * only here and dropped when the screen goes: the server never shows it
 * again. [server] and [address] are what a CardDAV client is given.
 */
data class ConnectDeviceUiState(
    val server: String = "",
    val address: String = "",
    val tokens: List<DeviceToken> = emptyList(),
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    val name: String = "",
    val isCreating: Boolean = false,
    val token: String? = null,
    /** The username to enter beside [token]: the account's address. */
    val username: String = "",
    val deleting: DeviceToken? = null,
    val isDeleting: Boolean = false,
)

sealed class ConnectDeviceEvent {
    data class Failed(val error: MochiError, val creating: Boolean) : ConnectDeviceEvent()
    object Deleted : ConnectDeviceEvent()
}

@HiltViewModel
class ConnectDeviceViewModel @Inject constructor(
    private val repository: PeopleRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectDeviceUiState())
    val uiState: StateFlow<ConnectDeviceUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConnectDeviceEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ConnectDeviceEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val server = sessionManager.serverUrl.first().trimEnd('/')
            _uiState.value = _uiState.value.copy(server = server, address = "$server/people/carddav/")
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tokens = repository.listTokens().sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(isLoading = false, tokens = tokens)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun setName(name: String) {
        _uiState.value = _uiState.value.copy(name = name.take(DEVICE_NAME_MAXIMUM))
    }

    fun create() {
        val name = _uiState.value.name.trim()
        if (name.isBlank() || _uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            try {
                val created = repository.createToken(name)
                _uiState.value = _uiState.value.copy(isCreating = false, token = created.token, username = created.username, name = "")
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false)
                _events.tryEmit(ConnectDeviceEvent.Failed(e.toMochiError(), creating = true))
            }
        }
    }

    /** The password has been saved elsewhere; forget it. */
    fun done() {
        _uiState.value = _uiState.value.copy(token = null)
    }

    fun requestDelete(token: DeviceToken) {
        _uiState.value = _uiState.value.copy(deleting = token)
    }

    fun cancelDelete() {
        if (_uiState.value.isDeleting) return
        _uiState.value = _uiState.value.copy(deleting = null)
    }

    fun confirmDelete() {
        val target = _uiState.value.deleting ?: return
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deleteToken(target.hash)
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    deleting = null,
                    tokens = _uiState.value.tokens.filterNot { it.hash == target.hash },
                )
                _events.tryEmit(ConnectDeviceEvent.Deleted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDeleting = false, deleting = null)
                _events.tryEmit(ConnectDeviceEvent.Failed(e.toMochiError(), creating = false))
            }
        }
    }
}
