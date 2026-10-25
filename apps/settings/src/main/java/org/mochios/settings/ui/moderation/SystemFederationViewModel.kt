// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapRaw
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.api.ModerationApi
import org.mochios.settings.api.SystemHosts
import javax.inject.Inject

/** Each network's servers by name, an XMPP link by server then by this server's domain. */
internal fun hostsOrdered(hosts: SystemHosts): SystemHosts = hosts.copy(
    fediverse = hosts.fediverse.sortedWith(compareBy(NaturalCompare) { it.host }),
    matrix = hosts.matrix.sortedWith(compareBy(NaturalCompare) { it.server }),
    xmpp = hosts.xmpp.copy(
        links = hosts.xmpp.links.sortedWith(
            compareBy<org.mochios.settings.api.XmppLink, String>(NaturalCompare) { it.server }.thenBy(NaturalCompare) { it.domain },
        ),
    ),
)

data class SystemFederationUiState(
    val isLoading: Boolean = true,
    val hosts: SystemHosts? = null,
    val error: MochiError? = null,
)

/**
 * What the server knows of the servers it talks to (the federation plan,
 * phase 40), read again every ten seconds while the screen is open, as the
 * web page does.
 */
@HiltViewModel
class SystemFederationViewModel @Inject constructor(
    private val api: ModerationApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemFederationUiState())
    val uiState: StateFlow<SystemFederationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                load()
                delay(10_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            load()
        }
    }

    private suspend fun load() {
        try {
            val answer = api.hosts().unwrapRaw()
            _uiState.value = SystemFederationUiState(isLoading = false, hosts = hostsOrdered(answer))
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
        }
    }
}
