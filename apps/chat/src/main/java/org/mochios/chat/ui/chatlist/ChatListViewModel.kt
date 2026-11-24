// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chatlist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.REOPEN_GRACE
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.REOPEN
import org.mochios.chat.R
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.data.UnreadChatsStore
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.model.USER_KEY
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject
import kotlin.time.Duration

/** Which chats the drawer list shows, as the web's chips above its chat list. */
enum class ChatFilter { ALL, UNREAD, GROUPS }

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val query: String = "",
    val filter: ChatFilter = ChatFilter.ALL,
    /** Whether the list has been read at all, so an empty one is known to be empty. */
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    /** Why the list could not be read. */
    val error: MochiError? = null,
)

/** What the drawer shows in place of a list it has no chats for. */
enum class ChatListShown { LOADING, FAILED, EMPTY, LISTED }

/** The drawer's state: the list, or why there is none to show, a read under way included. */
internal fun ChatListUiState.shown(): ChatListShown = when {
    chats.isNotEmpty() -> ChatListShown.LISTED
    error != null -> ChatListShown.FAILED
    !loaded || isLoading -> ChatListShown.LOADING
    else -> ChatListShown.EMPTY
}

/** What a list menu action came to, for the screen to say. */
sealed interface ChatListEvent {
    data class Done(@StringRes val message: Int) : ChatListEvent

    data class Failure(val error: MochiError) : ChatListEvent
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val pinnedStore: PinnedChatsStore,
    private val unreadStore: UnreadChatsStore,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    /** Locally pinned chat keys, for pinning to the top of the drawer list. */
    val pinned: StateFlow<Set<String>> = pinnedStore.pinned

    /** Chats marked unread on this device, which show a dot and count as unread. */
    val marked: StateFlow<Set<String>> = unreadStore.marked

    private val _events = MutableSharedFlow<ChatListEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ChatListEvent> = _events.asSharedFlow()

    /** The pending or in-flight read of the list; a newer one replaces it. */
    private var readJob: Job? = null

    /** The user's own socket, which says when the list changed. */
    private var subscription: String? = null

    /** Whether the screen has resumed before: its first resume comes as init reads. */
    private var resumed = false

    init {
        load()
        subscription = webSocket.subscribe(sessionManager.getServerUrlBlocking(), USER_KEY, app = "chat") { event ->
            // A chat came or went, or its name, members, order or count moved;
            // or the socket was down, and missed whatever did.
            if (event.event == "list" || event.type == REOPEN) reload(REFRESH_DEBOUNCE)
        }
    }

    /** Read the list, the drawer saying so while it has nothing to show. */
    fun load() {
        readJob?.cancel()
        _uiState.update { state -> state.copy(isLoading = true, error = null) }
        readJob = viewModelScope.launch { read() }
    }

