package org.mochios.people.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.people.model.Group
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createDialogOpen: Boolean = false,
)

/**
 * Navigation events emitted by the Groups list. Collected as a single-shot
 * flow by the composable so a re-composition can't replay them.
 */
sealed interface GroupsEvent {
    data class OpenGroup(val id: String) : GroupsEvent
}

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    private val _events = Channel<GroupsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
        observeGroupChanges()
    }

    /**
     * Reload the list whenever a group is edited or deleted on the detail
     * screen, which runs on a separate ViewModel. Keeps this list in sync
     * without depending on navigation-resume callbacks.
     */
    private fun observeGroupChanges() {
        viewModelScope.launch {
            repository.groupsChanged.collect {
                load(silent = true)
            }
        }
    }

    /**
     * Fetch the group list.
     *
     * @param silent when `true`, updates the list in the background without
     * toggling the loading indicator or surfacing a fetch error — used for
     * cross-screen sync where the change was already confirmed elsewhere, so
     * a spinner or error screen would only flicker. The current list is kept
     * if a silent fetch fails.
     */
    private fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            try {
                val groups = repository.listGroups()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    groups = groups,
                    isLoading = false,
                )
            } catch (e: Exception) {
                if (!silent) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.toMochiError(),
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val groups = repository.listGroups()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(
                    groups = groups,
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun retry() = load()

    fun openCreate() {
        _uiState.value = _uiState.value.copy(createDialogOpen = true)
    }

    fun closeCreate() {
        if (!_uiState.value.isCreating) {
            _uiState.value = _uiState.value.copy(createDialogOpen = false)
        }
    }

    fun createGroup(name: String, description: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val id = repository.createGroup(
                    name = trimmedName,
                    description = description.trim().ifBlank { null },
                )
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createDialogOpen = false,
                )
                _events.send(GroupsEvent.OpenGroup(id))
                // Refresh in the background so the list reflects the new
                // group when the user comes back from the detail screen.
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError(),
                )
            }
        }
    }
}
