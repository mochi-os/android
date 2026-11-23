// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemusers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.messageOrFallback
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.api.userMessage
import org.mochios.android.auth.AuthRepository
import org.mochios.android.i18n.AppContext
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.settings.R
import org.mochios.settings.api.SystemUser
import org.mochios.settings.api.SystemUserSession
import org.mochios.settings.api.SystemUsersApi
import javax.inject.Inject
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController

enum class SystemUsersSort { USERNAME, STATUS, LAST }

enum class SystemUsersOrder { ASC, DESC }

/**
 * What succeeded, for the screen to say in its own words: the session count is
 * a plural, which only the Compose layer can inflect. A failure is a message
 * the view model has already worded (see [SystemUsersViewModel.messages]).
 */
enum class SystemUsersToast {
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    USER_SUSPENDED,
    SUSPENSION_REMOVED,
    SESSION_REVOKED,
    SESSIONS_REVOKED,
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
    val sessionsLoadingFor: String? = null,
    val sessions: List<SystemUserSession> = emptyList(),
    val sessionsRevokedCount: Int = 0,
    val currentUsername: String = "",
    /** A failed read of the list; an action's failure is a message instead. */
    val error: MochiError? = null,
    val toast: SystemUsersToast? = null,
)

/**
 * Creating an administrator grants a privilege, so it takes the step-up that
 * promoting one does and [create] gets the proof; an ordinary user is created
 * at once, with none.
 */
internal fun createGated(role: String, stepUp: StepUpController, create: (String?) -> Unit) {
    if (role == "administrator") stepUp.request { token -> create(token) } else create(null)
}

@HiltViewModel
class SystemUsersViewModel @Inject constructor(
    private val api: SystemUsersApi,
    stepUpClient: SettingsStepUpClient,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemUsersUiState())
    val uiState: StateFlow<SystemUsersUiState> = _uiState.asStateFlow()

    /**
     * Why an action failed, said once: the server's reason, else the action's
     * own words. Shown whether or not any users are listed.
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Step-up gate for the administrator mutations: a stolen session must
     *  re-verify a login factor before it can change the server. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _messages.tryEmit(e.toMochiError().userMessage()) },
    )

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

    private var refreshJob: Job? = null

    /**
     * Load the page the current search, sort and offset ask for, dropping any
     * older load. The users shown stay on screen while it is read; the screen
     * shows a spinner only while it has none.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val s = _uiState.value
            try {
                val data = api.list(
                    limit = s.limit,
                    offset = s.offset,
                    search = s.search,
                    sort = s.sort.serial(),
                    order = s.order.serial(),
                ).unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    users = data.users,
                    count = data.count,
                )
            } catch (e: CancellationException) {
                throw e
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
        createGated(role, stepUp) { token ->
            mutate(
                success = SystemUsersToast.USER_CREATED,
                fallback = R.string.system_users_toast_create_failed,
                onDone = onDone,
            ) { api.create(username, role, token).unwrapRaw() }
        }
    }

    fun update(uid: String, username: String?, role: String?, onDone: (Boolean) -> Unit) {
        stepUp.request { token ->
            mutate(
                success = SystemUsersToast.USER_UPDATED,
                fallback = R.string.system_users_toast_update_failed,
                onDone = onDone,
            ) { api.update(uid, username, role, token).unwrapEmpty() }
        }
    }

    fun delete(uid: String, onDone: (Boolean) -> Unit) {
        stepUp.request { token ->
            mutate(
                success = SystemUsersToast.USER_DELETED,
                fallback = R.string.system_users_toast_delete_failed,
                onDone = onDone,
            ) { api.delete(uid, token).unwrapRaw() }
        }
    }

    fun toggleStatus(user: SystemUser, onDone: (Boolean) -> Unit) {
        val suspended = user.status == "suspended"
        val ok = if (suspended) SystemUsersToast.SUSPENSION_REMOVED else SystemUsersToast.USER_SUSPENDED
        stepUp.request { token ->
            mutate(
                success = ok,
                fallback = R.string.system_users_toast_status_failed,
                onDone = onDone,
            ) {
                if (suspended) api.activate(user.uid, token).unwrapRaw()
                else api.suspendUser(user.uid, token).unwrapRaw()
            }
        }
    }

    fun loadSessions(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sessionsLoadingFor = id, sessions = emptyList())
            try {
                val data = api.sessions(id).unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    sessionsLoadingFor = null,
                    sessions = data.sessions,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(sessionsLoadingFor = null)
                _messages.emit(e.toMochiError().userMessage())
            }
        }
    }

    fun clearSessions() {
        _uiState.value = _uiState.value.copy(sessions = emptyList(), sessionsLoadingFor = null)
    }

    fun revokeSession(userId: String, sessionId: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mutating = true)
            try {
                val resp = api.revokeSessions(userId, sessionId).unwrapRaw()
                val sessions = api.sessions(userId).unwrapRaw().sessions
                _uiState.value = _uiState.value.copy(
                    mutating = false,
                    sessions = sessions,
                    sessionsRevokedCount = resp.revoked,
                    toast = if (sessionId != null) SystemUsersToast.SESSION_REVOKED else SystemUsersToast.SESSIONS_REVOKED,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(mutating = false)
                _messages.emit(failure(e, R.string.system_users_toast_revoke_failed))
            }
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toast = null, sessionsRevokedCount = 0)
    }

    /** Run an action; a failure is said once, with [fallback] as its words where the server gives none. */
    private fun mutate(
        success: SystemUsersToast,
        fallback: Int,
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(mutating = false)
                _messages.emit(failure(e, fallback))
                onDone(false)
            }
        }
    }

    /** The server's reason for a refusal, else the action's own words for its failure. */
    private fun failure(e: Throwable, fallback: Int): String =
        e.toMochiError().messageOrFallback(AppContext.get().getString(fallback))

    private fun SystemUsersSort.serial(): String = when (this) {
        SystemUsersSort.USERNAME -> "username"
        SystemUsersSort.STATUS -> "status"
        SystemUsersSort.LAST -> "last"
    }

    private fun SystemUsersOrder.serial(): String = when (this) {
        SystemUsersOrder.ASC -> "asc"
        SystemUsersOrder.DESC -> "desc"
    }

    /** Dismiss a surfaced error once the screen has shown it. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
