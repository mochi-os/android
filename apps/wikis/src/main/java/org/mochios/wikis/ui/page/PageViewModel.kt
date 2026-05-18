package org.mochios.wikis.ui.page

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
import org.mochios.android.auth.SessionManager
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPage
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * One-shot side-effect events emitted by [PageViewModel] to the screen. Plain
 * navigation lives on the composable side (it owns the NavController), so this
 * channel is reserved for things the ViewModel completes asynchronously — RSS
 * URL ready for clipboard, unsubscribe finished, transient toast messages.
 */
sealed class PageViewEvent {
    /** Copy this URL to the clipboard and show the "RSS URL copied" toast. */
    data class CopyRssUrl(val url: String) : PageViewEvent()

    /** Display a transient error toast (already localised). */
    data class ShowError(val error: MochiError) : PageViewEvent()
}

/**
 * UI state for [PageViewScreen]. Mirrors the web `usePage` result + the
 * surrounding `WikiContext` so a single state value covers all branches —
 * loading, error, page found, page not found.
 *
 * Holding both the wiki info ([wiki] + [permissions]) and the page response on
 * one state simplifies the screen's `when` ladder: it can early-return on
 * loading / not-found / error before ever touching the page body.
 */
data class PageViewUiState(
    val isLoading: Boolean = true,
    val page: WikiPage? = null,
    val missingLinks: List<String> = emptyList(),
    val commentCount: Int = 0,
    val wiki: WikiInfo? = null,
    val permissions: WikiPermissions = WikiPermissions(),
    val error: MochiError? = null,
    val notFound: Boolean = false,
)

/**
 * ViewModel for the central wiki page-viewing experience.
 *
 * Reads `wikiId` and `page` from [SavedStateHandle] (set by the navigation
 * `NavType.StringType` args). On init, fires two parallel loads:
 *
 *  1. `loadInfo()` — `/-/info` for the wiki itself (name, home, permissions,
 *     subscription source). Powers the overflow menu's permission gates and
 *     unsubscribe affordance.
 *  2. `loadPage()` — `/-/<slug>` for the page body. Branches on the
 *     [PageFetchResponse] sealed type so the screen can show either the
 *     rendered body or the "Page not found" empty state.
 *
 * Action handlers (`unsubscribe`, `wikiRssToken`) are exposed for the overflow
 * menu's wired callbacks.
 */
@HiltViewModel
class PageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()
    val slug: String = savedStateHandle.get<String>("page").orEmpty()

    /** Origin of the Mochi server the session is bound to. Trimmed of trailing slash. */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(PageViewUiState())
    val uiState: StateFlow<PageViewUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PageViewEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PageViewEvent> = _events.asSharedFlow()

    init {
        loadInfo()
        loadPage()
    }

    /**
     * Fetch the wiki's `/-/info` response so the screen can apply the right
     * permission gates and decide whether to show the Unsubscribe action.
     * Errors here only surface as a banner — the page body still attempts to
     * load so the user isn't blocked by a transient info-endpoint failure.
     */
    fun loadInfo() {
        viewModelScope.launch {
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = _uiState.value.copy(
                    wiki = response.wiki,
                    permissions = response.permissions ?: WikiPermissions(),
                )
            } catch (e: Exception) {
                // Don't surface as a hard error — the page-body load will
                // surface the real error if there is one. Capture for
                // diagnostics only.
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Fetch `/-/<slug>` and branch on the sealed response. The deserializer
     * already mapped `{error: "not_found"}` into [PageFetchResponse.NotFound],
     * so the screen just reads `state.notFound` instead of pattern-matching
     * raw JSON.
     */
    fun loadPage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, notFound = false, error = null)
            try {
                when (val response = repository.getPage(wikiId, slug)) {
                    is PageFetchResponse.Page -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            page = response.page,
                            missingLinks = response.missingLinks ?: emptyList(),
                            commentCount = response.commentCount ?: 0,
                            notFound = false,
                            error = null,
                        )
                    }
                    is PageFetchResponse.NotFound -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            page = null,
                            missingLinks = emptyList(),
                            commentCount = 0,
                            notFound = true,
                            error = null,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /**
     * Unsubscribe from this wiki. Used by the overflow menu's Unsubscribe row
     * when the wiki is a subscription (has [WikiInfo.source]). The host
     * composable observes [uiState] and pops the back stack when the call
     * completes successfully.
     */
    suspend fun unsubscribe() {
        repository.unsubscribeWiki(wikiId)
    }

    /**
     * Build the per-wiki RSS URL for the requested mode and emit it as a
     * [PageViewEvent.CopyRssUrl] event for the composable to copy to
     * clipboard + toast.
     */
    fun copyRssUrl(mode: String) {
        viewModelScope.launch {
            try {
                val token = repository.wikiRssToken(wikiId, mode)
                val url = "$serverUrl/wikis/$wikiId/-/rss?token=$token"
                _events.emit(PageViewEvent.CopyRssUrl(url))
            } catch (e: Exception) {
                _events.emit(PageViewEvent.ShowError(e.toMochiError()))
            }
        }
    }

    /** Build the canonical share URL for this page on the bound server. */
    fun shareUrl(): String = "$serverUrl/wikis/$wikiId/$slug"

    /**
     * Replace the page's tag list in [uiState]. Called by the
     * [org.mochios.wikis.ui.components.TagManager] composable after the
     * server confirms an add / remove so the footer's chip row reflects the
     * mutation immediately, without waiting for a full page reload.
     */
    fun updatePageTags(tags: List<String>) {
        val current = _uiState.value.page ?: return
        _uiState.value = _uiState.value.copy(page = current.copy(tags = tags))
    }
}
