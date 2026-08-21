// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.staff.model.DirectorySearchResult
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

data class AddTeamMemberUiState(
    val search: String = "",
    val results: List<DirectorySearchResult> = emptyList(),
    val searching: Boolean = false,
    val selectedId: String? = null,
    val selectedName: String? = null,
    val role: String = "",
    val submitting: Boolean = false,
    val error: MochiError? = null,
    val added: Boolean = false,
)

/** Drives the add-team-member screen. */
@HiltViewModel
class AddTeamMemberViewModel @Inject constructor(
    private val repo: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddTeamMemberUiState())
    val state: StateFlow<AddTeamMemberUiState> = _state.asStateFlow()

    /**
     * Discards results from a search the user has since typed past. Each call
     * claims the next token; only the holder of the current one may write.
     */
    private var searchToken: Int = 0

    fun setSearch(query: String) {
        _state.value = _state.value.copy(search = query)
        val token = ++searchToken
        viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            if (token != searchToken) return@launch
            if (query.length < 2) {
                _state.value = _state.value.copy(results = emptyList(), searching = false)
                return@launch
            }
            _state.value = _state.value.copy(searching = true)
            try {
                val results = repo.searchDirectory(query)
                if (token == searchToken) {
                    _state.value = _state.value.copy(results = results, searching = false)
                }
            } catch (e: Exception) {
                if (token == searchToken) {
                    _state.value = _state.value.copy(results = emptyList(), searching = false)
                }
            }
        }
    }

    fun selectPerson(id: String, name: String) {
        _state.value = _state.value.copy(selectedId = id, selectedName = name)
    }

    fun setRole(role: String) {
        _state.value = _state.value.copy(role = role)
    }

    /** Adds the picked person and reports it back through [AddTeamMemberUiState]. */
    fun submit() {
        val id = _state.value.selectedId ?: return
        val role = _state.value.role
        if (role.isBlank() || _state.value.submitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            try {
                repo.addTeamMember(id, role)
                _state.value = _state.value.copy(submitting = false, added = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    submitting = false,
                    error = e.toMochiError(),
                )
            }
        }
    }
}
