// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    // Directory people matching the current query who aren't already friends.
    // Non-friends may be addressed; the sender-side probe at create decides
    // whether their chat_policy allows it (mirrors the web picker).
    val directory: List<Friend> = emptyList(),
    val selected: Set<String> = emptySet(),
    val groupName: String = "",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSearchingDirectory: Boolean = false,
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

                // Deep-link entry (mochi://chat/with?friend=X): skip the picker
                // and drop straight into the 1-on-1. Reuse the friend's existing
                // chat if there is one, otherwise create it, then forward to the
                // conversation. isLoading stays true so the picker never flashes
                // before the screen navigates away.
                val target = preselectFriend
                    .takeIf { it.isNotBlank() }
                    ?.let { id -> friends.firstOrNull { it.id == id } }
                if (target != null) {
                    try {
                        val chatId = target.chatId.ifBlank {
                            val response = repository.createChat(target.name, listOf(target.id))
                            response.fingerprint.ifEmpty { response.id }
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

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.value = _uiState.value.copy(directory = emptyList(), isSearchingDirectory = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearchingDirectory = true)
            try {
                val friendIds = _uiState.value.friends.map { it.id }.toSet()
                val results = repository.personSearch(trimmed)
                    .filter { it.id !in friendIds }
                    .map { Friend(id = it.id, identity = it.id, name = it.name, chatId = "") }
                _uiState.value = _uiState.value.copy(directory = results, isSearchingDirectory = false)
            } catch (e: Exception) {
                // A failed directory search leaves the local matches; not fatal.
                _uiState.value = _uiState.value.copy(directory = emptyList(), isSearchingDirectory = false)
            }
        }
    }

    // Friends matching the query first, then directory (non-friend) matches -
    // deduped by id. With no query the picker shows the friend list unchanged.
    fun filteredFriends(): List<Friend> {
        val query = _uiState.value.searchQuery.lowercase().trim()
        val friends = if (query.isEmpty()) _uiState.value.friends
        else _uiState.value.friends.filter { it.name.lowercase().contains(query) }
        val seen = friends.map { it.id }.toSet()
        return friends + _uiState.value.directory.filter { it.id !in seen }
    }

    // Every selectable person (friends + current directory results) - used for
    // resolving selected ids to display names.
    private fun allPeople(): List<Friend> = _uiState.value.friends + _uiState.value.directory

    // Default chat name from the selected people (handles directory picks,
    // which aren't in the friend list). Falls back to "Chat" when unresolved.
    fun selectedFallbackName(): String {
        val selected = _uiState.value.selected
        val names = allPeople().filter { it.id in selected }.joinToString(", ") { it.name }
        return names.ifBlank { "Chat" }
    }

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
                    createdChatId = response.fingerprint.ifEmpty { response.id }
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
