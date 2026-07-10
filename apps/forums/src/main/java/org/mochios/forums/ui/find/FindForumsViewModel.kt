// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.find

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
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

/** Debounce before a typed query is sent to the directory, in milliseconds. */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * One forum row in the discovery list, whichever source it came from — the
 * directory search, the recommendations, or a URL probe. Normalising them here
 * keeps the screen from branching three ways over near-identical shapes.
 *
 * @property id         Full entity id, used to subscribe.
 * @property key        Fingerprint when known, else [id] — the local tracking
 *                      key and the route argument once subscribed.
 * @property subtitle   Hyphenated fingerprint, shown under the name.
 * @property server     Home-server hint for a remote forum, or null.
 */
data class ForumDirectoryItem(
    val id: String,
    val key: String,
    val name: String,
    val subtitle: String = "",
    val blurb: String = "",
    val server: String? = null,
)

data class FindForumsUiState(
    val searchQuery: String = "",
    val recommended: List<ForumDirectoryItem> = emptyList(),
    val results: List<ForumDirectoryItem> = emptyList(),
    /** A forum resolved from a pasted URL, distinct from directory results. */
    val probeResult: ForumDirectoryItem? = null,
    val isSearching: Boolean = false,
    val isProbing: Boolean = false,
    val isLoading: Boolean = false,
    /** Key of the forum whose subscribe call is in flight, if any. */
    val subscribingKey: String? = null,
    val subscribed: Set<String> = emptySet(),
    val error: MochiError? = null,
)

@HiltViewModel
class FindForumsViewModel @Inject constructor(
    private val repository: ForumsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindForumsUiState())
    val uiState: StateFlow<FindForumsUiState> = _uiState.asStateFlow()

    /** Emits the forum to open once a subscribe succeeds. */
    private val _navigateToForum = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToForum: SharedFlow<String> = _navigateToForum.asSharedFlow()

    private var searchJob: Job? = null

    init {
        loadRecommendations()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val forums = repository.getRecommendations().forums.map { forum ->
                    ForumDirectoryItem(
                        id = forum.id,
                        key = forum.fingerprint.ifEmpty { forum.id },
                        name = forum.name,
                        blurb = forum.blurb,
                        server = forum.server.takeIf { server -> server.isNotBlank() },
                    )
                }
                _uiState.value = _uiState.value.copy(recommended = forums, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Search as the user types, debounced by [SEARCH_DEBOUNCE_MS] so a query is
     * only sent once they pause. A query that looks like a URL is probed
     * instead. Clearing the field restores the recommendations.
     */
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                probeResult = null,
                isSearching = false,
                isProbing = false,
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (looksLikeUrl(query)) {
                _uiState.value = _uiState.value.copy(results = emptyList())
                probe(query.trim())
            } else {
                _uiState.value = _uiState.value.copy(probeResult = null)
                search(query.trim())
            }
        }
    }

    private fun looksLikeUrl(query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    private suspend fun search(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        try {
            val entries = repository.searchForums(query)
            val results = entries.map { entry ->
                ForumDirectoryItem(
                    id = entry.id,
                    key = entry.fingerprint.ifEmpty { entry.id },
                    name = entry.name,
                    subtitle = entry.fingerprintHyphens.ifEmpty { entry.fingerprint },
                    server = entry.location.takeIf { location -> location.isNotBlank() },
                )
            }
            val alreadySubscribed = entries
                .filter { entry -> entry.subscribed }
                .map { entry -> entry.fingerprint.ifEmpty { entry.id } }
            _uiState.value = _uiState.value.copy(
                results = results,
                subscribed = _uiState.value.subscribed + alreadySubscribed,
                isSearching = false,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isSearching = false, error = e.toMochiError())
        }
    }

    private suspend fun probe(url: String) {
        _uiState.value = _uiState.value.copy(isProbing = true, error = null, probeResult = null)
        try {
            val r = repository.probe(url)
            _uiState.value = _uiState.value.copy(
                isProbing = false,
                probeResult = ForumDirectoryItem(
                    id = r.id,
                    key = r.fingerprint.ifEmpty { r.id },
                    name = r.name,
                    subtitle = r.fingerprint,
                    server = r.server.takeIf { server -> server.isNotBlank() },
                ),
            )
        } catch (_: Exception) {
            // A URL that resolves to nothing is a miss, not an error banner.
            _uiState.value = _uiState.value.copy(isProbing = false, probeResult = null)
        }
    }

    /**
     * Subscribe, then open the forum — only once the server has confirmed it.
     *
     * The entity's home server is tried first. A 502 means that hint was
     * unreachable, so the call is retried without it and the local server falls
     * back to general peer discovery. Mirrors web and [FindWikisViewModel].
     */
    fun subscribe(item: ForumDirectoryItem) {
        val target = item.id.ifEmpty { item.key }
        if (target.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(subscribingKey = item.key, error = null)
            val result = subscribeOrNull(target, item.server)
            val landingId = result.getOrElse { failure ->
                _uiState.value = _uiState.value.copy(
                    subscribingKey = null,
                    error = failure.toMochiError(),
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                subscribed = _uiState.value.subscribed + item.key,
                subscribingKey = null,
            )
            // The server names the forum it actually joined; prefer that over the
            // directory's key, which can be the full id rather than a fingerprint.
            _navigateToForum.emit(landingId.ifBlank { item.key })
        }
    }

    /** The joined forum's id on success, else the error that ended the attempt. */
    private suspend fun subscribeOrNull(target: String, server: String?): Result<String> {
        val first = runCatching { repository.subscribe(target, server) }
        first.getOrNull()?.let { landingId -> return Result.success(landingId) }

        val error = first.exceptionOrNull()!!.toMochiError()
        val hintUnreachable = server != null &&
            error is MochiError.ServerError && error.code == 502
        if (!hintUnreachable) return Result.failure(error)

        // The home-server hint was unreachable; let the local server fall back to
        // general peer discovery.
        return runCatching { repository.subscribe(target, null) }
            .mapCatching { landingId -> landingId }
            .recoverCatching { retryError -> throw retryError.toMochiError() }
    }

    /** Open an already-subscribed row without re-subscribing. */
    fun openForum(item: ForumDirectoryItem) {
        viewModelScope.launch { _navigateToForum.emit(item.key) }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
