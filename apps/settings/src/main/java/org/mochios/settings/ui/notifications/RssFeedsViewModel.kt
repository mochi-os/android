// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.android.notifications.RssFeed
import org.mochios.settings.R
import javax.inject.Inject

data class RssFeedsUiState(
    val isLoading: Boolean = true,
    val feeds: List<RssFeed> = emptyList(),
    val error: MochiError? = null,
    /** The feed just created, whose address shows until it is dismissed. */
    val created: RssFeed? = null,
    /** Actions waiting for the server: "create", or a feed's id for its rename or delete. */
    val busy: Set<String> = emptySet(),
)

/** What an action came to, for the screen to say. */
sealed interface RssFeedsEvent {
    data class Done(@StringRes val message: Int) : RssFeedsEvent

    data class Failure(val error: MochiError) : RssFeedsEvent
}

/**
 * The RSS feeds of the user's notifications, as the web's RSS feeds dialog
 * has them: each with its address to copy, a name to change, a delete, and
 * a "Notify by default" switch that moves on the tap and goes back if the
 * server refuses.
 */
@HiltViewModel
class RssFeedsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val server: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(RssFeedsUiState())
    val uiState: StateFlow<RssFeedsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RssFeedsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RssFeedsEvent> = _events.asSharedFlow()

    init {
        load()
    }

    /** The address an RSS reader subscribes to, as the web builds it. */
    fun address(feed: RssFeed): String = "$server/notifications/rss?token=${feed.token}"

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                _uiState.value = _uiState.value.copy(isLoading = false, feeds = repository.rssFeeds())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /** The list as the server has it now, with no spinner. */
    private suspend fun reload() {
        try {
            _uiState.value = _uiState.value.copy(feeds = repository.rssFeeds())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The list keeps what it shows.
        }
    }

    /**
     * Create a feed named [name], added to every category the user has now
     * when [existing]; [created] then holds it, for its address to show.
     */
    fun create(name: String, existing: Boolean, onCreated: () -> Unit) {
        if ("create" in _uiState.value.busy) return
        work("create") {
            val feed = repository.createRssFeed(name, existing)
            _uiState.value = _uiState.value.copy(created = feed)
            onCreated()
            reload()
        }
    }

    fun dismissCreated() {
        _uiState.value = _uiState.value.copy(created = null)
    }

    /** A blank [name], or the one the feed has, changes nothing. */
    fun rename(feed: RssFeed, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == feed.name) return
        work(feed.id) {
            repository.renameRssFeed(feed.id, trimmed)
            _uiState.value = _uiState.value.copy(
                feeds = _uiState.value.feeds.map { if (it.id == feed.id) it.copy(name = trimmed) else it },
            )
            _events.emit(RssFeedsEvent.Done(R.string.notifications_rss_renamed))
        }
    }

    fun delete(feed: RssFeed) {
        work(feed.id) {
            repository.deleteRssFeed(feed.id)
            _uiState.value = _uiState.value.copy(feeds = _uiState.value.feeds.filterNot { it.id == feed.id })
            _events.emit(RssFeedsEvent.Done(R.string.notifications_rss_deleted))
        }
    }

    /** The switch moves on the tap and goes back, with the error, if the server refuses. */
    fun setEnabled(feed: RssFeed, enabled: Boolean) {
        val value = if (enabled) 1 else 0
        val before = _uiState.value.feeds.firstOrNull { it.id == feed.id }?.enabled ?: return
        setEnabledShown(feed.id, value)
        viewModelScope.launch {
            try {
                repository.enableRssFeed(feed.id, enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A later tap has moved it since, and its own answer settles it.
                if (_uiState.value.feeds.any { it.id == feed.id && it.enabled == value }) setEnabledShown(feed.id, before)
                _events.emit(RssFeedsEvent.Failure(e.toMochiError()))
            }
        }
    }

    private fun setEnabledShown(id: String, enabled: Int) {
        _uiState.value = _uiState.value.copy(
            feeds = _uiState.value.feeds.map { if (it.id == id) it.copy(enabled = enabled) else it },
        )
    }

    /** Runs [action] with [key] busy, saying why if it fails. */
    private fun work(key: String, action: suspend () -> Unit) {
        _uiState.value = _uiState.value.copy(busy = _uiState.value.busy + key)
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(RssFeedsEvent.Failure(e.toMochiError()))
            } finally {
                _uiState.value = _uiState.value.copy(busy = _uiState.value.busy - key)
            }
        }
    }
}
