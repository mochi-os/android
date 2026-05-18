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
data class AccessTabUiState(
    val isLoading: Boolean = true,
    val rules: List<AccessRule> = emptyList(),
    val error: MochiError? = null,
    val userSearchResults: List<User> = emptyList(),
    val groups: List<Group> = emptyList(),
)

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
                val list = repository.getAccess(wikiId)
                    .sortedWith(compareBy(NaturalCompare) { it.name ?: it.subject })
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    rules = list,
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
                _uiState.value = _uiState.value.copy(
                    userSearchResults = repository.searchUsers(wikiId, query),
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    groups = repository.listGroups(wikiId)
                        .sortedWith(compareBy(NaturalCompare) { it.name }),
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(groups = emptyList())
            }
        }
    }
}
