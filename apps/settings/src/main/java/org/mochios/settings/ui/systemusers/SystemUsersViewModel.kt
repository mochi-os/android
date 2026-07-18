// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemusers

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
import org.mochios.android.auth.AuthRepository
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.settings.api.SystemUser
import org.mochios.settings.api.SystemUserSession
import org.mochios.settings.api.SystemUsersApi
import retrofit2.Response
import javax.inject.Inject

enum class SystemUsersSort { USERNAME, STATUS, LAST }

enum class SystemUsersOrder { ASC, DESC }

/**
 * Localisable toast signals from the ViewModel. The screen resolves these to
 * stringResource(R.string.system_users_toast_*) so the VM stays free of
 * Android string ids.
 */
enum class SystemUsersToast {
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    USER_SUSPENDED,
    SUSPENSION_REMOVED,
    SESSION_REVOKED,
    SESSIONS_REVOKED,
    CREATE_FAILED,
    UPDATE_FAILED,
    DELETE_FAILED,
    STATUS_FAILED,
    REVOKE_FAILED,
}

data class SystemUsersUiState(
    val isLoading: Boolean = true,
    val users: List<SystemUser> = emptyList(),
    val count: Int = 0,
    val search: String = "",
    val limit: Int = 25,
    val offset: Int = 0,
    val sort: SystemUsersSort = SystemUsersSort.USERNAME,
    val order: SystemUsersOrder = SystemUsersOrder.ASC,
    val mutating: Boolean = false,
    val sessionsLoadingFor: Long? = null,
    val sessions: List<SystemUserSession> = emptyList(),
    val sessionsRevokedCount: Int = 0,
    val currentUsername: String = "",
    val error: MochiError? = null,
    val toast: SystemUsersToast? = null,
)

@HiltViewModel
class SystemUsersViewModel @Inject constructor(
    private val api: SystemUsersApi,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemUsersUiState())
    val uiState: StateFlow<SystemUsersUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadCurrentUser()
        refresh()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            runCatching { authRepository.getIdentityInfo().identity }
                .onSuccess { identity ->
                    _uiState.value = _uiState.value.copy(currentUsername = identity.email)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val s = _uiState.value
            try {
                val data = api.list(
                    limit = s.limit,
                    offset = s.offset,
                    search = s.search,
                    sort = s.sort.serial(),
                    order = s.order.serial(),
                ).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    users = data.users,
                    count = data.count,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun setSearch(value: String) {
        _uiState.value = _uiState.value.copy(search = value, offset = 0)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            refresh()
        }
    }

    fun toggleSort(column: SystemUsersSort) {
        val current = _uiState.value
        val next = if (current.sort == column) {
            current.copy(
                order = if (current.order == SystemUsersOrder.ASC) SystemUsersOrder.DESC else SystemUsersOrder.ASC,
                offset = 0,
            )
        } else {
            current.copy(sort = column, order = SystemUsersOrder.ASC, offset = 0)
        }
        _uiState.value = next
        refresh()
    }

    fun setLimit(limit: Int) {
        _uiState.value = _uiState.value.copy(limit = limit, offset = 0)
        refresh()
    }

    fun nextPage() {
        val s = _uiState.value
        if (s.offset + s.limit >= s.count) return
        _uiState.value = s.copy(offset = s.offset + s.limit)
        refresh()
    }

    fun previousPage() {
        val s = _uiState.value
        if (s.offset == 0) return
        _uiState.value = s.copy(offset = maxOf(0, s.offset - s.limit))
        refresh()
    }

    fun create(username: String, role: String, onDone: (Boolean) -> Unit) {
        mutate(
            success = SystemUsersToast.USER_CREATED,
            failure = SystemUsersToast.CREATE_FAILED,
            onDone = onDone,
        ) { api.create(username, role).bodyOrThrow() }
    }

    fun update(id: Long, username: String?, role: String?, onDone: (Boolean) -> Unit) {
        mutate(
            success = SystemUsersToast.USER_UPDATED,
            failure = SystemUsersToast.UPDATE_FAILED,
            onDone = onDone,
        ) { api.update(id, username, role).bodyOrThrow() }
    }

    fun delete(id: Long, onDone: (Boolean) -> Unit) {
        mutate(
            success = SystemUsersToast.USER_DELETED,
            failure = SystemUsersToast.DELETE_FAILED,
            onDone = onDone,
        ) { api.delete(id).bodyOrThrow() }
    }

    fun toggleStatus(user: SystemUser, onDone: (Boolean) -> Unit) {
        val suspended = user.status == "suspended"
        val ok = if (suspended) SystemUsersToast.SUSPENSION_REMOVED else SystemUsersToast.USER_SUSPENDED
        mutate(
            success = ok,
            failure = SystemUsersToast.STATUS_FAILED,
            onDone = onDone,
        ) {
            if (suspended) api.activate(user.id).bodyOrThrow()
            else api.suspendUser(user.id).bodyOrThrow()
        }
    }

    fun loadSessions(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sessionsLoadingFor = id, sessions = emptyList())
            try {
                val data = api.sessions(id).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    sessionsLoadingFor = null,
                    sessions = data.sessions,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    sessionsLoadingFor = null,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun clearSessions() {
        _uiState.value = _uiState.value.copy(sessions = emptyList(), sessionsLoadingFor = null)
    }

    fun revokeSession(userId: Long, sessionId: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mutating = true)
            try {
                val resp = api.revokeSessions(userId, sessionId).bodyOrThrow()
                val sessions = api.sessions(userId).bodyOrThrow().sessions
                _uiState.value = _uiState.value.copy(
                    mutating = false,
                    sessions = sessions,
                    sessionsRevokedCount = resp.revoked,
                    toast = if (sessionId != null) SystemUsersToast.SESSION_REVOKED else SystemUsersToast.SESSIONS_REVOKED,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    mutating = false,
                    error = e.toMochiError(),
                    toast = SystemUsersToast.REVOKE_FAILED,
                )
            }
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toast = null, sessionsRevokedCount = 0)
    }

    private fun mutate(
        success: SystemUsersToast,
        failure: SystemUsersToast,
        onDone: (Boolean) -> Unit,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mutating = true)
            try {
                block()
                _uiState.value = _uiState.value.copy(mutating = false, toast = success)
                refresh()
                onDone(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    mutating = false,
                    error = e.toMochiError(),
                    toast = failure,
                )
                onDone(false)
            }
        }
    }

    private fun SystemUsersSort.serial(): String = when (this) {
        SystemUsersSort.USERNAME -> "username"
        SystemUsersSort.STATUS -> "status"
        SystemUsersSort.LAST -> "last"
    }

    private fun SystemUsersOrder.serial(): String = when (this) {
        SystemUsersOrder.ASC -> "asc"
        SystemUsersOrder.DESC -> "desc"
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }
}
