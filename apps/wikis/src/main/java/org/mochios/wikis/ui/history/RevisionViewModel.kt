// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.history

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.wikis.model.RevisionDetail
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

data class RevisionViewUiState(
    val isLoading: Boolean = true,
    val revision: RevisionDetail? = null,
    val currentVersion: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
    /** Compare-changes mode: when on, the body shows a line diff against the
     *  previous revision instead of the rendered markdown. Mirrors web's
     *  revision-view "Compare changes" toggle. */
    val showDiff: Boolean = false,
    val previousRevision: RevisionDetail? = null,
    val previousLoading: Boolean = false,
)

@HiltViewModel
class RevisionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()
    val version: Int = savedStateHandle.get<Int>("version") ?: 0

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(RevisionViewUiState())
    val uiState: StateFlow<RevisionViewUiState> = _uiState.asStateFlow()

    init {
        loadInfo()
        loadRevision()
    }

    fun loadInfo() {
        viewModelScope.launch {
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = _uiState.value.copy(
                    wiki = response.wiki,
                    permissions = response.permissions ?: WikiPermissions(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun loadRevision() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.getRevision(wikiId, slug, version)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    revision = response.revision,
                    currentVersion = response.version.current,
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

    fun toggleDiff() {
        val showing = !_uiState.value.showDiff
        _uiState.value = _uiState.value.copy(showDiff = showing)
        if (showing && version > 1 &&
            _uiState.value.previousRevision == null &&
            !_uiState.value.previousLoading
        ) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(previousLoading = true)
                try {
                    val resp = repository.getRevision(wikiId, slug, version - 1)
                    _uiState.value = _uiState.value.copy(
                        previousRevision = resp.revision,
                        previousLoading = false,
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(previousLoading = false)
                }
            }
        }
    }
}
