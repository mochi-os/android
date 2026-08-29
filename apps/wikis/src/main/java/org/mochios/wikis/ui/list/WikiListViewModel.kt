// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.wikis.model.DirectoryEntry
import org.mochios.wikis.model.Recommendation
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.repository.JoinWikiResult
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

data class WikiListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val wikis: List<WikiInfo> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val error: MochiError? = null,

    /**
     * The top bar's filter over [wikis] — distinct from [searchQuery], which
     * queries the directory for wikis the user has *not* joined yet.
     */
    val showSearch: Boolean = false,
    val listQuery: String = "",

    val searchQuery: String = "",
    val searchResults: List<DirectoryEntry> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: MochiError? = null,

    val subscribingId: String? = null,
    val unsubscribingId: String? = null,

    /** Set when the user taps the overflow Unsubscribe action — drives the confirm dialog. */
    val unsubscribeCandidate: WikiInfo? = null,
)

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

    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(WikiListUiState())
    val uiState: StateFlow<WikiListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WikiListEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WikiListEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null

    init {
        // The list is the repository's, not this ViewModel's copy of it: a
        // subscribe from the find screen or an unsubscribe from anywhere
        // reaches the cache, and this screen is showing it the moment the
        // user comes back to it.
        viewModelScope.launch {
            repo.wikiList.collect { wikis ->
                _uiState.value = _uiState.value.copy(
                    wikis = wikis.sortedWith(compareBy(NaturalCompare) { it.name }),
                )
            }
        }
        loadInfo()
        loadRecommendations()
    }

    // ---------------- list ----------------

    fun loadInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                repo.getClassInfo()
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                repo.getClassInfo()
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = null)
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

    // ---------------- inline directory search ----------------

    fun toggleSearch() {
        val current = _uiState.value
        // Closing drops the query: a filter still applied to a list whose bar
        // has gone would hide wikis with nothing on screen saying why.
        _uiState.value = current.copy(
            showSearch = !current.showSearch,
            listQuery = if (current.showSearch) "" else current.listQuery,
        )
    }

    fun updateListQuery(query: String) {
        _uiState.value = _uiState.value.copy(listQuery = query)
    }

    /**
     * [wikis] narrowed to the top bar's query — every wiki when it is blank.
     *
     * Name only: it is the one thing a card puts on screen, so a match the
     * user cannot see the reason for never appears. Finding a wiki by ID or
     * fingerprint is what the find screen is for.
     */
    fun filteredWikis(): List<WikiInfo> {
        val query = _uiState.value.listQuery.trim().lowercase()
        if (query.isBlank()) return _uiState.value.wikis
        return _uiState.value.wikis.filter { wiki ->
            wiki.name.lowercase().contains(query)
        }
    }

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
            delay(SEARCH_DEBOUNCE)
            _uiState.value = _uiState.value.copy(searchLoading = true, searchError = null)
            try {
                // A pasted share link names a wiki on a server this instance
                // may never have heard of, so the directory cannot answer it.
                // Probe the peer the link names instead.
                val results = if (isShareLink(query)) {
                    listOf(repo.probeUrl(query.trim()))
                } else {
                    repo.directorySearch(query).results
                }
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

    fun subscribeFromSearch(entry: DirectoryEntry) {
        subscribe(
            target = entry.id.ifEmpty { entry.fingerprint },
            server = entry.location,
            peer = entry.peer,
        )
    }

    /** Subscribe to a recommendation. Recommendations always include a server hint. */
    fun subscribeFromRecommendation(rec: Recommendation) {
        subscribe(target = rec.id.ifEmpty { rec.fingerprint }, server = rec.server.ifBlank { null })
    }

    private fun subscribe(target: String, server: String?, peer: String? = null) {
        if (_uiState.value.subscribingId == target) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(subscribingId = target)
            val result = tryJoin(target, server, peer)
            _uiState.value = _uiState.value.copy(subscribingId = null)
            if (result.isSuccess) {
                val join = result.getOrThrow()
                _events.emit(WikiListEvent.OpenWiki(join.fingerprint.ifBlank { join.id }, join.home))
            } else {
                val message = result.exceptionOrNull()?.toMochiError()?.userMessage()
                    ?: MochiError.Unknown().userMessage()
                _events.emit(WikiListEvent.Toast(message))
            }
        }
    }

    /**
     * Retries without the server hint on a 502: the directory's server is
     * unreachable, so the local server falls back to peer discovery.
     */
    private suspend fun tryJoin(
        target: String,
        server: String?,
        peer: String? = null,
    ): Result<JoinWikiResult> {
        return try {
            Result.success(repo.joinWiki(target, server, peer))
        } catch (e: Exception) {
            val err = e.toMochiError()
            if (server != null && err is MochiError.ServerError && err.code == 502) {
                try {
                    Result.success(repo.joinWiki(target, null, peer))
                } catch (retry: Exception) {
                    Result.failure(retry)
                }
            } else {
                Result.failure(e)
            }
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
                _uiState.value = _uiState.value.copy(unsubscribingId = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(unsubscribingId = null)
                val message = e.toMochiError().userMessage()
                _events.emit(WikiListEvent.Toast(message))
            }
        }
    }

    // ---------------- RSS ----------------

    /**
     * Mint a class-level RSS token and build its feed URL. [mode] is "changes",
     * "comments" or "all".
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
     * Retire the tokens behind the all-wikis feed. Nothing to hand back - the
     * caller only needs to know whether it took.
     */
    suspend fun revokeRssAccess(): Result<Unit> {
        return try {
            repo.revokeGlobalRssTokens()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun subscribedWikiIds(): Set<String> {
        val state = _uiState.value
        return state.wikis.flatMap { listOfNotNull(it.id.takeIf { v -> v.isNotEmpty() }, it.fingerprint) }
            .toSet()
    }
}

/** What a wiki share link starts with; anything else is a directory search. */
private const val SHARE_LINK_PREFIX = "mochi://"

/**
 * Whether [query] is a pasted share link rather than a search term.
 *
 * A link names a wiki on a server this instance may never have heard of, so
 * the directory cannot answer it and the peer must be probed instead. Pasting
 * routinely carries surrounding whitespace, hence the trim.
 */
internal fun isShareLink(query: String): Boolean =
    query.trim().startsWith(SHARE_LINK_PREFIX)
