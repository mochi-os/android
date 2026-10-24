// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatAccount
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.model.Friend
import org.mochios.chat.model.addressList
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

/**
 * A chat the picked people already share with the caller, offered to open
 * before making another.
 */
data class ExistingChat(
    val id: String,
    val name: String,
)

data class NewChatUiState(
    val friends: List<Friend> = emptyList(),
    /**
     * Directory people matching the search who aren't friends: they can be
     * added when their chat policy accepts anyone, as the web's picker has it.
     */
    val directory: List<Friend> = emptyList(),
    /** The caller's own name, first in a new group's default name as the web has it. */
    val me: String = "",
    val picked: List<Friend> = emptyList(),
    val existing: List<ExistingChat> = emptyList(),
    /**
     * The one person picked already has a 1:1 chat with the caller, so Create
     * stays off; a group may share its people with another, as the web allows.
     */
    val duplicate: Boolean = false,
    val groupName: String = "",
    /** Whether the user typed [groupName]; until then the default name follows the picks. */
    val groupNameEdited: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSearchingDirectory: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdChatId: String? = null,
    /** The user's accounts on other networks a conversation can be had through. */
    val accounts: List<ChatAccount> = emptyList(),
    /** The account chosen, with people there by their addresses; empty for Mochi. */
    val account: String = "",
    val addresses: String = "",
) {
    val selected: Set<String>
        get() = picked.map { friend -> friend.id }.toSet()

    /** The account chosen, when one is. */
    val chosen: ChatAccount? get() = accounts.firstOrNull { it.id == account }
}

@HiltViewModel
class NewChatViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val repository: ChatRepository,
) : ViewModel() {

    /** Friend identity pre-selected via deep-link (mochi://chat/with?friend=X). */
    private val preselectFriend: String = savedStateHandle["friend"] ?: ""

    /**
     * A contact's messaging address on another network, from people
     * (mochi://chat/with?account=A&address=B&name=C): the conversation there
     * already is, or a new one.
     */
    private val handoffAccount: String = savedStateHandle["account"] ?: ""
    private val handoffAddress: String = savedStateHandle["address"] ?: ""
    private val handoffName: String = savedStateHandle["name"] ?: ""

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
                _uiState.value = _uiState.value.copy(me = data.name, accounts = data.accounts)

                if (handoffAccount.isNotBlank() && handoffAddress.isNotBlank()) {
                    try {
                        val chatId = repository.createChat(handoffName.trim(), listOf(handoffAddress), handoffAccount).id
                        _uiState.value = _uiState.value.copy(friends = friends, createdChatId = chatId.ifEmpty { null }, isLoading = chatId.isNotEmpty())
                    } catch (e: Exception) {
                        // The picker, on that account with the address filled
                        // in, so the user can retry.
                        _uiState.value = _uiState.value.copy(
                            friends = friends,
                            isLoading = false,
                            account = handoffAccount,
                            addresses = handoffAddress,
                            groupName = handoffName,
                            groupNameEdited = handoffName.isNotBlank(),
                            error = e.toMochiError(),
                        )
                    }
                    return@launch
                }

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
            )
            return
        }
        _uiState.value = _uiState.value.copy(existing = direct, duplicate = false)
        existingJob = viewModelScope.launch {
            val groups = try {
                findGroups(picked.map { friend -> friend.id }.toSet())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(existing = groups + direct)
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

    /** Choose the account the conversation is had through; empty for Mochi. */
    fun selectAccount(account: String) {
        _uiState.value = _uiState.value.copy(account = account, picked = emptyList())
        refreshExisting()
    }

    fun updateAddresses(text: String) {
        _uiState.value = _uiState.value.copy(addresses = text)
    }

    /** Start a conversation on the account chosen with the people at the addresses given. */
    fun createRemote() {
        val state = _uiState.value
        val addresses = addressList(state.addresses)
        if (state.account.isEmpty() || addresses.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = state.copy(isCreating = true, error = null)
            try {
                val response = repository.createChat(state.groupName.trim(), addresses, state.account)
                _uiState.value = _uiState.value.copy(isCreating = false, createdChatId = response.id.ifEmpty { null })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false, error = e.toMochiError())
            }
        }
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
            delay(SEARCH_DEBOUNCE)
            _uiState.value = _uiState.value.copy(isSearchingDirectory = true)
            val directory = try {
                val friendIds = _uiState.value.friends.map { friend -> friend.id }.toSet()
                repository.personSearch(trimmed)
                    .filter { person -> person.id !in friendIds }
                    .map { person ->
                        Friend(id = person.id, identity = person.id, name = person.name, chat = "")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(directory = directory, isSearchingDirectory = false)
        }
    }

    /**
     * Friends matching the search first, then the directory's matches who
     * aren't friends; with no search, the friend list unchanged.
     */
    fun filteredFriends(): List<Friend> {
        val query = _uiState.value.searchQuery.lowercase().trim()
        val friends = if (query.isEmpty()) {
            _uiState.value.friends
        } else {
            _uiState.value.friends.filter { friend -> friend.name.lowercase().contains(query) }
        }
        val seen = friends.map { friend -> friend.id }.toSet()
        return friends + _uiState.value.directory.filter { person -> person.id !in seen }
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
            if (state.picked.isEmpty() || state.duplicate) {
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
 * and the [picked] people's names, all sorted alphabetically; [fallback] when
 * none have a name.
 */
internal fun groupChatName(me: String, picked: List<Friend>, fallback: String): String =
    (listOf(me) + picked.map { friend -> friend.name })
        .filter { name -> name.isNotBlank() }
        .sortedWith(NaturalCompare)
        .joinToString(", ")
        .ifBlank { fallback }

/**
 * Whether a chat with [members] is exactly the caller plus [people]: every
 * picked person is in it and the only one left over is the caller.
 */
internal fun sameGroup(members: Set<String>, people: Set<String>): Boolean =
    members.size == people.size + 1 && members.containsAll(people)
