package org.mochios.wikis.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.NaturalCompare
import org.mochios.wikis.model.DirectoryEntry
import org.mochios.wikis.model.Recommendation
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.repository.JoinWikiResult
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for the wikis landing list. Mirrors the web `WikisListPage` body
 * in `apps/wikis/web/src/routes/_authenticated/index.tsx`:
 *
 *  - [wikis] is the user's mix of owned + subscribed wikis (owned has no
 *    [WikiInfo.source]; subscribed has a non-null source).
 *  - [recommendations] is the cold-start "Recommended wikis" rail driven by
 *    `-/recommendations`. We keep it loaded eagerly so the empty state can
 *    show it without an extra round-trip.
 *  - [searchQuery] / [searchResults] / [searchLoading] / [searchError] back
 *    the inline directory search exposed inside the empty state. Web debounces
 *    by 500 ms — the ViewModel does the same.
 *  - [subscribingId] / [unsubscribingId] disable per-row Subscribe /
 *    Unsubscribe buttons while a request is in flight, matching the web's
 *    `pendingWikiId` + mutation `isPending` flags.
 *  - [createDialogOpen] is a separate concern from the toolbar overflow's
 *    RSS submenu; the dialog body itself is owned by a downstream agent
 *    (`CreateWikiDialog` / `JoinWikiScreen`). This screen only owns the bit.
 */
data class WikiListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val wikis: List<WikiInfo> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val error: MochiError? = null,

    val createDialogOpen: Boolean = false,

    val searchQuery: String = "",
    val searchResults: List<DirectoryEntry> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: MochiError? = null,

    val subscribingId: String? = null,
    val unsubscribingId: String? = null,

    /** Set when the user taps the overflow Unsubscribe action — drives the confirm dialog. */
    val unsubscribeCandidate: WikiInfo? = null,
)

/**
 * Side-effect events emitted by the ViewModel. Mirrors People's `FriendsEvent`
 * pattern — the screen collects these and routes them to navigation /
 * snackbar / clipboard helpers without putting one-shot data into the
 * persistent UI state.
 */
sealed class WikiListEvent {
    /** Show a transient string (already localised) in a snackbar. */
    data class Toast(val message: String) : WikiListEvent()
    /** Subscribe completed — navigate to the new wiki's home page. */
    data class OpenWiki(val wikiId: String, val home: String) : WikiListEvent()
}

