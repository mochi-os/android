// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.find

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
import org.mochios.feeds.model.Feed
import org.mochios.feeds.repository.FeedsRepository
import javax.inject.Inject

@HiltViewModel
class FindFeedsViewModel @Inject constructor(
    private val repository: FeedsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Feed>>(emptyList())
    val searchResults: StateFlow<List<Feed>> = _searchResults.asStateFlow()

    private val _recommendations = MutableStateFlow<List<Feed>>(emptyList())
    val recommendations: StateFlow<List<Feed>> = _recommendations.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingRecommendations = MutableStateFlow(false)
    val isLoadingRecommendations: StateFlow<Boolean> = _isLoadingRecommendations.asStateFlow()

    private val _error = MutableStateFlow<MochiError?>(null)
    val error: StateFlow<MochiError?> = _error.asStateFlow()

    private val _subscribingFeed = MutableStateFlow<String?>(null)
    val subscribingFeed: StateFlow<String?> = _subscribingFeed.asStateFlow()

    private val _subscribedFeeds = MutableStateFlow<Set<String>>(emptySet())
    val subscribedFeeds: StateFlow<Set<String>> = _subscribedFeeds.asStateFlow()

    private val _probeResult = MutableStateFlow<Feed?>(null)
    val probeResult: StateFlow<Feed?> = _probeResult.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    // One-shot: the feed to open after a successful subscribe. The screen
    // navigates into that feed, whose screen reloads and shows the suggestions.
    private val _navigateToFeed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToFeed: SharedFlow<String> = _navigateToFeed.asSharedFlow()

    private var searchJob: Job? = null

    init {
        loadRecommendations()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _probeResult.value = null
            _isSearching.value = false
            _isProbing.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            if (looksLikeUrl(query)) {
                probe(query)
                _searchResults.value = emptyList()
            } else {
                _probeResult.value = null
                search(query)
            }
        }
    }

    private fun looksLikeUrl(query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
    }

    private suspend fun search(query: String) {
        _isSearching.value = true
        try {
            _searchResults.value = repository.searchDirectory(query)
        } catch (e: Exception) {
            _error.value = e.toMochiError()
        } finally {
            _isSearching.value = false
        }
    }

    private suspend fun probe(url: String) {
        _isProbing.value = true
        try {
            val result = repository.probeUrl(url.trim())
            _probeResult.value = result.feed
        } catch (_: Exception) {
            _probeResult.value = null
        } finally {
            _isProbing.value = false
        }
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            _isLoadingRecommendations.value = true
            try {
                _recommendations.value = repository.getRecommendations()
            } catch (_: Exception) {
                // Non-critical
            } finally {
                _isLoadingRecommendations.value = false
            }
        }
    }

    fun subscribe(feed: Feed) {
        val feedId = feed.fingerprint.ifEmpty { feed.id }
        if (feedId.isEmpty()) return

        viewModelScope.launch {
            _subscribingFeed.value = feedId
            try {
                // The subscribe request identifies the feed by its full id; the
                // fingerprint-preferred [feedId] is only the local tracking key.
                repository.subscribeFeed(feed.id.ifEmpty { feed.fingerprint }, feed.server)
                _subscribedFeeds.value = _subscribedFeeds.value + feedId
                // Fetch interest suggestions and stash them BEFORE navigating, so
                // they're ready when the feed screen opens — and not cancelled
                // when this screen (and its ViewModel scope) is popped.
                try {
                    val suggestions = repository.getSuggestedInterests(feedId)
                    if (suggestions.isNotEmpty()) {
                        repository.setPendingInterestSuggestion(feedId, suggestions)
                    }
                } catch (_: Exception) { }
                // Open the just-subscribed feed; its screen reloads and shows the
                // suggestions prompt.
                _navigateToFeed.emit(feedId)
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _subscribingFeed.value = null
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
