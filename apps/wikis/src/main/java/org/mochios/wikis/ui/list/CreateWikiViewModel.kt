// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * State of the create-wiki screen.
 *
 * @property isCreating true while the create request is in flight.
 * @property error what went wrong on the last create attempt, if anything.
 * @property createdWikiId set to the new wiki's id after a successful create so
 *   the screen can navigate into it; cleared once consumed.
 */
data class CreateWikiUiState(
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdWikiId: String? = null
)

/** Drives the create-wiki screen. */
@HiltViewModel
class CreateWikiViewModel @Inject constructor(
    private val repo: WikisRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateWikiUiState())
    val uiState: StateFlow<CreateWikiUiState> = _uiState.asStateFlow()

    /** Clears the pending-navigation id once the screen has opened the wiki. */
    fun consumeCreatedWiki() {
        _uiState.value = _uiState.value.copy(createdWikiId = null)
    }

    /** Creates an owned wiki and reports its id back through [CreateWikiUiState]. */
    fun createWiki(name: String, privacy: String) {
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val created = repo.createWiki(name, privacy)
                val newWikiId = created.fingerprint.ifBlank { created.id }
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdWikiId = newWikiId.takeIf { id -> id.isNotBlank() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }
}
