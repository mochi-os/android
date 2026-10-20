// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.model.Chat
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

/** Which chats the drawer list shows, as the web's chips above its chat list. */
enum class ChatFilter { ALL, UNREAD, GROUPS }

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val query: String = "",
    val filter: ChatFilter = ChatFilter.ALL,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val pinnedStore: PinnedChatsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    /** Locally pinned chat keys, for pinning to the top of the drawer list. */
    val pinned: StateFlow<Set<String>> = pinnedStore.pinned

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val chats = repository.listChats()
                _uiState.value = _uiState.value.copy(chats = chats, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val chats = repository.listChats()
                _uiState.value = _uiState.value.copy(chats = chats, isRefreshing = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setFilter(filter: ChatFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun filteredChats(): List<Chat> {
        val state = _uiState.value
        val filtered = state.chats.filter { chat ->
            matchesChatFilter(chat, state.query, state.filter)
        }
        val pinnedKeys = pinnedStore.pinned.value
        // Pinned chats first, then most recent activity; chats with no activity
        // yet (updated=0) sink to the bottom in name order so they remain
        // reachable but don't outrank chats with real messages.
        return filtered.sortedWith(
            compareByDescending<Chat> { chat -> chat.id in pinnedKeys }
                .thenByDescending { chat -> chat.updated }
                .thenBy(NaturalCompare) { chat -> chat.name }
        )
    }

    fun deleteLeftChat(chatId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChat(chatId)
                _uiState.value = _uiState.value.copy(
                    chats = _uiState.value.chats.filterNot { it.id == chatId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}

/**
 * The unread badge for a drawer row. The open conversation is being read as
 * it is shown, so its row carries none even before the list refetches.
 */
internal fun unreadBadge(chat: Chat, open: String): Int = if (chat.id == open) 0 else chat.unread

/**
 * Whether [chat] belongs in the list for the typed [query], matched against
 * its name, and the chosen [filter]: unread messages, or more than two members.
 */
internal fun matchesChatFilter(chat: Chat, query: String, filter: ChatFilter): Boolean {
    val term = query.trim()
    if (term.isNotEmpty() && !chat.name.contains(term, ignoreCase = true)) {
        return false
    }
    return when (filter) {
        ChatFilter.ALL -> true
        ChatFilter.UNREAD -> chat.unread > 0
        ChatFilter.GROUPS -> chat.members > 2
    }
}