    /** Read the list again with no spinner, after [wait]: a burst of frames makes one read. */
    fun reload(wait: Duration = Duration.ZERO) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            delay(wait)
            read()
        }
    }

    /**
     * The screen's ON_RESUME, back from settings, a new chat or the
     * background. A socket opening again says so once it is open, and the
     * read its frame sets off is the return's; otherwise the return reads now.
     */
    fun onScreenResumed() {
        if (!resumed) {
            resumed = true
            return
        }
        if (webSocket.reopening(USER_KEY)) reload(REOPEN_GRACE) else reload()
    }

    /** The drawer opened: the list as it is now, unread counts and order with it. */
    fun onDrawerOpened() = reload()

    /**
     * The list as the server has it, setting it in the state as it then is:
     * a search typed or a chip tapped meanwhile stays. A successful read is
     * the whole list of the account signed in as it was sent, so pins and
     * marks of chats it no longer has are dropped - unless another account
     * has been signed in since, whose pins and marks it says nothing of.
     */
    private suspend fun read() {
        try {
            val account = sessionManager.getBoundIdentity()
            val chats = repository.listChats()
            val listed = chats.map { chat -> chat.id }.toSet()
            if (sessionManager.getBoundIdentity() == account) {
                pinnedStore.prune(listed, account)
                unreadStore.prune(listed, account)
            }
            _uiState.update { state -> state.copy(chats = chats, loaded = true, isLoading = false, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { state -> state.copy(isLoading = false, error = e.toMochiError()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscription?.let { id -> webSocket.unsubscribe(id) }
    }

    /** Pin [chat] to the top of the list, or unpin it; pins stay on this device. */
    fun togglePin(chat: String) = pinnedStore.toggle(chat)

    /**
     * Mark as read from the list, as the web has it: the badge clears on the
     * tap, the list reads again once the server has the read, and a refusal
     * shows the chat unread again with the error.
     */
    fun markRead(chat: String) {
        unreadStore.clear(chat)
        val before = _uiState.value.chats.firstOrNull { it.id == chat }?.unread ?: 0
        setUnread(chat, 0)
        viewModelScope.launch {
            try {
                repository.markRead(chat)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A list read since shows the server's own count, which stays.
                if (_uiState.value.chats.any { it.id == chat && it.unread == 0 }) setUnread(chat, before)
                _events.emit(ChatListEvent.Failure(e.toMochiError()))
                return@launch
            }
            _events.emit(ChatListEvent.Done(R.string.chat_list_marked_read))
            reload()
        }
    }

    /** Mark as unread from the list: a mark on this device until the chat is read. */
    fun markUnread(chat: String) {
        unreadStore.mark(chat)
        _events.tryEmit(ChatListEvent.Done(R.string.chat_list_marked_unread))
    }

    private fun setUnread(chat: String, unread: Int) {
        _uiState.update { state ->
            state.copy(chats = state.chats.map { if (it.id == chat) it.copy(unread = unread) else it })
        }
    }

    fun setQuery(query: String) {
        _uiState.update { state -> state.copy(query = query) }
    }

    fun setFilter(filter: ChatFilter) {
        _uiState.update { state -> state.copy(filter = filter) }
    }

    /** The chats the search and chip leave, keeping [open], the conversation on screen. */
    fun filteredChats(open: String = ""): List<Chat> {
        val state = _uiState.value
        val marks = unreadStore.marked.value
        val filtered = state.chats.filter { chat ->
            matchesChatFilter(chat, state.query, state.filter, open, marks)
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

}

/**
 * The unread badge for a drawer row. The open conversation is being read as
 * it is shown, so its row carries none even before the list refetches.
 */
internal fun unreadBadge(chat: Chat, open: String): Int = if (chat.id == open) 0 else chat.unread

/**
 * Whether a drawer row shows a dot for a chat [marked] unread: an active chat
 * other than the [open] one, with no count to show instead.
 */
internal fun unreadDot(chat: Chat, open: String, marked: Set<String>): Boolean =
    chat.id != open && chat.status == ChatStatus.ACTIVE && chat.id in marked && unreadBadge(chat, open) == 0

/**
 * Whether [chat] belongs in the list for the typed [query], matched against
 * its name, and the chosen [filter], as the web has them: an active chat with
 * unread messages or [marked] unread, or the [open] one, or any chat that
 * isn't a 1:1, a group everyone else has left included.
 */
internal fun matchesChatFilter(
    chat: Chat,
    query: String,
    filter: ChatFilter,
    open: String = "",
    marked: Set<String> = emptySet(),
): Boolean {
    val term = query.trim()
    if (term.isNotEmpty() && !chat.name.contains(term, ignoreCase = true)) {
        return false
    }
    return when (filter) {
        ChatFilter.ALL -> true
        ChatFilter.UNREAD ->
            chat.id == open || (chat.status == ChatStatus.ACTIVE && (chat.unread > 0 || chat.id in marked))
        ChatFilter.GROUPS -> chat.members != 2
    }
}
