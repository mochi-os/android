// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feedlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.feeds.model.Feed
import org.mochios.feeds.repository.FeedsRepository
import javax.inject.Inject

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val repository: FeedsRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _feeds = MutableStateFlow<List<Feed>>(emptyList())
    val feeds: StateFlow<List<Feed>> = _feeds.asStateFlow()

    // Whether the server has an AI provider configured; gates the AI sort
    // option offered for every feed.
    private val _hasAi = MutableStateFlow(false)
    val hasAi: StateFlow<Boolean> = _hasAi.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<MochiError?>(null)
    val error: StateFlow<MochiError?> = _error.asStateFlow()

    private val _currentSort = MutableStateFlow("")
    val currentSort: StateFlow<String> = _currentSort.asStateFlow()

    // Global RSS URL state — null when not yet generated, set once a token
    // has been minted for the chosen mode. Resetting (mode change) wipes it.


    private val subscriptionIds = mutableListOf<String>()

    /** The feed keys [subscriptionIds] cover, so an unchanged list keeps its sockets. */
    private var subscribedKeys: Set<String> = emptySet()

    /** The pending or in-flight feeds fetch; cancelled when a newer one starts. */
    private var feedsJob: Job? = null

    private var resumedBefore = false

    init {
        loadFeeds()
        observeSubscriptionChanges()
    }

    // Reload the drawer whenever the viewer subscribes to or unsubscribes from a
    // feed, so the list stays correct even when no navigation recreates this VM.
    private fun observeSubscriptionChanges() {
        viewModelScope.launch {
            repository.subscriptionChanges.collect {
                loadFeeds()
            }
        }
    }

    fun setSort(sort: String) {
        if (_currentSort.value == sort) return
        _currentSort.value = sort
        viewModelScope.launch {
            try {
                repository.setGlobalSort(sort)
            } catch (_: Exception) {
                // Non-critical — UI state already updated.
            }
        }
    }

    fun loadFeeds() {
        feedsJob?.cancel()
        feedsJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val info = repository.getFeedsInfo()
                val feedList = info.feeds
                    .sortedWith(compareBy(NaturalCompare) { feed -> feed.name })
                _feeds.value = feedList
                _hasAi.value = info.hasAi
                if (info.sort.isNotEmpty()) {
                    _currentSort.value = info.sort
                }
                subscribeToWebSockets(feedList)
            } catch (e: Exception) {
                ensureActive()
                _error.value = e.toMochiError()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * The screen's ON_RESUME. The first arrives as the screen opens, while
     * init is already loading, so only a later one - back from a feed, with
     * unread counts to update - refreshes.
     */
    fun onScreenResumed() {
        if (!resumedBefore) {
            resumedBefore = true
            return
        }
        refresh()
    }

    fun refresh() {
        feedsJob?.cancel()
        feedsJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val info = repository.getFeedsInfo()
                val feedList = info.feeds
                    .sortedWith(compareBy(NaturalCompare) { feed -> feed.name })
                _feeds.value = feedList
                _hasAi.value = info.hasAi
                subscribeToWebSockets(feedList)
            } catch (e: Exception) {
                ensureActive()
                _error.value = e.toMochiError()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun subscribeToWebSockets(feedList: List<Feed>) {
        val keys = feedList.map { feed -> feed.fingerprint }
            .filter { key -> key.isNotEmpty() }
            .toSet()
        if (keys == subscribedKeys) {
            return
        }
        unsubscribeAll()
        subscribedKeys = keys
        val serverUrl = sessionManager.getServerUrlBlocking()
        for (feed in feedList) {
            if (feed.fingerprint.isNotEmpty()) {
                val subId = webSocket.subscribe(
                    serverUrl,
                    feed.fingerprint,
                    app = "feeds",
                ) { event ->
                    // Server event types are slash-namespaced (feeds.star commit
                    // hook + handlers); the old underscore names never matched.
                    when (event.type) {
                        "post/create", "post/delete", "feed/update",
                        "feed/removed", "feed/deleted" -> {
                            refreshSilently()
                        }
                    }
                }
                subscriptionIds.add(subId)
            }
        }
    }

    /**
     * Refetch the feeds without a spinner. Each call cancels the pending fetch
     * and waits [REFRESH_DEBOUNCE] first, so a burst of frames across the
     * feeds makes one request.
     */
    fun refreshSilently() {
        feedsJob?.cancel()
        feedsJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            refreshFeedSilently()
        }
    }

    private suspend fun refreshFeedSilently() {
        try {
            val info = repository.getFeedsInfo()
            val feedList = info.feeds
                .sortedWith(compareBy(NaturalCompare) { feed -> feed.name })
            _feeds.value = feedList
            _hasAi.value = info.hasAi
            subscribeToWebSockets(feedList)
        } catch (_: Exception) {
            // Silent refresh failure
        }
    }

    private fun unsubscribeAll() {
        subscriptionIds.forEach { webSocket.unsubscribe(it) }
        subscriptionIds.clear()
        subscribedKeys = emptySet()
    }

    override fun onCleared() {
        super.onCleared()
        unsubscribeAll()
    }
}
