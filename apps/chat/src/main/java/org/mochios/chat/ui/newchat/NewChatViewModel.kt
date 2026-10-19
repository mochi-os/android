// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

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
import org.mochios.chat.model.Friend
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

data class NewChatUiState(
    val friends: List<Friend> = emptyList(),
    val selected: Set<String> = emptySet(),
    val groupName: String = "",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdChatId: String? = null
)

@HiltViewModel
class NewChatViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val repository: ChatRepository,
) : ViewModel() {

    /** Friend identity pre-selected via deep-link (mochi://chat/with?friend=X). */
    private val preselectFriend: String = savedStateHandle["friend"] ?: ""

    private val _uiState = MutableStateFlow(NewChatUiState())
    val uiState: StateFlow<NewChatUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = repository.getNewChatData()
                val friends = data.friends.sortedWith(compareBy(NaturalCompare) { it.name })

                // Deep-link entry with a friend: open or create the 1-on-1
                // directly. isLoading stays true so the picker never flashes
                // before the screen navigates away.
                val target = preselectFriend
                    .takeIf { it.isNotBlank() }
                    ?.let { id -> friends.firstOrNull { it.id == id } }
                if (target != null) {
                    try {
                        val chatId = target.chat.ifBlank {
                            repository.createChat(target.name, listOf(target.id)).id
                        }
                        _uiState.value = _uiState.value.copy(friends = friends, createdChatId = chatId)
                    } catch (e: Exception) {
                        // Create failed: drop the user onto the picker with the friend
                        // pre-selected and the error shown, so they can retry instead of
                        // hitting a dead-end error screen.
                        _uiState.value = _uiState.value.copy(
                            friends = friends,
                            isLoading = false,
                            selected = setOf(target.id),
                            error = e.toMochiError(),
                        )
                    }
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    friends = friends,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun toggleSelect(friendId: String) {
        val current = _uiState.value.selected
        _uiState.value = _uiState.value.copy(
            selected = if (friendId in current) current - friendId else current + friendId
        )
    }

    fun updateGroupName(name: String) {
        _uiState.value = _uiState.value.copy(groupName = name)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    // Only friends can be added to a chat, so the search filters the friend
    // list on the device and never asks the server's directory.
    fun filteredFriends(): List<Friend> {
        val query = _uiState.value.searchQuery.lowercase().trim()
        return if (query.isEmpty()) {
            _uiState.value.friends
        } else {
            _uiState.value.friends.filter { friend -> friend.name.lowercase().contains(query) }
        }
    }

    private fun allPeople(): List<Friend> = _uiState.value.friends

    // Default chat name from the selected people; [fallback] when none resolve.
    fun selectedFallbackName(fallback: String): String =
        fallbackChatName(allPeople(), _uiState.value.selected, fallback)

    fun createChat(fallbackName: String) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.selected.isEmpty()) return@launch
            _uiState.value = state.copy(isCreating = true, error = null)
            try {
                val chosenName = state.groupName.ifBlank { fallbackName }
                val response = repository.createChat(chosenName, state.selected.toList())
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdChatId = response.id
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false, error = e.toMochiError())
            }
        }
    }

    fun consumeCreatedChat() {
        _uiState.value = _uiState.value.copy(createdChatId = null)
    }

    fun openExistingChat(chatId: String) {
        _uiState.value = _uiState.value.copy(createdChatId = chatId)
    }
}

/**
 * The default name for a new chat: the selected people's names, or the
 * caller's translated [fallback] when none of them resolve. The name is
 * stored server-side and shown to every member, so it is never an English
 * literal.
 */
internal fun fallbackChatName(people: List<Friend>, selected: Set<String>, fallback: String): String {
    val names = people.filter { it.id in selected }.joinToString(", ") { it.name }
    return names.ifBlank { fallback }
}
