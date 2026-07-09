// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.policy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

// Whom may start a chat with this user. Mirrors the web chat_policy setting.
data class ChatPolicyUiState(
    val policy: String = "friends",
    val loaded: String = "friends",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ChatPolicyViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatPolicyUiState())
    val uiState: StateFlow<ChatPolicyUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val policy = repository.getChatPolicy()
                _uiState.value = _uiState.value.copy(policy = policy, loaded = policy, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun select(policy: String) {
        _uiState.value = _uiState.value.copy(policy = policy)
    }

    fun save() {
        val state = _uiState.value
        if (state.policy == state.loaded) {
            _uiState.value = state.copy(saved = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)
            try {
                repository.setChatPolicy(state.policy)
                _uiState.value = _uiState.value.copy(isSaving = false, loaded = state.policy, saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }
}
