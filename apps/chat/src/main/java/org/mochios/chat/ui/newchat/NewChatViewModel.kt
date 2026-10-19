// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.model.Friend
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

/** A chat the picked people already share with the caller, offered instead of a duplicate. */
data class ExistingChat(
    val id: String,
    val name: String,
)

data class NewChatUiState(
    val friends: List<Friend> = emptyList(),
    /** The caller's own name, first in a new group's default name as the web has it. */
    val me: String = "",
    val picked: List<Friend> = emptyList(),
    val existing: List<ExistingChat> = emptyList(),
    /** The picked people already have a chat of exactly these members, so Create stays off. */
    val duplicate: Boolean = false,
    val isCheckingExisting: Boolean = false,
    val groupName: String = "",
    /** Whether the user typed [groupName]; until then the default name follows the picks. */
    val groupNameEdited: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdChatId: String? = null
) {
    val selected: Set<String>
        get() = picked.map { friend -> friend.id }.toSet()
}

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
                _uiState.value = _uiState.value.copy(me = data.name)

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
                            picked = listOf(target),
                            error = e.toMochiError(),
                        )
                        refreshExisting()
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

    fun toggleSelect(friend: Friend) {
        val current = _uiState.value.picked
        _uiState.value = _uiState.value.copy(
            picked = if (current.any { picked -> picked.id == friend.id }) {
                current.filterNot { picked -> picked.id == friend.id }
            } else {
                current + friend
            }
        )
        refreshExisting()
    }

    private var chats: List<Chat>? = null
    private val chatMembers = mutableMapOf<String, Set<String>>()
    private var existingJob: Job? = null

    private fun refreshExisting() {
        existingJob?.cancel()
        val picked = _uiState.value.picked
        val direct = picked
            .filter { friend -> friend.chat.isNotBlank() }
            .map { friend -> ExistingChat(id = friend.chat, name = friend.name) }
        if (picked.size < 2) {
            _uiState.value = _uiState.value.copy(
                existing = direct,
                duplicate = direct.isNotEmpty(),
                isCheckingExisting = false,
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            existing = direct,
            duplicate = false,
            isCheckingExisting = true,
        )
        existingJob = viewModelScope.launch {
            val groups = try {
                findGroups(picked.map { friend -> friend.id }.toSet())
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(
                existing = groups + direct,
                duplicate = groups.isNotEmpty(),
                isCheckingExisting = false,
            )
        }
    }

    private suspend fun findGroups(people: Set<String>): List<ExistingChat> {
        val all = chats ?: repository.listChats().also { list -> chats = list }
        val candidates = all.filter { chat ->
            chat.status == ChatStatus.ACTIVE && chat.members == people.size + 1
        }
        return candidates
            .filter { chat ->
                val members = chatMembers[chat.id]
                    ?: repository.getMembers(chat.id)
                        .map { member -> member.id }
                        .toSet()
                        .also { ids -> chatMembers[chat.id] = ids }
                sameGroup(members, people)
            }
            .map { chat -> ExistingChat(id = chat.id, name = chat.name) }
    }

    fun updateGroupName(name: String) {
        _uiState.value = _uiState.value.copy(groupName = name, groupNameEdited = true)
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

    private fun allPeople(): List<Friend> = _uiState.value.picked + _uiState.value.friends

    // Default chat name from the selected people; [fallback] when none resolve.
    fun selectedFallbackName(fallback: String): String {
        val state = _uiState.value
        return if (state.picked.size > 1) {
            groupChatName(state.me, state.picked, fallback)
        } else {
            fallbackChatName(allPeople(), state.selected, fallback)
        }
    }

    fun createChat(fallbackName: String) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.picked.isEmpty() || state.duplicate || state.isCheckingExisting) {
                return@launch
            }
            _uiState.value = state.copy(isCreating = true, error = null)
            try {
                val chosenName = state.groupName
                    .takeIf { state.groupNameEdited }
                    .orEmpty()
                    .ifBlank { fallbackName }
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

/**
 * The default name for a new group as the web gives it: the caller's own [me]
 * first, then the [picked] people by name; [fallback] when none have a name.
 */
internal fun groupChatName(me: String, picked: List<Friend>, fallback: String): String {
    val others = picked
        .map { friend -> friend.name }
        .filter { name -> name.isNotBlank() }
        .sortedWith(NaturalCompare)
    return (listOf(me).filter { name -> name.isNotBlank() } + others)
        .joinToString(", ")
        .ifBlank { fallback }
}

/**
 * Whether a chat with [members] is exactly the caller plus [people]: every
 * picked person is in it and the only one left over is the caller.
 */
internal fun sameGroup(members: Set<String>, people: Set<String>): Boolean =
    members.size == people.size + 1 && members.containsAll(people)
