// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationCategory
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.android.notifications.NotificationsUnreadStore
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.settings.api.NotifTopic
import org.mochios.settings.api.NotificationPrefsApi
import javax.inject.Inject

/** Notifications list filter, surfaced as the two top tabs. */
enum class NotificationsTab { UNREAD, ALL }

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<MochiNotification> = emptyList(),
    val unreadCount: Int = 0,
    val tab: NotificationsTab = NotificationsTab.UNREAD,
    val categories: List<NotificationCategory> = emptyList(),
    val topics: List<NotifTopic> = emptyList(),
    val error: MochiError? = null,
) {
    /**
     * Null when the server has no topic row; set-category requires an existing
     * row, so such a notification cannot be recategorised.
     */
    fun topicFor(notification: MochiNotification): NotifTopic? = topics.firstOrNull {
        it.app.id == notification.app &&
            it.topic == notification.topic &&
            it.`object` == notification.`object`
    }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val unread: NotificationsUnreadStore,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val prefs: NotificationPrefsApi,
) : ViewModel() {

    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private var subscriptionId: String? = null

    /** The pending or in-flight list fetch; cancelled when a newer one starts. */
    private var loadJob: Job? = null

    /**
     * How many frames have been applied. A list fetched while a frame landed
     * answers from before that frame and would undo it, so it is fetched again
     * instead.
     */
    private var frames = 0

    init {
        load(initial = true)
        subscribeWebSocket()
    }

    fun refresh() = load(initial = false)

    fun setTab(tab: NotificationsTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    private fun load(initial: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            fetch(initial)
        }
    }

    /**
     * Refetch for a socket frame that cannot be applied. Cancels the pending
     * fetch and waits [REFRESH_DEBOUNCE] first, so a burst of frames makes one
     * round of calls.
     */
    private fun refreshLatest() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            fetch(initial = false)
        }
    }

    private suspend fun fetch(initial: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLoading = initial,
            isRefreshing = !initial,
            error = null,
        )
        try {
            val seen = frames
            val resp = repository.list()
            if (frames != seen) {
                refreshLatest()
                return
            }
            val (items, count) = overlay(resp.data, resp.count)
            _uiState.value = _uiState.value.copy(
                items = items,
                unreadCount = count,
                isLoading = false,
                isRefreshing = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = e.toMochiError(),
            )
        }
        loadCategories()
    }

    /**
     * Failure is swallowed: the picker comes from the settings app and must not
     * put an error over a list the notifications app loaded.
     */
    private suspend fun loadCategories() {
        try {
            val categories = prefs.getCategories().unwrapRaw()
            val topics = prefs.getTopics().unwrapRaw()
            _uiState.value = _uiState.value.copy(categories = categories, topics = topics)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            _uiState.value = _uiState.value.copy(categories = emptyList(), topics = emptyList())
        }
    }

    fun setCategory(topic: NotifTopic, categoryId: String?) {
        viewModelScope.launch {
            val before = _uiState.value.topics
            _uiState.value = _uiState.value.copy(
                topics = before.map {
                    if (it.app == topic.app && it.topic == topic.topic && it.`object` == topic.`object`) {
                        it.copy(category = categoryId)
                    } else {
                        it
                    }
                },
            )
            try {
                prefs.setTopicCategory(
                    app = topic.app.id,
                    topic = topic.topic,
                    obj = topic.`object`,
                    category = categoryId ?: "",
                ).unwrapEmpty()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(topics = before, error = e.toMochiError())
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * A click's rows, shown read before the server answers. [stamp] is the
     * read time they were given; [taken] is what the click has taken off the
     * unread count, which a refusal adds back.
     */
    private class Marked(val ids: Set<String>, val stamp: Long, var taken: Int)

    /** Clicks the server has not answered yet, oldest first. */
    private val marked = mutableListOf<Marked>()

    /**
     * A fetched list with every row a pending click covers still read, so a
     * list fetched before the server had the read does not undo the click.
     */
    private fun overlay(rows: List<MochiNotification>, count: Int): Pair<List<MochiNotification>, Int> {
        marked.forEach { click -> click.taken = 0 }
        val shown = rows.map { row ->
            val click = marked.firstOrNull { row.id in it.ids }
            if (click == null || row.read != 0L) {
                row
            } else {
                click.taken++
                row.copy(read = click.stamp)
            }
        }
        return shown to (count - marked.sumOf { it.taken }).coerceAtLeast(0)
    }

    /** One row read on the click; it goes back to unread if the server refuses. */
    fun markRead(id: String) {
        val state = _uiState.value
        val taken = if (state.items.any { it.id == id && it.read == 0L } && state.unreadCount > 0) 1 else 0
        mark(setOf(id), taken) { repository.markRead(id) }
    }

    /** Every row read on the click; those it marked go back to unread if the server refuses. */
    fun markAllRead() {
        val state = _uiState.value
        val ids = state.items.filter { it.read == 0L }.map { it.id }.toSet()
        mark(ids, taken = state.unreadCount) { repository.markAllRead() }
    }

    /**
     * Marks [ids] read for the click. Its frame, when the server sends it
     * back, finds the rows read already.
     */
    private fun mark(ids: Set<String>, taken: Int, save: suspend () -> Unit) {
        val state = _uiState.value
        val click = Marked(ids, System.currentTimeMillis() / 1000, taken)
        marked += click
        _uiState.value = state.copy(
            items = state.items.map { if (it.id in ids && it.read == 0L) it.copy(read = click.stamp) else it },
            unreadCount = (state.unreadCount - taken).coerceAtLeast(0),
        )
        viewModelScope.launch {
            try {
                save()
                marked -= click
                unread.refreshLatest()
            } catch (e: CancellationException) {
                marked -= click
                throw e
            } catch (e: Exception) {
                marked -= click
                // Only the rows still showing this click's stamp go back; one a
                // later click also covers stays read until that one answers.
                val now = _uiState.value
                _uiState.value = now.copy(
                    items = now.items.map { row ->
                        if (row.id in ids && row.read == click.stamp && marked.none { row.id in it.ids }) {
                            row.copy(read = 0L)
                        } else {
                            row
                        }
                    },
                    unreadCount = now.unreadCount + click.taken,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                repository.clearAll()
                _uiState.value = _uiState.value.copy(items = emptyList(), unreadCount = 0)
                unread.refreshLatest()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun subscribeWebSocket() {
        if (serverUrl.isBlank()) return
        subscriptionId = webSocket.subscribe(
            serverUrl,
            "notifications",
            app = "notifications",
        ) { event ->
            // The socket calls back on its own thread; the list changes on the
            // main one, as everything else that changes it does.
            viewModelScope.launch { framed(event) }
        }
    }

    /**
     * A frame applied to the list held, as the web applies it to its cache, so
     * the list ends as the server's would without being read again. The list
     * is oldest first and a frame here changes rows in place or removes them,
     * so the order holds. The user's own read or clear comes back as a frame
     * too, and finds the rows as it left them.
     *
     * The list is fetched for a frame that cannot be applied exactly: a new or
     * changed row, whose frame carries no title and does not say whether this
     * device lists it; an object's clear that changed rows, which reads them
     * or deletes them and does not say which; a change to the categories,
     * which decide what this device lists; a reopened socket, which may have
     * missed frames; and any frame this client does not know.
     */
    private fun framed(event: WebSocketEvent) {
        when (event.type) {
            "read" -> {
                val id = event.id
                if (id == null) {
                    refreshLatest()
                    return
                }
                frames++
                val state = _uiState.value
                if (state.items.none { it.id == id && it.read == 0L }) return
                val now = System.currentTimeMillis() / 1000
                _uiState.value = state.copy(
                    items = state.items.map { if (it.id == id) it.copy(read = now) else it },
                    unreadCount = (state.unreadCount - 1).coerceAtLeast(0),
                )
            }
            "read_all" -> {
                frames++
                val state = _uiState.value
                val now = System.currentTimeMillis() / 1000
                _uiState.value = state.copy(
                    items = state.items.map { if (it.read == 0L) it.copy(read = now) else it },
                    unreadCount = 0,
                )
            }
            "clear_all" -> {
                frames++
                _uiState.value = _uiState.value.copy(items = emptyList(), unreadCount = 0)
            }
            // Sent on every open of a feed, forum, chat or game: one that
            // changed nothing leaves the list as it is.
            "clear_object" -> if (event.changed != 0L) refreshLatest()
            else -> refreshLatest()
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }
}
