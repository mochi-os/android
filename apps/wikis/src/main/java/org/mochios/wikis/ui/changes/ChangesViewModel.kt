package org.mochios.wikis.ui.changes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.wikis.model.Change
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [ChangesListScreen]. Mirrors web's `changes-list.tsx`: a flat
 * list of [Change] rows in newest-first order with author avatars and links
 * to the edited page.
 *
 * Wiki info + permissions are loaded in parallel so the screen can wrap its
 * body in a [org.mochios.wikis.ui.components.LocalWikiContext] (needed by
 * [org.mochios.wikis.ui.components.AuthorAvatar]).
 */
data class ChangesUiState(
    val isLoading: Boolean = true,
    val changes: List<Change> = emptyList(),
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
)

/**
 * ViewModel for [ChangesListScreen]. Reads `wikiId` from [SavedStateHandle]
 * (set by `WikisApp.CHANGES`) and fires two parallel loads on init: wiki
 * info and the recent-changes feed (`/-/changes`).
 */
@HiltViewModel
class ChangesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(ChangesUiState())
    val uiState: StateFlow<ChangesUiState> = _uiState.asStateFlow()

    init {
        loadInfo()
        loadChanges()
    }

    fun loadInfo() {
        viewModelScope.launch {
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = _uiState.value.copy(
                    wiki = response.wiki,
                    permissions = response.permissions ?: WikiPermissions(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun loadChanges() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val changes = repository.getChanges(wikiId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    changes = changes,
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
}
