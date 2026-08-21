// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.forumlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class CreateForumUiState(
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdForumId: String? = null
)

/** Drives the create-forum screen. */
@HiltViewModel
class CreateForumViewModel @Inject constructor(
    private val repository: ForumsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateForumUiState())
    val uiState: StateFlow<CreateForumUiState> = _uiState.asStateFlow()

    /** Clears the pending-navigation id once the screen has opened the forum. */
    fun consumeCreatedForum() {
        _uiState.value = _uiState.value.copy(createdForumId = null)
    }

    /** Creates a forum and reports its id back through [CreateForumUiState]. */
    fun createForum(name: String, privacy: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val forumId = repository.createForum(name, privacy)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdForumId = forumId.takeIf { id -> id.isNotBlank() }
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
