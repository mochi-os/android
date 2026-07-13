// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * Backing model for the staff Configuration screen. Mirrors web's
 * `apps/staff/web/src/features/config/config-page.tsx` shape — one map per
 * "server-side value" and one map per "local edit", with a per-key
 * `submitting` flag so each field can save independently.
 *
 * The two moderation thresholds (`threshold_low` / `threshold_high`) are
 * stored alongside the free-form configuration values to keep the screen
 * single-source-of-truth — the Comptroller serves them via separate
 * endpoints (`moderation/thresholds` and `moderation/set_thresholds`), but
 * the UI treats them like every other key.
 */
data class ConfigUiState(
    /** Authoritative values from the server (keyed by config key). */
    val server: Map<String, String> = emptyMap(),
    /** Local edits the user has typed but not yet saved. */
    val local: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val saving: Map<String, Boolean> = emptyMap(),
)

sealed interface ConfigEvent {
    data class Toast(val messageRes: Int) : ConfigEvent
    data class Error(val error: MochiError) : ConfigEvent
}

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repo: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    private val _events = Channel<ConfigEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val cfg = repo.getConfig()
                val thresholds = repo.getModerationThresholds()
                val merged = buildMap {
                    cfg.forEach { put(it.key, it.value) }
                    put(KEY_LOW, thresholds.low.toString())
                    put(KEY_HIGH, thresholds.high.toString())
                }
                _state.value = _state.value.copy(
                    server = merged,
                    local = merged,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun setLocal(key: String, value: String) {
        _state.value = _state.value.copy(local = _state.value.local + (key to value))
    }

    fun save(key: String) {
        val value = _state.value.local[key].orEmpty()
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = _state.value.saving + (key to true))
            try {
                when (key) {
                    KEY_LOW -> {
                        val updated = repo.setModerationThresholds(low = value.toIntOrNull())
                        _state.value = _state.value.copy(
                            server = _state.value.server +
                                (KEY_LOW to updated.low.toString()) +
                                (KEY_HIGH to updated.high.toString()),
                            local = _state.value.local +
                                (KEY_LOW to updated.low.toString()) +
                                (KEY_HIGH to updated.high.toString()),
                        )
                    }
                    KEY_HIGH -> {
                        val updated = repo.setModerationThresholds(high = value.toIntOrNull())
                        _state.value = _state.value.copy(
                            server = _state.value.server +
                                (KEY_LOW to updated.low.toString()) +
                                (KEY_HIGH to updated.high.toString()),
                            local = _state.value.local +
                                (KEY_LOW to updated.low.toString()) +
                                (KEY_HIGH to updated.high.toString()),
                        )
                    }
                    else -> {
                        repo.setConfig(key, value)
                        _state.value = _state.value.copy(server = _state.value.server + (key to value))
                    }
                }
                _events.send(ConfigEvent.Toast(org.mochios.staff.R.string.staff_config_toast_saved))
            } catch (e: Exception) {
                _events.send(ConfigEvent.Error(e.toMochiError()))
            } finally {
                _state.value = _state.value.copy(saving = _state.value.saving - key)
            }
        }
    }

    companion object {
        const val KEY_LOW = "threshold_low"
        const val KEY_HIGH = "threshold_high"
        const val KEY_FEE = "fee_percent"
        const val KEY_REVIEW_WINDOW = "review_timeout_days"
        const val KEY_STRIPE_PUBLISHABLE = "stripe_publishable_key"
        const val KEY_STRIPE_SECRET = "stripe_secret_key"
        const val KEY_STRIPE_WEBHOOK = "stripe_webhook_secret"
        const val KEY_STRIPE_OAUTH = "stripe_oauth_client_id"
    }
}
