package org.mochios.wikis.ui.history

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
import org.mochios.wikis.model.Revision
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [PageHistoryScreen]. Mirrors web's `page-history.tsx` data
 * inputs — a slug, the list of revisions, and the current version number so
 * the table can hide the Revert action on the active row.
 *
 * Wiki info + permissions are loaded in parallel with the history so the
 * screen can wrap its body in a [org.mochios.wikis.ui.components.LocalWikiContext]
 * (needed by [org.mochios.wikis.ui.components.AuthorAvatar]).
 */
data class PageHistoryUiState(
    val isLoading: Boolean = true,
    val revisions: List<Revision> = emptyList(),
    val currentVersion: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
)

/**
 * ViewModel for [PageHistoryScreen]. Reads `wikiId` and `page` from
 * [SavedStateHandle] (set by `WikisApp.PAGE_HISTORY`) and fires two parallel
 * loads on init: wiki info (so the screen has a [WikiInfo] for the wiki
 * context) and the history itself (`/-/<slug>/history`).
 *
 * The current version comes from the most recent revision in the response
 * (revisions are ordered newest-first, matching web). When the history is
 * empty there's no current version to compare against — the screen renders
 * the empty state and the Revert action never appears.
 */
@HiltViewModel
class PageHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(PageHistoryUiState())
    val uiState: StateFlow<PageHistoryUiState> = _uiState.asStateFlow()

    init {
        loadInfo()
        loadHistory()
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

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.getHistory(wikiId, slug)
                val current = response.revisions.maxOfOrNull { it.version } ?: 0
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    revisions = response.revisions,
                    currentVersion = current,
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
