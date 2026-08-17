// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.auth.SessionManager
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.android.notifications.NotificationsUnreadStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.settings.api.NotifCategory
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
    /**
     * Categories a notification's topic can be moved to, and the topic rows
     * that say which one it is in now. Empty when the settings app could not
     * be read, which simply hides the picker - the list itself comes from the
     * notifications app and must not fail with it.
     */
    val categories: List<NotifCategory> = emptyList(),
    val topics: List<NotifTopic> = emptyList(),
    val error: MochiError? = null,
) {
    /**
     * The topic row a notification belongs to, or null if the server has none.
     * The set-category call requires an existing row - it does not create one -
     * so a notification without one cannot be recategorised yet.
     */
    fun topicFor(notification: MochiNotification): NotifTopic? = topics.firstOrNull {
        it.app == notification.app &&
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

    init {
        load(initial = true)
        subscribeWebSocket()
    }

    fun refresh() = load(initial = false)

    fun setTab(tab: NotificationsTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = initial,
                isRefreshing = !initial,
                error = null,
            )
            try {
                val resp = repository.list()
                _uiState.value = _uiState.value.copy(
                    items = resp.data.sortedByDescending { it.created },
                    unreadCount = resp.count,
                    isLoading = false,
                    isRefreshing = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.toMochiError(),
                )
            }
            loadCategories()
        }
    }

    /**
     * Categories and topic rows for the per-notification picker. Failure is
     * swallowed on purpose: these come from the settings app, the list comes
     * from the notifications app, and losing the picker must not put an error
     * over a list that loaded perfectly well. The picker just does not appear.
     */
    private suspend fun loadCategories() {
        try {
            val categories = prefs.getCategories().unwrapRaw()
            val topics = prefs.getTopics().unwrapRaw()
            _uiState.value = _uiState.value.copy(categories = categories, topics = topics)
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(categories = emptyList(), topics = emptyList())
        }
    }

    /**
     * Move a notification's topic to [categoryId], or to no category when it is
     * null. Applied locally first so the row updates immediately, then reverted
     * if the server refuses.
     */
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
                    app = topic.app,
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

    fun markRead(id: String) {
        viewModelScope.launch {
            try {
                repository.markRead(id)
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items.map {
                        if (it.id == id && it.read == 0L) it.copy(read = System.currentTimeMillis() / 1000) else it
                    },
                )
                unread.refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                repository.markAllRead()
                val now = System.currentTimeMillis() / 1000
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items.map { if (it.read == 0L) it.copy(read = now) else it },
                    unreadCount = 0,
                )
                unread.refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                repository.clearAll()
                _uiState.value = _uiState.value.copy(items = emptyList(), unreadCount = 0)
                unread.refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun subscribeWebSocket() {
        if (serverUrl.isBlank()) return
        subscriptionId = webSocket.subscribe(serverUrl, "notifications") { _ ->
            viewModelScope.launch { load(initial = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }
}
