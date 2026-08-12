// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/**
 * State of the create-group screen.
 *
 * @property isCreating true while the create request is in flight.
 * @property error what went wrong on the last create attempt, if anything.
 * @property createdGroupId set to the new group's id after a successful create
 *   so the screen can navigate into it; cleared once consumed.
 */
data class CreateGroupUiState(
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdGroupId: String? = null
)

/** Drives the create-group screen. */
@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val repository: PeopleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    /** Clears the pending-navigation id once the screen has opened the group. */
    fun consumeCreatedGroup() {
        _uiState.value = _uiState.value.copy(createdGroupId = null)
    }

    /** Creates a group and reports its id back through [CreateGroupUiState]. */
    fun createGroup(name: String, description: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || _uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val id = repository.createGroup(
                    name = trimmedName,
                    description = description.trim().ifBlank { null },
                )
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdGroupId = id.takeIf { value -> value.isNotBlank() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError(),
                )
            }
        }
    }
}
