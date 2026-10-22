// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.NetworkInfo
import org.mochios.settings.api.NetworksApi
import org.mochios.settings.api.SystemNetworks
import org.mochios.settings.api.PeerEntry
import org.mochios.settings.api.ServerCounts
import org.mochios.settings.api.SystemStatusApi
import org.mochios.settings.api.SystemUpdateInfo
import retrofit2.Response
import javax.inject.Inject
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController

data class SystemStatusUiState(
    val isLoading: Boolean = true,
    val isInstalling: Boolean = false,
    val serverVersion: String = "",
    val serverStarted: Long = 0,
    val serverPeerId: String = "",
    val serverFingerprint: String = "",
    val update: SystemUpdateInfo? = null,
    val peers: List<PeerEntry> = emptyList(),
    val network: NetworkInfo? = null,
    val counts: ServerCounts? = null,
    /** The other networks' states, shown only while the server offers them. */
    val networks: SystemNetworks? = null,
    /** The Matrix domain whose server key is being replaced. */
    val replacing: String? = null,
    val error: MochiError? = null,
    val installError: MochiError? = null,
)

@HiltViewModel
class SystemStatusViewModel @Inject constructor(
    private val api: SystemStatusApi,
    private val networksApi: NetworksApi,
    stepUpClient: SettingsStepUpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemStatusUiState())
    val uiState: StateFlow<SystemStatusUiState> = _uiState.asStateFlow()

    /** Step-up gate for the administrator mutations: a stolen session must
     *  re-verify a login factor before it can change the server. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.value = _uiState.value.copy(installError = e.toMochiError()) },
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Outcomes to show the user once. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val settingsData = api.listSettings().unwrapRaw()
                val version = settingsData.settings.firstOrNull { it.name == "server_version" }?.value.orEmpty()
                val started = settingsData.settings.firstOrNull { it.name == "server_started" }?.value
                    ?.toLongOrNull() ?: 0L
                // Update is optional — failures here shouldn't blank the page.
                val update = try {
                    api.getUpdate().bodyOrNull()
                } catch (_: Exception) {
                    null
                }
                // Peers / network / counts are optional for the same reason
                // (older servers don't expose the endpoint).
                val peersData = try {
                    api.getPeers().bodyOrNull()
                } catch (_: Exception) {
                    null
                }
                // The other networks are optional too: a server before them
                // has no such endpoint.
                val networks = try {
                    networksApi.system().bodyOrNull()
                } catch (_: Exception) {
                    null
                }
                _uiState.value = SystemStatusUiState(
                    isLoading = false,
                    networks = networks,
                    serverVersion = version,
                    serverStarted = started,
                    serverPeerId = settingsData.server.id,
                    serverFingerprint = settingsData.server.fingerprint,
                    update = update,
                    peers = (peersData?.peers ?: emptyList())
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.fingerprint }.ifBlank { it.peer } }),
                    network = peersData?.network,
                    counts = peersData?.counts,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun installUpdate() {
        stepUp.request { token ->
            _uiState.value = _uiState.value.copy(isInstalling = true, installError = null)
            try {
                api.installUpdate(token).unwrapEmpty()
                // Re-fetch update info; pending field will surface "Installing X…".
                val update = try {
                    api.getUpdate().bodyOrNull()
                } catch (_: Exception) {
                    _uiState.value.update
                }
                _uiState.value = _uiState.value.copy(isInstalling = false, update = update)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isInstalling = false, installError = e.toMochiError())
            }
        }
    }

    /**
     * Replace a Matrix domain's signing key: other servers fetch the new one,
     * and what the old one signed stays valid. Step-up gated, as on the web.
     */
    fun replaceServerKey(domain: String) {
        stepUp.request { token ->
            _uiState.value = _uiState.value.copy(replacing = domain)
            try {
                networksApi.replaceServerKey(domain, token).unwrapEmpty()
                _messages.emit(AppContext.get().getString(R.string.system_status_key_replaced))
                val networks = try {
                    networksApi.system().bodyOrNull()
                } catch (_: Exception) {
                    _uiState.value.networks
                }
                _uiState.value = _uiState.value.copy(replacing = null, networks = networks)
            } catch (e: Exception) {
                val error = e.toMochiError()
                _uiState.value = _uiState.value.copy(replacing = null)
                _messages.emit(
                    if (error is MochiError.Unknown) {
                        error.message ?: AppContext.get().getString(R.string.system_status_key_failed)
                    } else {
                        error.userMessage()
                    },
                )
            }
        }
    }

    private fun <T> Response<T>.bodyOrNull(): T? = if (isSuccessful) body() else null
}
