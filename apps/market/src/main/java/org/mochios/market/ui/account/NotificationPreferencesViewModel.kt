// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.market.lib.MarketNotificationTopics
import org.mochios.market.lib.NotificationPreferencesStore
import javax.inject.Inject

data class NotificationPreferencesUiState(
    val isLoading: Boolean = true,
    val topics: List<String> = MarketNotificationTopics.ALL,
    val enabled: Set<String> = MarketNotificationTopics.ALL.toSet(),
)

/**
 * Persists topic mutes to a local DataStore only: the notifications app exposes
 * no market-side API for them, so inbound notifications are filtered
 * client-side.
 */
@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val store: NotificationPreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationPreferencesUiState())
    val state: StateFlow<NotificationPreferencesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // The DataStore Flow emits on every write so a second
            // tab/preference panel writing to the same store will
            // propagate here automatically.
            store.observe().collect { muted ->
                val enabled = MarketNotificationTopics.ALL.filter { it !in muted }.toSet()
                _state.value = _state.value.copy(
                    isLoading = false,
                    enabled = enabled,
                )
            }
        }
    }

    fun setTopicEnabled(topic: String, enabled: Boolean) {
        viewModelScope.launch {
            store.setEnabled(topic, enabled)
        }
    }
}
