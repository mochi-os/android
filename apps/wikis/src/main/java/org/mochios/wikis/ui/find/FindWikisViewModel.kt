// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.wikis.model.DirectoryEntry
import org.mochios.wikis.model.Recommendation
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [FindWikisScreen]. Mirrors web's `FindWikisPage`
 * (`apps/wikis/web/src/routes/_authenticated/find.tsx`) — a debounced
 * directory-search field on top, a recommendations list below, and a
 * subscribe button per row that handles the 502-retry-without-server-hint
 * dance.
 */
data class FindWikisUiState(
    val searchQuery: String = "",
    val results: List<DirectoryEntry> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    /** Entity ids/fingerprints/sources the user is already subscribed to. */
    val subscribedIds: Set<String> = emptySet(),
    val isSearching: Boolean = false,
    val isLoadingRecommendations: Boolean = false,
    /** Wiki id currently being subscribed to (disables that row's button). */
    val pendingId: String? = null,
    val error: MochiError? = null,
)

/**
 * Single-shot events surfaced to the composable so re-composition can't
 * replay them — snackbar feedback and navigation on successful subscribe.
 */
sealed interface FindEvent {
    data class SubscribeSuccess(val wikiId: String) : FindEvent
    data class SubscribeRetried(val wikiId: String) : FindEvent
    data class SubscribeFailed(val error: MochiError) : FindEvent
}

@HiltViewModel
class FindWikisViewModel @Inject constructor(
    private val repository: WikisRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindWikisUiState())
    val uiState: StateFlow<FindWikisUiState> = _uiState.asStateFlow()

    private val _events = Channel<FindEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        loadRecommendations()
        loadSubscribed()
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRecommendations = true)
            try {
                val r = repository.recommendations()
                _uiState.value = _uiState.value.copy(
                    recommendations = r.wikis,
                    isLoadingRecommendations = false,
                )
            } catch (_: Exception) {
                // Non-fatal: recommendations are optional discovery sugar.
                _uiState.value = _uiState.value.copy(isLoadingRecommendations = false)
            }
        }
    }

    private fun loadSubscribed() {
        viewModelScope.launch {
            try {
                val info = repository.getClassInfo()
                val ids = (info.wikis ?: emptyList()).flatMap { wiki ->
                    listOfNotNull(wiki.id, wiki.fingerprint, wiki.source)
                        .filter { it.isNotEmpty() }
                }.toSet()
                _uiState.value = _uiState.value.copy(subscribedIds = ids)
            } catch (_: Exception) {
                // Non-fatal — filter just stays empty.
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                isSearching = false,
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(500) // Debounce — matches web's 500ms in inline-wiki-search.
            performSearch(query.trim())
        }
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        try {
            val r = repository.directorySearch(query)
            _uiState.value = _uiState.value.copy(
                results = r.results,
                isSearching = false,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                error = e.toMochiError(),
            )
        }
    }

    /**
     * Subscribe to a directory result. Tries the entity's home server first;
     * on a 502 (bad gateway — server hint unreachable) retries with no
     * server hint so the local server falls back to general peer discovery.
     */
    fun subscribeDirectoryEntry(entry: DirectoryEntry) {
        subscribe(target = entry.id, server = entry.location.takeUnless { it.isNullOrBlank() })
    }

    /**
     * Subscribe to a recommendation. Same retry semantics as
     * [subscribeDirectoryEntry] — `server` is the recommendation's hint.
     */
    fun subscribeRecommendation(rec: Recommendation) {
        subscribe(target = rec.id, server = rec.server.takeIf { it.isNotBlank() })
    }

    private fun subscribe(target: String, server: String?) {
        if (target.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingId = target)
            val first = runCatching { repository.joinWiki(target, server) }
            val result = first.fold(
                onSuccess = { it },
                onFailure = { err ->
                    val mochiError = err.toMochiError()
                    // Retry without server hint on 502 (matches web).
                    if (server != null && mochiError is MochiError.ServerError && mochiError.code == 502) {
                        _events.send(FindEvent.SubscribeRetried(target))
                        runCatching { repository.joinWiki(target, null) }.getOrElse { retryErr ->
                            _uiState.value = _uiState.value.copy(
                                pendingId = null,
                                error = retryErr.toMochiError(),
                            )
                            _events.send(FindEvent.SubscribeFailed(retryErr.toMochiError()))
                            return@launch
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            pendingId = null,
                            error = mochiError,
                        )
                        _events.send(FindEvent.SubscribeFailed(mochiError))
                        return@launch
                    }
                },
            )
            val landingId = result.fingerprint.ifEmpty { result.id }
            _uiState.value = _uiState.value.copy(
                pendingId = null,
                subscribedIds = _uiState.value.subscribedIds + setOfNotNull(
                    result.id.takeIf { it.isNotEmpty() },
                    result.fingerprint.takeIf { it.isNotEmpty() },
                ),
            )
            _events.send(FindEvent.SubscribeSuccess(landingId))
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
