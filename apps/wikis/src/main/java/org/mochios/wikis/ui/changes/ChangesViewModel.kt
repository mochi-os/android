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
    val isLoadingMore: Boolean = false,
    val changes: List<Change> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
) {
    /** True when more changes remain on the server beyond what's loaded. */
    val hasMore: Boolean get() = changes.size < total
}

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
                val response = repository.getChanges(wikiId, PAGE_SIZE, 0)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    changes = response.changes,
                    total = response.total,
                    offset = response.changes.size,
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

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || !current.hasMore) return
        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            try {
                val response = repository.getChanges(wikiId, PAGE_SIZE, current.offset)
                // De-dup against already-loaded ids in case rows shifted between pages.
                val seen = current.changes.mapTo(mutableSetOf()) { it.id }
                val merged = current.changes + response.changes.filter { it.id !in seen }
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    changes = merged,
                    total = response.total,
                    offset = current.offset + response.changes.size,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
