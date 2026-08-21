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
import javax.inject.Inject

/**
 * Home menu gating: System is administrator-only, Domains needs administrator
 * or a delegation (web's `useFilteredSidebarData`). Both come from the domains
 * endpoint.
 */
data class SettingsHomeUiState(
    val isAdmin: Boolean = false,
    val hasDomainAccess: Boolean = false,
)

@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    private val domainsApi: DomainsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsHomeUiState())
    val state: StateFlow<SettingsHomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val resp = domainsApi.getDomains()
                val data = resp.body()
                if (resp.isSuccessful && data != null) {
                    _state.value = SettingsHomeUiState(
                        isAdmin = data.admin,
                        hasDomainAccess = data.admin || (data.delegations?.isNotEmpty() == true),
                    )
                }
            }
        }
    }
}
