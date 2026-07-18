// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.staff.model.DirectorySearchResult
import org.mochios.staff.model.StaffMember
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * UI state for [TeamScreen]. Mirrors web's `TeamPage` local state shape —
 * the loaded team list, the open Add dialog state, the open Remove dialog
 * state, and the per-member "role updating" flag that gates the Select
 * while the server processes the change.
 */
data class TeamUiState(
    val members: List<StaffMember> = emptyList(),
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val showAddDialog: Boolean = false,
    val removeTarget: StaffMember? = null,
    val submitting: Boolean = false,
    /** ID of the row currently saving a role change, if any. */
    val roleUpdatingId: String? = null,

    // Add-dialog substate
    val addSearch: String = "",
    val addResults: List<DirectorySearchResult> = emptyList(),
    val addSearching: Boolean = false,
    val addSelectedId: String? = null,
    val addSelectedName: String? = null,
    val addRole: String = "",
)

sealed interface TeamEvent {
    data class Toast(val messageRes: Int) : TeamEvent
    data class Error(val error: MochiError) : TeamEvent
}

/**
 * ViewModel for the Team screen. Owns the team list and the Add dialog
 * substate (debounced directory search). Role changes and removals fire
 * straight against [StaffRepository]; the screen renders the optimistic
 * pending flags.
 */
@HiltViewModel
class TeamViewModel @Inject constructor(
    private val repo: StaffRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TeamUiState())
    val state: StateFlow<TeamUiState> = _state.asStateFlow()

    private val _events = Channel<TeamEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var searchToken: Int = 0

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val list = repo.listTeam()
                _state.value = _state.value.copy(members = list, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun openAddDialog() {
        _state.value = _state.value.copy(
            showAddDialog = true,
            addSearch = "",
            addResults = emptyList(),
            addSelectedId = null,
            addSelectedName = null,
            addRole = "",
        )
    }

    fun closeAddDialog() {
        _state.value = _state.value.copy(
            showAddDialog = false,
            addSearch = "",
            addResults = emptyList(),
            addSelectedId = null,
            addSelectedName = null,
            addRole = "",
            addSearching = false,
        )
    }

    fun setAddSearch(query: String) {
        _state.value = _state.value.copy(addSearch = query)
        val token = ++searchToken
        viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            if (token != searchToken) return@launch
            if (query.length < 2) {
                _state.value = _state.value.copy(addResults = emptyList(), addSearching = false)
                return@launch
            }
            _state.value = _state.value.copy(addSearching = true)
            try {
                val results = repo.searchDirectory(query)
                if (token == searchToken) {
                    _state.value = _state.value.copy(addResults = results, addSearching = false)
                }
            } catch (e: Exception) {
                if (token == searchToken) {
                    _state.value = _state.value.copy(addResults = emptyList(), addSearching = false)
                }
            }
        }
    }

    fun selectAddPerson(id: String, name: String) {
        _state.value = _state.value.copy(addSelectedId = id, addSelectedName = name)
    }

    fun setAddRole(role: String) {
        _state.value = _state.value.copy(addRole = role)
    }

    fun submitAdd() {
        val id = _state.value.addSelectedId ?: return
        val role = _state.value.addRole
        if (role.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            try {
                repo.addTeamMember(id, role)
                _events.send(TeamEvent.Toast(org.mochios.staff.R.string.staff_team_toast_added))
                _state.value = _state.value.copy(submitting = false)
                closeAddDialog()
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.send(TeamEvent.Error(e.toMochiError()))
            }
        }
    }

    fun askRemove(member: StaffMember) {
        _state.value = _state.value.copy(removeTarget = member)
    }

    fun cancelRemove() {
        _state.value = _state.value.copy(removeTarget = null)
    }

    fun confirmRemove() {
        val target = _state.value.removeTarget ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            try {
                repo.removeTeamMember(target.id)
                _events.send(TeamEvent.Toast(org.mochios.staff.R.string.staff_team_toast_removed))
                _state.value = _state.value.copy(removeTarget = null, submitting = false)
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.send(TeamEvent.Error(e.toMochiError()))
            }
        }
    }

    fun changeRole(member: StaffMember, role: String) {
        if (role == member.role) return
        viewModelScope.launch {
            _state.value = _state.value.copy(roleUpdatingId = member.id)
            try {
                val updated = repo.setTeamRole(member.id, role)
                _state.value = _state.value.copy(
                    members = _state.value.members.map { if (it.id == member.id) updated else it },
                )
                _events.send(TeamEvent.Toast(org.mochios.staff.R.string.staff_team_toast_role_updated))
            } catch (e: Exception) {
                _events.send(TeamEvent.Error(e.toMochiError()))
            } finally {
                _state.value = _state.value.copy(roleUpdatingId = null)
            }
        }
    }
}
