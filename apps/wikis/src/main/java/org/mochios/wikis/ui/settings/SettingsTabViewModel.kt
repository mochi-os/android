// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.settings

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.wikis.R
import org.mochios.wikis.model.WikiSettings
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [SettingsTab]. Carries the wiki's stored settings (home page,
 * subscription source) plus transient state for the inline rename and home
 * page save flows. The wiki name itself lives on the parent view model so
 * the TopAppBar title and the rename row stay in sync.
 */
data class SettingsTabUiState(
    val isLoading: Boolean = true,
    val settings: WikiSettings = WikiSettings(),
    val error: MochiError? = null,
    val isRenaming: Boolean = false,
    val nameError: NameValidationError? = null,
    val isSyncing: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
)

/**
 * Wiki name validation errors. Mirrors the rules enforced by the web client
 * (`apps/wikis/web/src/features/wiki/wiki-settings.tsx`):
 *
 *  - [REQUIRED]    name is empty or whitespace-only
 *  - [TOO_LONG]    name exceeds 100 characters
 *  - [INVALID_CHAR] name contains `<` or `>`
 */
enum class NameValidationError { REQUIRED, TOO_LONG, INVALID_CHAR }

/**
 * Snackbar messages dispatched by [SettingsTabViewModel]. Same shape as
 * [WikiSettingsSnackbar] — the tab collects on the parent view model in
 * practice, but a dedicated channel here keeps the tab's mutations
 * decoupled from the parent.
 */
data class SettingsTabSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
)

private const val MAX_NAME_LENGTH = 100
private val DISALLOWED_NAME_CHARS = Regex("[<>\r\n]")

@HiltViewModel
class SettingsTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    private val _uiState = MutableStateFlow(SettingsTabUiState())
    val uiState: StateFlow<SettingsTabUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<SettingsTabSnackbar>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<SettingsTabSnackbar> = _snackbar.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.getSettings(wikiId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    settings = response.settings,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /**
     * Validate a candidate wiki name without dispatching it. Used by the
     * inline edit row to show a per-character error message under the
     * input. Returns null when valid, the matching error enum otherwise.
     */
    fun validateName(name: String): NameValidationError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameValidationError.REQUIRED
            trimmed.length > MAX_NAME_LENGTH -> NameValidationError.TOO_LONG
            DISALLOWED_NAME_CHARS.containsMatchIn(trimmed) -> NameValidationError.INVALID_CHAR
            else -> null
        }
    }

    fun rename(newName: String, currentName: String, onSuccess: (String) -> Unit) {
        val trimmed = newName.trim()
        val validation = validateName(trimmed)
        if (validation != null) {
            _uiState.value = _uiState.value.copy(nameError = validation)
            return
        }
        if (trimmed == currentName) {
            // No-op; just clear validation and let the screen close the editor.
            _uiState.value = _uiState.value.copy(nameError = null)
            onSuccess(trimmed)
            return
        }
        _uiState.value = _uiState.value.copy(isRenaming = true, nameError = null)
        viewModelScope.launch {
            try {
                repository.renameWiki(wikiId, trimmed)
                _uiState.value = _uiState.value.copy(isRenaming = false)
                _snackbar.emit(SettingsTabSnackbar(R.string.wikis_settings_name_saved))
                onSuccess(trimmed)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRenaming = false)
                _snackbar.emit(SettingsTabSnackbar(R.string.wikis_settings_name_save_failed))
            }
        }
    }

    fun clearNameError() {
        _uiState.value = _uiState.value.copy(nameError = null)
    }

    fun saveHome(home: String, onSuccess: (String) -> Unit) {
        val trimmed = home.trim().ifEmpty { "home" }
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                repository.setSetting(wikiId, "home", trimmed)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    settings = _uiState.value.settings.copy(home = trimmed),
                )
                _snackbar.emit(SettingsTabSnackbar(R.string.wikis_settings_home_saved))
                onSuccess(trimmed)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                _snackbar.emit(SettingsTabSnackbar(R.string.wikis_settings_home_save_failed))
            }
        }
    }

    fun sync() {
        _uiState.value = _uiState.value.copy(isSyncing = true)
        viewModelScope.launch {
            try {
                repository.syncWiki(wikiId)
                _uiState.value = _uiState.value.copy(isSyncing = false)
                _snackbar.emit(SettingsTabSnackbar(R.string.wikis_settings_sync_success))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSyncing = false)
                _snackbar.emit(SettingsTabSnackbar(R.string.wikis_settings_sync_failed))
            }
        }
    }

    fun delete() {
        _uiState.value = _uiState.value.copy(isDeleting = true)
        viewModelScope.launch {
            try {
                repository.deleteWiki(wikiId)
                _uiState.value = _uiState.value.copy(isDeleting = false)
                _snackbar.emit(SettingsTabSnackbar(R.string.wikis_settings_delete_success))
                _deleted.emit(Unit)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDeleting = false)
                _snackbar.emit(SettingsTabSnackbar(R.string.wikis_settings_delete_failed))
            }
        }
    }
}
