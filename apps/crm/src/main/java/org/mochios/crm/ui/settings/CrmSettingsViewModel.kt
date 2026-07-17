// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.model.AccessRule
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.crm.model.Person
import org.mochios.crm.model.Crm
import org.mochios.crm.model.Group
import org.mochios.crm.repository.CrmsRepository
import javax.inject.Inject

data class CrmSettingsUiState(
    val crm: Crm? = null,
    val accessRules: List<AccessRule> = emptyList(),
    val people: List<Person> = emptyList(),
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val name: String = "",
    val description: String = "",
    val prefix: String = "",
    val userSearchResults: List<Person> = emptyList(),
    val groups: List<Group> = emptyList()
)

@HiltViewModel
class CrmSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CrmsRepository
) : ViewModel() {

    val crmId: String = savedStateHandle.get<String>("crmId") ?: ""

    private val _uiState = MutableStateFlow(CrmSettingsUiState())
    val uiState: StateFlow<CrmSettingsUiState> = _uiState.asStateFlow()

    /** In-flight user search, cancelled by the next keystroke. */
    private var userSearchJob: Job? = null

    init {
        loadCrm()
        loadAccess()
    }

    private fun loadCrm() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val details = repository.getCrmInfo(crmId)
                _uiState.value = _uiState.value.copy(
                    crm = details.crm,
                    name = details.crm.name,
                    description = details.crm.description,
                    prefix = details.crm.prefix,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    private fun loadAccess() {
        viewModelScope.launch {
            try {
                val rules = repository.getAccess(crmId)
                    .sortedWith(compareBy(NaturalCompare) { it.name ?: it.subject })
                val people = repository.getPeople(crmId)
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    accessRules = rules,
                    people = people
                )
            } catch (_: Exception) { }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updatePrefix(prefix: String) {
        _uiState.value = _uiState.value.copy(prefix = prefix)
    }

    fun saveCrm() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                val state = _uiState.value
                repository.updateCrm(
                    crmId,
                    name = state.name,
                    description = state.description,
                    prefix = state.prefix
                )
                _uiState.value = _uiState.value.copy(isSaving = false)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun deleteCrm(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                repository.deleteCrm(crmId)
                _uiState.value = _uiState.value.copy(isDeleting = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun setAccess(subject: String, level: String) {
        viewModelScope.launch {
            try {
                repository.setAccess(crmId, subject, level)
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Live user search for the access picker (needs >=2 chars), mirroring web. */
    fun searchUsers(query: String) {
        // Each keystroke replaces the last: without this a typed name is one
        // request per letter, and a slow early response can land after a later
        // one and overwrite the newer results.
        userSearchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            return
        }
        userSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            try {
                // Await the results before reading the state to copy from —
                // inline, the receiver is captured before the suspend, so a slow
                // search writes back the snapshot it started from.
                val results = repository.searchUsers(query.trim())
                _uiState.value = _uiState.value.copy(userSearchResults = results)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            }
        }
    }

    /** Load the user's friend groups for the access picker's Groups tab. */
    fun loadGroups() {
        viewModelScope.launch {
            try {
                val groups = repository.getGroups()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(groups = groups)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(groups = emptyList())
            }
        }
    }

    fun unsubscribe(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(crmId)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun revokeAccess(subject: String) {
        viewModelScope.launch {
            try {
                repository.revokeAccess(crmId, subject)
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
