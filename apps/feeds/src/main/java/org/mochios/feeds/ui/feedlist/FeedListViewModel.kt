// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feedlist

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
import org.mochios.android.util.NaturalCompare
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

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _createError = MutableStateFlow<MochiError?>(null)
    val createError: StateFlow<MochiError?> = _createError.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    // Emits the new feed's id right after a successful create, so the screen can
    // open it (landing on its empty "create the first post" state).
    private val _feedCreated = MutableSharedFlow<String>()
    val feedCreated: SharedFlow<String> = _feedCreated.asSharedFlow()

    private val _currentSort = MutableStateFlow("")
    val currentSort: StateFlow<String> = _currentSort.asStateFlow()

    // Global RSS URL state — null when not yet generated, set once a token
    // has been minted for the chosen mode. Resetting (mode change) wipes it.
    private val _globalRssUrl = MutableStateFlow<String?>(null)
    val globalRssUrl: StateFlow<String?> = _globalRssUrl.asStateFlow()

    private val _rssCopiedMessage = MutableStateFlow<String?>(null)
    val rssCopiedMessage: StateFlow<String?> = _rssCopiedMessage.asStateFlow()

    private val subscriptionIds = mutableListOf<String>()

    init {
        loadFeeds()
        loadGlobalSort()
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

    private fun loadGlobalSort() {
        viewModelScope.launch {
            try {
                _currentSort.value = repository.getGlobalSort()
            } catch (_: Exception) {
                // Non-critical — leave as default.
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
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val info = repository.getFeedsInfo()
                val feedList = info.feeds
                    .sortedWith(compareBy(NaturalCompare) { feed -> feed.name })
                _feeds.value = feedList
                _hasAi.value = info.hasAi
                subscribeToWebSockets(feedList)
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val info = repository.getFeedsInfo()
                val feedList = info.feeds
                    .sortedWith(compareBy(NaturalCompare) { feed -> feed.name })
                _feeds.value = feedList
                _hasAi.value = info.hasAi
                subscribeToWebSockets(feedList)
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun showCreateDialog() {
        _showCreateDialog.value = true
        _createError.value = null
    }

    fun hideCreateDialog() {
        _showCreateDialog.value = false
        _createError.value = null
    }

    fun createFeed(name: String, privacy: String, memories: Boolean) {
        viewModelScope.launch {
            _isCreating.value = true
            _createError.value = null
            try {
                val feed = repository.createFeed(name, privacy, memories)
                _showCreateDialog.value = false
                refresh()
                _feedCreated.emit(feed.fingerprint.ifEmpty { feed.id })
            } catch (e: Exception) {
                _createError.value = e.toMochiError()
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun generateGlobalRssUrl(mode: String) {
        viewModelScope.launch {
            try {
                val token = repository.getRssToken("*", mode)
                val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
                _globalRssUrl.value = "$serverUrl/feeds/-/rss?token=$token"
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun clearGlobalRssUrl() {
        _globalRssUrl.value = null
    }

    fun setRssCopiedMessage(message: String) {
        _rssCopiedMessage.value = message
    }

    fun clearRssCopiedMessage() {
        _rssCopiedMessage.value = null
    }

    fun updateFeedUnreadCount(feedFingerprint: String, delta: Int) {
        _feeds.value = _feeds.value.map { feed ->
            if (feed.fingerprint == feedFingerprint) {
                feed.copy(unread = maxOf(0, feed.unread + delta))
            } else {
                feed
            }
        }
    }

    private fun subscribeToWebSockets(feedList: List<Feed>) {
        unsubscribeAll()
        val serverUrl = sessionManager.getServerUrlBlocking()
        for (feed in feedList) {
            if (feed.fingerprint.isNotEmpty()) {
                val subId = webSocket.subscribe(serverUrl, feed.fingerprint) { event ->
                    // Server event types are slash-namespaced (feeds.star commit
                    // hook + handlers); the old underscore names never matched.
                    when (event.type) {
                        "post/create", "post/delete", "feed/update" -> {
                            viewModelScope.launch { refreshFeedSilently() }
                        }
                    }
                }
                subscriptionIds.add(subId)
            }
        }
    }

    /**
     * Re-fetch the feed list without surfacing a loading or refreshing
     * indicator, so the drawer's unread badges reflect server state after an
     * action elsewhere (e.g. marking a feed read).
     */
    fun refreshSilently() {
        viewModelScope.launch { refreshFeedSilently() }
    }

    private suspend fun refreshFeedSilently() {
        try {
            val info = repository.getFeedsInfo()
            _feeds.value = info.feeds
                .sortedWith(compareBy(NaturalCompare) { feed -> feed.name })
            _hasAi.value = info.hasAi
        } catch (_: Exception) {
            // Silent refresh failure
        }
    }

    private fun unsubscribeAll() {
        subscriptionIds.forEach { webSocket.unsubscribe(it) }
        subscriptionIds.clear()
    }

    override fun onCleared() {
        super.onCleared()
        unsubscribeAll()
    }
}
