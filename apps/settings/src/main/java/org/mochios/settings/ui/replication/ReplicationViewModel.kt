// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.replication

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
import org.mochios.settings.api.ReplicationApi
import org.mochios.settings.api.ReplicationData
import org.mochios.settings.api.ReplicationHost
import org.mochios.settings.api.ReplicationLink
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController
import retrofit2.Response
import javax.inject.Inject

data class ReplicationUiState(
    val isLoading: Boolean = true,
    val links: List<ReplicationLink> = emptyList(),
    val hosts: List<ReplicationHost> = emptyList(),
    val username: String = "",
    val serverPeerId: String = "",
    val serverFingerprint: String = "",
    val error: MochiError? = null,
)

sealed class ReplicationEvent {
    data class Copied(val success: Boolean) : ReplicationEvent()

    /** The account was removed from this server; the screen signs out. */
    data object Left : ReplicationEvent()
}

@HiltViewModel
class ReplicationViewModel @Inject constructor(
    private val api: ReplicationApi,
    stepUpClient: SettingsStepUpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReplicationUiState())
    val uiState: StateFlow<ReplicationUiState> = _uiState.asStateFlow()

    /** Step-up gate: approving a link replicates the user's private keys to
     *  the peer, so the user re-verifies their login factor(s) first. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.value = _uiState.value.copy(error = e.toMochiError()) },
    )

    private val _events = MutableSharedFlow<ReplicationEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ReplicationEvent> = _events.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.getReplication().bodyOrThrow()
                _uiState.value = ReplicationUiState(
                    isLoading = false,
                    links = data.links,
                    hosts = data.hosts,
                    username = data.user.username,
                    serverPeerId = data.server.id,
                    serverFingerprint = data.server.fingerprint,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun approve(peer: String) = stepUp.request { token ->
        try {
            api.approveLink(peer, token).bodyOrThrow()
            refresh()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.toMochiError())
        }
    }
    fun deny(peer: String) = mutate { api.denyLink(peer).bodyOrThrow() }

    /** Advanced: forget an unreachable host. Step-up gated. */
    fun remove(peer: String) = stepUp.request { token ->
        try {
            api.removeHost(peer, token).bodyOrThrow()
            refresh()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.toMochiError())
        }
    }

    /** Remove the account from THIS server. Step-up gated; on success the
     *  screen signs out (this server's copy, and its sessions, are gone). */
    fun leave() = stepUp.request { token ->
        try {
            api.leave(token).bodyOrThrow()
            _events.emit(ReplicationEvent.Left)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.toMochiError())
        }
    }

    fun reportCopied(success: Boolean) {
        viewModelScope.launch { _events.emit(ReplicationEvent.Copied(success)) }
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
        return body() ?: throw RuntimeException("empty body")
    }
}
