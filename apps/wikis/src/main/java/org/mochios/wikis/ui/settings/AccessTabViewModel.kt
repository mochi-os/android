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
import org.mochios.android.util.NaturalCompare
import org.mochios.wikis.R
import org.mochios.wikis.model.AccessRule
import org.mochios.wikis.model.Group
import org.mochios.wikis.model.User
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [AccessTab]. The access rules list mirrors the response
 * from `GET {wiki}/-/access`. User-search and group-list results back the
 * Add Access dialog.
 */
/**
 * One row in the access list: a subject and its derived access level.
 *
 * The server stores access as raw permission rules — a single `allow` rule
 * (operation = "view" / "edit") for granted subjects, or one `deny` rule per
 * operation for explicitly-blocked ("none") subjects. We group those raw rules
 * back to one row per subject and derive the level the same way web's
 * `AccessList` does: any deny rule means "none"; otherwise the granted
 * operation is the level.
 */
data class AccessSubject(
    val subject: String,
    val level: String,
    val name: String? = null,
    val isOwner: Boolean = false,
)

data class AccessTabUiState(
    val isLoading: Boolean = true,
    val subjects: List<AccessSubject> = emptyList(),
    val error: MochiError? = null,
    val userSearchResults: List<User> = emptyList(),
    val groups: List<Group> = emptyList(),
)

/**
 * Group the raw access rules into one [AccessSubject] per subject and derive
 * each subject's level. Mirrors web's grouping in `access-list.tsx`:
 * `grant == 0` (a deny rule) collapses the whole subject to "none"; otherwise
 * the granted operation is the level. Subjects are sorted owners-first then
 * by display name.
 */
internal fun groupAccessRules(rules: List<AccessRule>): List<AccessSubject> {
    val order = mutableListOf<String>()
    val grouped = linkedMapOf<String, MutableList<AccessRule>>()
    for (rule in rules) {
        val bucket = grouped.getOrPut(rule.subject) {
            order.add(rule.subject)
            mutableListOf()
        }
        bucket.add(rule)
    }
    return order.map { subject ->
        val subjectRules = grouped.getValue(subject)
        val denied = subjectRules.any { it.grant == 0 }
        val level = if (denied) {
            "none"
        } else {
            // The granted operation is the level (server stores one allow rule).
            subjectRules.firstOrNull { it.grant != 0 }?.operation ?: "none"
        }
        AccessSubject(
            subject = subject,
            level = level,
            name = subjectRules.firstNotNullOfOrNull { it.name },
            isOwner = subjectRules.any { it.isOwner == true },
        )
    }.sortedWith(
        compareByDescending<AccessSubject> { it.isOwner }
            .thenComparing({ it.name ?: it.subject }, NaturalCompare),
    )
}

/** Snackbar message dispatched by [AccessTabViewModel]. */
data class AccessTabSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
)

@HiltViewModel
class AccessTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    private val _uiState = MutableStateFlow(AccessTabUiState())
    val uiState: StateFlow<AccessTabUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<AccessTabSnackbar>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<AccessTabSnackbar> = _snackbar.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val subjects = groupAccessRules(repository.getAccess(wikiId))
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    subjects = subjects,
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

    fun setAccess(subject: String, level: String) {
        viewModelScope.launch {
            try {
                repository.setAccess(wikiId, subject, level)
                _snackbar.emit(AccessTabSnackbar(R.string.wikis_access_set_success))
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
                _snackbar.emit(AccessTabSnackbar(R.string.wikis_access_set_failed))
            }
        }
    }

    fun revokeAccess(subject: String) {
        viewModelScope.launch {
            try {
                repository.revokeAccess(wikiId, subject)
                _snackbar.emit(AccessTabSnackbar(R.string.wikis_access_revoke_success))
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
                _snackbar.emit(AccessTabSnackbar(R.string.wikis_access_revoke_failed))
            }
        }
    }

    fun searchUsers(query: String) {
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            return
        }
        viewModelScope.launch {
            try {
                val results = repository.searchUsers(query)
                _uiState.value = _uiState.value.copy(userSearchResults = results)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            try {
                val groups = repository.listGroups(wikiId)
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(groups = groups)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(groups = emptyList())
            }
        }
    }
}
