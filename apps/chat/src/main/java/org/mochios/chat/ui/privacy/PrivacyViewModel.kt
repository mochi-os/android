// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.chat.model.ChatPreferences
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

/**
 * Who may start a chat with the user, and what the user shares with the
 * people they chat with. [loaded] is null until the settings have been read:
 * a failed load must not leave the controls on defaults that look like the
 * user's own, and a save would then overwrite what they really have.
 */
data class PrivacyUiState(
    val value: ChatPreferences = ChatPreferences(),
    val loaded: ChatPreferences? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** The settings could not be read; nothing can be saved. */
    val failure: MochiError? = null,
    /** A save was refused; the dialog stays open. */
    val error: MochiError? = null,
    val saved: Boolean = false,
) {
    val changed: Boolean get() = loaded != null && value != loaded
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    /**
     * Read the settings afresh, as the dialog does each time it opens: the
     * model outlives the dialog, and what it read last time, or an edit left
     * unsaved, may no longer be what the user has.
     */
    fun load() {
        viewModelScope.launch {
            _uiState.value = PrivacyUiState(isLoading = true)
            try {
                val preferences = repository.getPreferences()
                _uiState.value = _uiState.value.copy(value = preferences, loaded = preferences, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, failure = e.toMochiError())
            }
        }
    }

    fun change(value: ChatPreferences) {
        _uiState.value = _uiState.value.copy(value = value)
    }

    fun save() {
        val state = _uiState.value
        val loaded = state.loaded ?: return
        if (!state.changed) {
            _uiState.value = state.copy(saved = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null)
            try {
                repository.setPreferences(loaded, state.value)
                _uiState.value = _uiState.value.copy(isSaving = false, loaded = state.value, saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    fun consumeSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }
}
