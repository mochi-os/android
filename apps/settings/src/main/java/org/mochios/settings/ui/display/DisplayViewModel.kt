// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.i18n.ThemeInfo
import javax.inject.Inject

data class DisplayUiState(
    val isLoading: Boolean = true,
    /** Raw key → value map mirroring what the server stores. Blank == use default. */
    val values: Map<String, String> = emptyMap(),
    val themes: List<ThemeInfo> = emptyList(),
    val defaultThemeId: String? = null,
    val error: MochiError? = null,
    val isSaving: Boolean = false,
    /** A reset in flight; its confirmation stays open until it answers. */
    val isResetting: Boolean = false,
)

@HiltViewModel
class DisplayViewModel @Inject constructor(
    private val preferences: PreferencesManager,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisplayUiState())
    val uiState: StateFlow<DisplayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                preferences.refresh()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    values = preferences.rawPreferences(),
                    themes = preferences.availableThemes(),
                    defaultThemeId = preferences.defaultTheme(),
                )
                cacheActiveThemeAnchors()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /**
     * The pick shows at once and goes back, with the error, if the server
     * refuses: what shows after either answer is the server's, with any pick
     * still waiting over it.
     */
    fun set(key: String, value: String) {
        _uiState.value = _uiState.value.copy(
            values = _uiState.value.values + (key to value),
            isSaving = true,
            error = null,
        )
        viewModelScope.launch {
            try {
                preferences.setPreference(key, value)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    values = preferences.rawPreferences(),
                    themes = preferences.availableThemes(),
                    defaultThemeId = preferences.defaultTheme(),
                )
                cacheActiveThemeAnchors()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    values = preferences.rawPreferences(),
                    error = e.toMochiError(),
                )
                cacheActiveThemeAnchors()
            }
        }
    }

    /** Reset only the supplied keys to their server defaults. */
    fun reset(keys: List<String>, onSuccess: () -> Unit) {
        if (_uiState.value.isResetting) return
        _uiState.value = _uiState.value.copy(isResetting = true, error = null)
        viewModelScope.launch {
            try {
                preferences.resetKeys(keys)
                _uiState.value = _uiState.value.copy(
                    isResetting = false,
                    values = preferences.rawPreferences(),
                    themes = preferences.availableThemes(),
                    defaultThemeId = preferences.defaultTheme(),
                )
                cacheActiveThemeAnchors()
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isResetting = false, error = e.toMochiError())
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Writes the active theme's anchors to the session store, which
     * `MochiTheme` reads; saving the `theme` preference alone only tells the
     * server.
     */
    private suspend fun cacheActiveThemeAnchors() {
        val state = _uiState.value
        val activeId = state.values["theme"]?.takeIf { id -> id.isNotBlank() }
            ?: state.defaultThemeId
            ?: return
        val theme = state.themes.firstOrNull { candidate -> candidate.id == activeId } ?: return
        sessionManager.saveTheme(
            hue = theme.hue,
            chroma = theme.chroma,
            hueBg = theme.hueBg,
        )
    }
}
