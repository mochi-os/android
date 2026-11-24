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
    val selectedFingerprint: String = "",
    val role: String = "",
    /** Whether the picked person and role are being confirmed before the role is granted. */
    val confirming: Boolean = false,
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

    fun selectPerson(person: DirectorySearchResult) {
        _state.value = _state.value.copy(
            selectedId = person.id,
            selectedName = person.name,
            selectedFingerprint = person.fingerprint,
        )
    }

    fun setRole(role: String) {
        _state.value = _state.value.copy(role = role)
    }

    /**
     * Asks to grant the role: the picked person's name and fingerprint are
     * shown to check against before [submit], since names are neither unique
     * nor verified and the role carries refund, ban and dispute authority.
     */
    fun confirm() {
        if (_state.value.selectedId == null || _state.value.role.isBlank() || _state.value.submitting) return
        _state.value = _state.value.copy(confirming = true)
    }

    fun cancelConfirm() {
        _state.value = _state.value.copy(confirming = false)
    }

    /** Adds the picked person and reports it back through [AddTeamMemberUiState]. */
    fun submit() {
        val id = _state.value.selectedId ?: return
        val role = _state.value.role
        if (role.isBlank() || _state.value.submitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, confirming = false, error = null)
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
