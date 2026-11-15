// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.settings.api.DomainsApi
import org.mochios.settings.api.NetworksApi
import javax.inject.Inject

/**
 * Home menu gating: System is administrator-only, Domains needs administrator
 * or a delegation (web's `useFilteredSidebarData`); both come from the domains
 * endpoint. Networks, and the owner's Handles, need a server that offers other
 * networks, which networks/offered says.
 */
data class SettingsHomeUiState(
    val isAdmin: Boolean = false,
    val hasDomainAccess: Boolean = false,
    val hasNetworks: Boolean = false,
)

@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    private val domainsApi: DomainsApi,
    private val networksApi: NetworksApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsHomeUiState())
    val state: StateFlow<SettingsHomeUiState> = _state.asStateFlow()

    private var resumedBefore = false

    init { refresh() }

    /**
     * The screen's ON_RESUME. The first arrives as the screen opens, while
     * init is already fetching, so only a later one - a return to the home
     * screen - refreshes.
     */
    fun onScreenResumed() {
        if (!resumedBefore) {
            resumedBefore = true
            return
        }
        refresh()
    }

    /** Set once the server has answered that it predates networks/offered. */
    private var older = false

    /** Re-fetched on every return to the home screen: a fetch that failed at
     *  start-up used to hide the System and Domains groups for the whole
     *  session, with nothing to retry it. The two reads go out together. */
    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val resp = domainsApi.getDomains()
                val data = resp.body()
                if (resp.isSuccessful && data != null) {
                    _state.value = _state.value.copy(
                        isAdmin = data.admin,
                        hasDomainAccess = data.admin || (data.delegations?.isNotEmpty() == true),
                    )
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                offered()?.let { offered -> _state.value = _state.value.copy(hasNetworks = offered) }
            }
        }
    }

    /**
     * Whether the server offers other networks, or null when it did not say.
     * Read from the one flag's own action; a server from before it answers
     * 404, and is asked for the whole Networks page instead, which carries the
     * same flag.
     */
    private suspend fun offered(): Boolean? {
        if (!older) {
            val resp = networksApi.offered()
            if (resp.code() != 404) return resp.body()?.takeIf { resp.isSuccessful }?.offered
            older = true
        }
        val resp = networksApi.data()
        return resp.body()?.takeIf { resp.isSuccessful }?.offered
    }
}
