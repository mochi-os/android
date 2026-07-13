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

/**
 * UI state for [NotificationPreferencesScreen]. [enabled] is the set of
 * topic keys the user has not muted — defaults to every topic in
 * [MarketNotificationTopics.ALL] so a fresh install starts fully opted
 * in. [topics] is the canonical ordered list of every key the user can
 * mute (the same dotted-component strings declared in
 * `apps/market/labels/en.conf` under `notifications.topic.*`).
 */
data class NotificationPreferencesUiState(
    val isLoading: Boolean = true,
    val topics: List<String> = MarketNotificationTopics.ALL,
    val enabled: Set<String> = MarketNotificationTopics.ALL.toSet(),
)

/**
 * ViewModel for the per-topic notification mute screen.
 *
 * As of 2026-05-19 the Comptroller / notifications app doesn't expose
 * a market-side `setTopicMute` API: the notifications app stores topic
 * preferences in its own per-user DB, and Mochi forbids cross-app HTTP
 * requests from inside a Starlark app's frontend. Until a wrapper
 * lands on the market app's API surface, this ViewModel persists the
 * user's choices into a local DataStore via
 * [NotificationPreferencesStore]. Inbound notifications are filtered
 * by topic client-side at display time so the toggles still have a
 * visible effect.
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
