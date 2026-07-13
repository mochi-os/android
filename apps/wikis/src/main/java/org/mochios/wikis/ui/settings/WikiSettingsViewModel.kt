// Copyright © 2026 Mochisoft OÜ
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
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * Top-level UI state for [WikiSettingsScreen]. Carries the [WikiInfo] and
 * the caller's permission set so the screen can hide tabs that the wiki
 * doesn't expose (subscribed wikis have no Replicas tab).
 */
data class WikiSettingsUiState(
    val isLoading: Boolean = true,
    val wiki: WikiInfo? = null,
    val fingerprint: String? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
)

/**
 * One-shot snackbar message dispatched by [WikiSettingsViewModel]. Tabs and
 * the host screen subscribe to the shared flow and resolve the resource id
 * at render time via `stringResource`.
 */
data class WikiSettingsSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
)

/**
 * Host view model for the wiki settings screen. Loads the wiki info on init
 * so every tab sees the same name / fingerprint / source values, and offers
 * a shared snackbar channel + active-tab persistence.
 *
 * Per-tab data lives in dedicated tab view models ([SettingsTabViewModel],
 * [AccessTabViewModel], [ReplicasTabViewModel], plus the existing
 * [org.mochios.wikis.ui.redirects.RedirectsViewModel]) so the tabs can be
 * developed and tested in isolation.
 */
@HiltViewModel
class WikiSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val initialTab: String = savedStateHandle.get<String>("tab").orEmpty().ifEmpty { "settings" }

    private val _uiState = MutableStateFlow(WikiSettingsUiState())
    val uiState: StateFlow<WikiSettingsUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<WikiSettingsSnackbar>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<WikiSettingsSnackbar> = _snackbar.asSharedFlow()

    init {
        loadInfo()
    }

    fun loadInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    wiki = response.wiki,
                    fingerprint = response.fingerprint,
                    permissions = response.permissions ?: WikiPermissions(),
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

    /** Update the cached wiki name after a successful rename. */
    fun setWikiName(name: String) {
        val current = _uiState.value.wiki ?: return
        _uiState.value = _uiState.value.copy(wiki = current.copy(name = name))
    }

    /** Update the cached home setting after a successful save. */
    fun setHome(home: String) {
        val current = _uiState.value.wiki ?: return
        _uiState.value = _uiState.value.copy(wiki = current.copy(home = home))
    }

    fun emit(messageRes: Int, vararg args: Any) {
        viewModelScope.launch {
            _snackbar.emit(WikiSettingsSnackbar(messageRes, args.toList()))
        }
    }
}
