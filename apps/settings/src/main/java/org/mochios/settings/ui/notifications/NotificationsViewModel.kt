// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import android.os.SystemClock
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

    /** The frame this user's own read or clear echoes back as, already shown
     *  in the list, so its arrival need not fetch the list again. */
    @Volatile
    private var echo: Echo? = null

    /** When [echo] stops being expected, on the [SystemClock.elapsedRealtime] clock. */
    @Volatile
    private var echoUntil = 0L

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
     * Refetch for a socket frame. Cancels the pending fetch and waits
     * [REFRESH_DEBOUNCE] first, so a burst of frames - our own mark-read or
     * clear echoes back as one - makes one round of calls.
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
            val resp = repository.list()
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
        mark(setOf(id), taken, Echo("read", id)) { repository.markRead(id) }
    }

    /** Every row read on the click; those it marked go back to unread if the server refuses. */
    fun markAllRead() {
        val state = _uiState.value
        val ids = state.items.filter { it.read == 0L }.map { it.id }.toSet()
        mark(ids, taken = state.unreadCount, Echo("read_all")) { repository.markAllRead() }
    }

    /** Marks [ids] read for the click, expecting [expected] back from the server. */
    private fun mark(ids: Set<String>, taken: Int, expected: Echo, save: suspend () -> Unit) {
        val state = _uiState.value
        val click = Marked(ids, System.currentTimeMillis() / 1000, taken)
        marked += click
        expectEcho(expected)
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
                echo = null
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
                expectEcho(Echo("clear_all"))
                repository.clearAll()
                _uiState.value = _uiState.value.copy(items = emptyList(), unreadCount = 0)
                unread.refreshLatest()
            } catch (e: Exception) {
                echo = null
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
            if (!isOwnEcho(event)) {
                refreshLatest()
            }
        }
    }

    /** Expect [expected] back from the server for an action whose result the
     *  list already shows. */
    private fun expectEcho(expected: Echo) {
        echoUntil = SystemClock.elapsedRealtime() + ECHO_WINDOW
        echo = expected
    }

    /** Whether [event] is the expected echo, which is then spent. A "read"
     *  must also name the notification this user marked: one from another
     *  device in the same window is someone else's change. */
    private fun isOwnEcho(event: WebSocketEvent): Boolean {
        val expected = echo ?: return false
        if (event.type != expected.type || SystemClock.elapsedRealtime() > echoUntil) {
            return false
        }
        if (expected.id != null && expected.id != event.id) {
            return false
        }
        echo = null
        return true
    }

    /** A frame [type] expected back, and for a "read" the notification's [id]. */
    private data class Echo(val type: String, val id: String? = null)

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }

    private companion object {
        /** How long after an action its echo is still taken as its own. */
        const val ECHO_WINDOW = 5_000L
    }
}
