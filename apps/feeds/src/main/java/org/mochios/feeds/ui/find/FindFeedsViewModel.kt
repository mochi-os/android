// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.find

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Lookup
import org.mochios.feeds.model.Person
import org.mochios.feeds.repository.FeedsRepository
import org.mochios.feeds.repository.PermissionRequiredException
import javax.inject.Inject

@HiltViewModel
class FindFeedsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
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

    /**
     * A handle or link looked up on another network: a person or community a
     * feed may follow, or a post there. Null while nothing is found.
     */
    private val _lookup = MutableStateFlow<Lookup?>(null)
    val lookup: StateFlow<Lookup?> = _lookup.asStateFlow()

    private val _isLookingUp = MutableStateFlow(false)
    val isLookingUp: StateFlow<Boolean> = _isLookingUp.asStateFlow()

    private var lookupJob: Job? = null

    /** The user's contacts with a handle on another network, to follow. */
    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    /** The feeds the user owns, one of which follows a person found here. */
    private val _owned = MutableStateFlow<List<Feed>>(emptyList())
    val owned: StateFlow<List<Feed>> = _owned.asStateFlow()

    /** The person being followed, by the query that names them. */
    private val _following = MutableStateFlow<String?>(null)
    val following: StateFlow<String?> = _following.asStateFlow()

    private val _followed = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** A follow was added, with how many posts it brought in. */
    val followed: SharedFlow<Int> = _followed.asSharedFlow()

    /** A follow that needs the app allowed a permission first, and what to retry. */
    private val _pendingPermission = MutableStateFlow<PendingFollow?>(null)
    val pendingPermission: StateFlow<PendingFollow?> = _pendingPermission.asStateFlow()

    init {
        loadRecommendations()
        loadSubscribed()
        loadPeople()
        // Arriving with a share link resolves it straight away, as if pasted.
        savedStateHandle.get<String>("link")?.takeIf { it.isNotBlank() }?.let { setSearchQuery(it) }
    }

    /**
     * Seed the subscribed set from the user's feeds, keyed on both fingerprint
     * and id since a directory hit can carry either.
     */
    private fun loadSubscribed() {
        viewModelScope.launch {
            try {
                val feeds = repository.listFeeds()
                _owned.value = feeds.filter { it.owner == 1 }.sortedWith(compareBy(NaturalCompare) { it.name })
                _subscribedFeeds.value = _subscribedFeeds.value + feeds.flatMap { feed ->
                    listOf(feed.fingerprint, feed.id).filter { handle -> handle.isNotEmpty() }
                }
            } catch (_: Exception) {
                // Non-critical: the rows just fall back to offering Subscribe.
            }
        }
    }

    private fun loadPeople() {
        viewModelScope.launch {
            try {
                _people.value = repository.people().sortedWith(compareBy(NaturalCompare) { it.name.ifEmpty { it.handle } })
            } catch (_: Exception) {
                // A server without the networks has nobody to offer.
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        lookupJob?.cancel()
        _lookup.value = null
        _isLookingUp.value = false
        if (looksUpElsewhere(query)) {
            lookupJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE)
                _isLookingUp.value = true
                try {
                    _lookup.value = repository.lookup(query.trim())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Nothing there by that name.
                } finally {
                    _isLookingUp.value = false
                }
            }
        }
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _probeResult.value = null
            _isSearching.value = false
            _isProbing.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            if (isProbeLink(query)) {
                probe(query)
                _searchResults.value = emptyList()
            } else {
                _probeResult.value = null
                search(query)
            }
        }
    }


    private suspend fun search(query: String) {
        _isSearching.value = true
        try {
            _searchResults.value = repository.searchDirectory(query)
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
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
                repository.subscribeFeed(feed.id.ifEmpty { feed.fingerprint }, feed.server, feed.peer)
                _subscribedFeeds.value = _subscribedFeeds.value + feedId
                // Open the just-subscribed feed.
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

    /** Follow a person or community on another network into one of the user's feeds. */
    fun follow(feed: Feed, query: String) {
        if (_following.value != null || query.isBlank()) return
        val feedId = feed.fingerprint.ifEmpty { feed.id }
        _following.value = query
        viewModelScope.launch {
            try {
                val result = repository.addSource(feedId, query, "person")
                _people.value = _people.value.map { if (it.id == query || it.handle == query) it.copy(followed = true) else it }
                _followed.emit(result.ingested)
            } catch (e: PermissionRequiredException) {
                val name = try {
                    repository.permissionName(e.permission)
                } catch (_: Exception) {
                    e.permission
                }
                _pendingPermission.value = PendingFollow(e.app, e.permission, name, feed, query)
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _following.value = null
            }
        }
    }

    /** Grant the permission a follow asked for, then follow again. */
    fun allowPendingPermission() {
        val pending = _pendingPermission.value ?: return
        _pendingPermission.value = null
        viewModelScope.launch {
            try {
                repository.grantPermission(pending.app, pending.permission)
                follow(pending.feed, pending.query)
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun denyPendingPermission() {
        _pendingPermission.value = null
    }
}

/** A follow waiting on a permission: what the app asks for, and the follow to retry. */
data class PendingFollow(
    val app: String,
    val permission: String,
    val name: String,
    val feed: Feed,
    val query: String,
)

/**
 * Whether a search names someone or something on another network: a handle,
 * a did, a link, or a bare domain - never a Mochi share link or a feeds page.
 */
internal fun looksUpElsewhere(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty() || q.any { it.isWhitespace() } || q.startsWith("mochi:", ignoreCase = true) || q.contains("/feeds/")) {
        return false
    }
    return Regex("^(https?://|did:|[@!])", RegexOption.IGNORE_CASE).containsMatchIn(q) ||
        Regex("^[^@/]+@[^@/]+\\.[^@/]+$").matches(q) ||
        Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$", RegexOption.IGNORE_CASE).matches(q)
}

/** Whether a search is a link to resolve through the probe rather than a name
 *  to look up: a web URL, or a mochi:// share link, which reaches a private
 *  feed no directory lists. */
internal fun isProbeLink(query: String): Boolean {
    val trimmed = query.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("mochi://", ignoreCase = true)
}
