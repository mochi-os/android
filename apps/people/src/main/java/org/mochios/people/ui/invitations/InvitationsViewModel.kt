package org.mochios.people.ui.invitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.people.model.FriendInvite
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

/** One-shot snackbar event emitted after an invite mutation. The screen
 *  collects [events] and shows a Snackbar with the matching string. */
sealed class InvitationsEvent {
    data class Accepted(val name: String) : InvitationsEvent()
    data class Declined(val name: String) : InvitationsEvent()
    data class Cancelled(val name: String) : InvitationsEvent()
    data class PolicyUpdated(val policy: String) : InvitationsEvent()
}

data class InvitationsUiState(
    val isLoading: Boolean = false,
    val received: List<FriendInvite> = emptyList(),
    val sent: List<FriendInvite> = emptyList(),
    val searchQuery: String = "",
    val policy: String = "notify",
    val settingsDialogOpen: Boolean = false,
    val savingPolicy: Boolean = false,
    val error: MochiError? = null,
)

@HiltViewModel
class InvitationsViewModel @Inject constructor(
    private val repository: PeopleRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    /**
     * Absolute base URL for the server — used by the screen to build avatar
     * URLs that hit the people app's per-entity proxy
     * (`/people/<id>/-/avatar`). Read once at construction; the session URL
     * does not change without process restart.
     */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _events = Channel<InvitationsEvent>(Channel.BUFFERED)
    val events: Flow<InvitationsEvent> = _events.receiveAsFlow()

    private val _uiState = MutableStateFlow(InvitationsUiState())
    val uiState: StateFlow<InvitationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        loadPolicy()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Preserve server-order on invites (most-recent first) to
                // match the web. Alphabetical sort would scramble the
                // recency cue users expect on an invitations list.
                val response = repository.listFriends()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    received = response.received,
                    sent = response.sent,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    private fun loadPolicy() {
        viewModelScope.launch {
            try {
                val prefs = repository.getPreferences()
                _uiState.value = _uiState.value.copy(policy = prefs.invitePolicy)
            } catch (_: Exception) {
                // Non-critical — leave default. The settings dialog will fall
                // back to the displayed value when the user opens it.
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun filteredReceived(): List<FriendInvite> {
        val q = _uiState.value.searchQuery.lowercase()
        val list = _uiState.value.received
        if (q.isBlank()) return list
        return list.filter { it.name.lowercase().contains(q) }
    }

    fun filteredSent(): List<FriendInvite> {
        val q = _uiState.value.searchQuery.lowercase()
        val list = _uiState.value.sent
        if (q.isBlank()) return list
        return list.filter { it.name.lowercase().contains(q) }
    }

    fun accept(invite: FriendInvite) {
        viewModelScope.launch {
            try {
                repository.acceptInvite(invite.id)
                _events.trySend(InvitationsEvent.Accepted(invite.name))
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun decline(invite: FriendInvite) {
        viewModelScope.launch {
            try {
                repository.ignoreInvite(invite.id)
                _events.trySend(InvitationsEvent.Declined(invite.name))
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun cancel(invite: FriendInvite) {
        viewModelScope.launch {
            try {
                // For sent invites the server's "delete friend" action also
                // serves as cancel — same row, same id.
                repository.deleteFriend(invite.id)
                _events.trySend(InvitationsEvent.Cancelled(invite.name))
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun openSettings() {
        _uiState.value = _uiState.value.copy(settingsDialogOpen = true)
        // Refresh the policy in case it was changed elsewhere.
        loadPolicy()
    }

    fun closeSettings() {
        _uiState.value = _uiState.value.copy(settingsDialogOpen = false)
    }

    fun setPolicy(policy: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingPolicy = true)
            try {
                repository.setInvitePolicy(policy)
                _uiState.value = _uiState.value.copy(
                    policy = policy,
                    savingPolicy = false,
                    settingsDialogOpen = false,
                )
                _events.trySend(InvitationsEvent.PolicyUpdated(policy))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    savingPolicy = false,
                    error = e.toMochiError(),
                )
            }
        }
    }
}
