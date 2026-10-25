// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.moderation

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
import org.mochios.android.api.unwrapRaw
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.AppContext
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.R
import org.mochios.settings.api.ModerationApi
import org.mochios.settings.api.SystemBlocks
import javax.inject.Inject

/** One block: what it names, as the server keeps it, whether a person, and since when. */
data class BlockRow(val key: String, val person: Boolean, val created: Long) {
    /** An XMPP address is shown without its scheme. */
    val label: String get() = key.removePrefix("xmpp:")
}

/** Servers and people together, by what they name. */
internal fun blockRows(blocks: SystemBlocks): List<BlockRow> =
    (blocks.domains.map { BlockRow(it.domain, false, it.created) } + blocks.people.map { BlockRow(it.actor, true, it.created) })
        .sortedWith(compareBy(NaturalCompare) { it.key })

data class SystemBlocksUiState(
    val isLoading: Boolean = true,
    val rows: List<BlockRow> = emptyList(),
    val error: MochiError? = null,
    val busy: String? = null,
)

/**
 * The owner's blocks for the whole server (the federation plan, phase 40):
 * domains, and people by their address on their network.
 */
@HiltViewModel
class SystemBlocksViewModel @Inject constructor(
    private val api: ModerationApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemBlocksUiState())
    val uiState: StateFlow<SystemBlocksUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val answer = api.blocks().unwrapRaw()
                _uiState.value = _uiState.value.copy(isLoading = false, rows = blockRows(answer), error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** Block what was typed; [onDone] runs once it is blocked, to clear the field. */
    fun block(target: String, onDone: () -> Unit = {}) {
        val value = target.trim()
        if (value.isEmpty()) return
        change("add", R.string.networks_blocked, onDone) { api.block(value).unwrapRaw() }
    }

    fun unblock(row: BlockRow) = change(row.key, R.string.networks_unblocked) { api.unblock(row.key).unwrapRaw() }

    private fun change(key: String, done: Int, after: () -> Unit = {}, block: suspend () -> SystemBlocks) {
        if (_uiState.value.busy != null) return
        _uiState.value = _uiState.value.copy(busy = key)
        viewModelScope.launch {
            try {
                val answer = block()
                _uiState.value = _uiState.value.copy(rows = blockRows(answer))
                _messages.emit(AppContext.get().getString(done))
                after()
            } catch (e: Exception) {
                val error = e.toMochiError()
                _messages.emit(
                    if (error is MochiError.Unknown) error.message ?: AppContext.get().getString(R.string.networks_block_failed) else error.userMessage(),
                )
            } finally {
                _uiState.value = _uiState.value.copy(busy = null)
            }
        }
    }
}
