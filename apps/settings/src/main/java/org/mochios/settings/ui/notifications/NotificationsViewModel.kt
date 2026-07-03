// Copyright © 2026 Mochi OÜ
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.android.notifications.NotificationsUnreadStore
import org.mochios.android.websocket.MochiWebSocket
import javax.inject.Inject

/** Notifications list filter, surfaced as the two top tabs. */
enum class NotificationsTab { UNREAD, ALL }

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<MochiNotification> = emptyList(),
    val unreadCount: Int = 0,
    val tab: NotificationsTab = NotificationsTab.UNREAD,
    val error: MochiError? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val unread: NotificationsUnreadStore,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
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
        }
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