@HiltViewModel
class WikiListViewModel @Inject constructor(
    private val repo: WikisRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    /**
     * Server origin used to build RSS feed URLs (`${serverUrl}/wikis/-/rss?token=...`).
     * Captured at construction time the same way People's `FriendsViewModel`
     * captures `serverUrl` for avatar URLs.
     */
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(WikiListUiState())
    val uiState: StateFlow<WikiListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WikiListEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WikiListEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null

    init {
        loadInfo()
        loadRecommendations()
    }

    // ---------------- list ----------------

    fun loadInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val info = repo.getClassInfo()
                _uiState.value = _uiState.value.copy(
                    wikis = (info.wikis.orEmpty()).sortedWith(compareBy(NaturalCompare) { it.name }),
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val info = repo.getClassInfo()
                _uiState.value = _uiState.value.copy(
                    wikis = (info.wikis.orEmpty()).sortedWith(compareBy(NaturalCompare) { it.name }),
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
            loadRecommendations()
        }
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            try {
                val recs = repo.recommendations().wikis
                _uiState.value = _uiState.value.copy(recommendations = recs)
            } catch (_: Exception) {
                // Non-critical — recommendations are decorative; web swallows the error too.
            }
        }
    }

    // ---------------- create dialog ----------------

    fun openCreateDialog() {
        _uiState.value = _uiState.value.copy(createDialogOpen = true)
    }

    fun closeCreateDialog() {
        _uiState.value = _uiState.value.copy(createDialogOpen = false)
    }

    // ---------------- inline directory search ----------------

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                searchLoading = false,
                searchError = null,
            )
            return
        }
        searchJob = viewModelScope.launch {
            // 500ms debounce matches the web `InlineWikiSearch` debounce.
            delay(500)
            _uiState.value = _uiState.value.copy(searchLoading = true, searchError = null)
            try {
                val results = repo.directorySearch(query).results
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    searchLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searchLoading = false,
                    searchError = e.toMochiError(),
                )
            }
        }
    }

    // ---------------- subscribe ----------------

    /**
     * Subscribe to a wiki referenced from a directory search hit.
     *
     * Web's `handleSubscribe` first tries with the server hint from the
     * directory entry's `location`; if that fails with HTTP 502 (bad gateway
     * — the requested server isn't reachable), it retries without the hint so
     * the comptroller can pick a different replica. Mirrored here.
     */
    fun subscribeFromSearch(entry: DirectoryEntry) {
        subscribe(target = entry.id, server = entry.location)
    }

    /** Subscribe to a recommendation. Recommendations always include a server hint. */
    fun subscribeFromRecommendation(rec: Recommendation) {
        subscribe(target = rec.id, server = rec.server.ifBlank { null })
    }

    private fun subscribe(target: String, server: String?) {
        if (_uiState.value.subscribingId == target) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(subscribingId = target)
            val result = tryJoin(target, server)
            _uiState.value = _uiState.value.copy(subscribingId = null)
            if (result.isSuccess) {
                val join = result.getOrThrow()
                // Refresh local list so the new wiki shows up if the user
                // backs out of the wiki home instead of taking the auto-nav.
                refresh()
                _events.emit(WikiListEvent.OpenWiki(join.fingerprint.ifBlank { join.id }, join.home))
            } else {
                val err = result.exceptionOrNull()?.toMochiError()
                val message = err?.let { errorToMessage(it) } ?: "Failed to subscribe"
                _events.emit(WikiListEvent.Toast(message))
            }
        }
    }

    /**
     * Two-pass subscribe: first with the caller-supplied server hint, then
     * (only if we got back HTTP 502) with the hint stripped. Mirrors the web
     * fallback in `find.tsx`'s `handleSubscribe` — a 502 means the directory's
     * known server isn't currently reachable, so the comptroller (server=null)
     * is asked to find any replica.
     */
    private suspend fun tryJoin(target: String, server: String?): Result<JoinWikiResult> {
        return try {
            Result.success(repo.joinWiki(target, server))
        } catch (e: Exception) {
            val err = e.toMochiError()
            if (server != null && err is MochiError.ServerError && err.code == 502) {
                try {
                    Result.success(repo.joinWiki(target, null))
                } catch (retry: Exception) {
                    Result.failure(retry)
                }
            } else {
                Result.failure(e)
            }
        }
    }

    private fun errorToMessage(err: MochiError): String? {
        return when (err) {
            is MochiError.AuthError -> err.message
            is MochiError.ForbiddenError -> err.message
            is MochiError.NotFoundError -> err.message
            is MochiError.ServerError -> err.message
            is MochiError.Unknown -> err.message
            else -> null
        }
    }

    // ---------------- unsubscribe ----------------

    fun requestUnsubscribe(wiki: WikiInfo) {
        _uiState.value = _uiState.value.copy(unsubscribeCandidate = wiki)
    }

    fun cancelUnsubscribe() {
        _uiState.value = _uiState.value.copy(unsubscribeCandidate = null)
    }

    fun confirmUnsubscribe() {
        val wiki = _uiState.value.unsubscribeCandidate ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                unsubscribingId = wiki.id,
                unsubscribeCandidate = null,
            )
            try {
                repo.unsubscribeWiki(wiki.fingerprint ?: wiki.id)
                _uiState.value = _uiState.value.copy(
                    unsubscribingId = null,
                    wikis = _uiState.value.wikis.filterNot { it.id == wiki.id },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(unsubscribingId = null)
                val message = errorToMessage(e.toMochiError()) ?: "Failed to unsubscribe"
                _events.emit(WikiListEvent.Toast(message))
            }
        }
    }

    // ---------------- RSS ----------------

    /**
     * Mint a class-level RSS token for the requested [mode] (one of
     * `"changes"`, `"comments"`, `"all"`) and return the absolute feed URL.
     * Web's `handleCopyRssUrl` builds the same URL shape:
     * `${origin}${appPath}/-/rss?token=...`.
     */
    suspend fun makeRssUrl(mode: String): Result<String> {
        return try {
            val token = repo.globalRssToken(mode)
            Result.success("$serverUrl/wikis/-/rss?token=$token")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set of wiki identifiers (both `id` and `fingerprint`) that the user is
     * already subscribed to / owns. Used to filter the inline search results
     * and the recommendations rail so we never offer a Subscribe button for a
     * wiki that's already in the list. Mirrors `subscribedWikiIds` in
     * `WikisListPage`.
     */
    fun subscribedWikiIds(): Set<String> {
        val state = _uiState.value
        return state.wikis.flatMap { listOfNotNull(it.id.takeIf { v -> v.isNotEmpty() }, it.fingerprint) }
            .toSet()
    }
}
