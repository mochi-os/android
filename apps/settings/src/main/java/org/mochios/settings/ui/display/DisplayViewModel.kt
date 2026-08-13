// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun set(key: String, value: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                preferences.setPreference(key, value)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    values = preferences.rawPreferences(),
                    themes = preferences.availableThemes(),
                    defaultThemeId = preferences.defaultTheme(),
                )
                cacheActiveThemeAnchors()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /** Reset only the supplied keys to their server defaults. */
    fun reset(keys: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                preferences.resetKeys(keys)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    values = preferences.rawPreferences(),
                    themes = preferences.availableThemes(),
                    defaultThemeId = preferences.defaultTheme(),
                )
                cacheActiveThemeAnchors()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Writes the active theme's anchors to the session store, which is what
     * `MochiTheme` builds its colour scheme from.
     *
     * Saving the `theme` preference only tells the server; the running app
     * reads its colours from the cached anchors, so without this a picked theme
     * moved the tick in the picker and changed nothing on screen until the next
     * cold start re-ran `ThemeRepository.fetchAndCacheTheme()`.
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
