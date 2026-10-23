// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapRaw
import org.mochios.settings.api.OtherToken
import org.mochios.settings.api.SessionsApi
import org.mochios.settings.api.Session
import javax.inject.Inject

data class SessionsUiState(
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    /** Sorted accessed-desc; the server flags the caller's own session. */
    val sessions: List<Session> = emptyList(),
    /** Sign-ins by other networks' apps and minted passwords, most recently used first. */
    val signins: List<OtherToken> = emptyList(),
    val passwords: List<OtherToken> = emptyList(),
    /** The session or token being revoked; its confirmation stays open until the server answers. */
    val revoking: String? = null,
)

/** Tokens most recently used first, then most recently made. */
internal fun recentFirst(tokens: List<OtherToken>): List<OtherToken> =
    tokens.sortedWith(compareByDescending<OtherToken> { it.used }.thenByDescending { it.created })

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val api: SessionsApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val answer = api.listSessions().unwrapRaw()
                _uiState.value = SessionsUiState(
                    isLoading = false,
                    sessions = answer.sessions.sortedByDescending { it.accessed },
                    signins = recentFirst(answer.signins),
                    passwords = recentFirst(answer.passwords),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun revoke(id: String, onSuccess: () -> Unit) {
        if (_uiState.value.revoking != null) return
        _uiState.value = _uiState.value.copy(revoking = id)
        viewModelScope.launch {
            try {
                api.revokeSession(id).unwrapRaw()
                _uiState.value = _uiState.value.copy(revoking = null)
                onSuccess()
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(revoking = null, error = e.toMochiError())
            }
        }
    }

    /** Revoke a sign-in by another app or a minted password. */
    fun revokeToken(hash: String, onSuccess: () -> Unit) {
        if (_uiState.value.revoking != null) return
        _uiState.value = _uiState.value.copy(revoking = hash)
        viewModelScope.launch {
            try {
                api.revokeToken(hash).unwrapRaw()
                _uiState.value = _uiState.value.copy(revoking = null)
                onSuccess()
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(revoking = null, error = e.toMochiError())
            }
        }
    }

    /** Dismiss a surfaced error once the screen has shown it. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
