// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.chat.R
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatDetail
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.model.Friend
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

data class ChatUiState(
    val chat: ChatDetail = ChatDetail(),
    val identity: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long? = null,
    val nextCursorId: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val error: MochiError? = null,
    val pendingAttachments: List<Uri> = emptyList(),
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchMatchIds: List<String> = emptyList(),
    val searchMatchIndex: Int = 0,
    val forwardMessageIds: List<String> = emptyList(),
    val forwardChats: List<Chat> = emptyList(),
    val forwardFriends: List<Friend> = emptyList(),
    val forwardLoading: Boolean = false,
    val replyingTo: ChatMessage? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isPinned: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val pinnedStore: PinnedChatsStore,
    private val application: Application,
) : ViewModel() {

    private val chatId: String = savedStateHandle["chatId"] ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var subscriptionId: String? = null
    private var searchJob: Job? = null

    // One-shot toast messages (already localised) — e.g. forward success/failure.
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        load()
        viewModelScope.launch {
            pinnedStore.pinned.collect { pins ->
                _uiState.value = _uiState.value.copy(isPinned = chatId in pins)
            }
        }
    }

    /** Toggle this chat's local pin. Pins are device-only (no server concept). */
    fun togglePin() {
        pinnedStore.toggle(chatId)
    }

    /**
     * Manually move the read watermark to the latest message (the same call
     * fired automatically on open), then confirm with a toast.
     */
    fun markReadNow() {
        markRead()
        _events.tryEmit(application.getString(R.string.chat_marked_read))
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val view = repository.viewChat(chatId)
                val msgs = repository.getMessages(chatId, limit = MESSAGE_PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    chat = view.chat,
                    identity = view.identity,
                    messages = msgs.messages,
                    hasMore = msgs.hasMore,
                    nextCursor = msgs.nextCursor,
                    nextCursorId = msgs.nextCursorId,
                    isLoading = false
                )
                subscribeWebSocket(view.chat.key)
                markRead()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Move the read watermark to the latest message (server defaults `read` to
     * the newest message's time). Fire-and-forget: the watermark is local-only,
     * non-synced, and only moves forward, so failures are non-critical. Mirrors
     * the web client marking a chat read on open / on each new message.
     */
    private fun markRead() {
        viewModelScope.launch {
            try {
                repository.markRead(chatId)
            } catch (_: Exception) {
                // Non-critical; ignore.
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val msgs = repository.getMessages(chatId, limit = MESSAGE_PAGE_SIZE)
                _uiState.value = _uiState.value.copy(
                    messages = msgs.messages,
                    hasMore = msgs.hasMore,
                    nextCursor = msgs.nextCursor,
                    nextCursorId = msgs.nextCursorId,
                    isRefreshing = false,
                    error = null
                )
                markRead()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    fun loadMoreOlder() {
        val cursor = _uiState.value.nextCursor ?: return
        val cursorId = _uiState.value.nextCursorId
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val older = repository.getMessages(chatId, before = cursor, beforeId = cursorId, limit = MESSAGE_PAGE_SIZE)
                // Defensive: the (created, id) keyset cursor returns no overlap,
                // but dedupe by id anyway so the LazyColumn keys can never collide.
                val merged = (older.messages + _uiState.value.messages)
                    .distinctBy { message -> message.id }
                _uiState.value = _uiState.value.copy(
                    messages = merged,
                    hasMore = older.hasMore,
                    nextCursor = older.nextCursor,
                    nextCursorId = older.nextCursorId,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false, error = e.toMochiError())
            }
        }
    }

    /** Delete messages (delete-for-everyone). The server only removes the ones
     *  the caller owns; refresh reflects the resulting tombstones. */
    fun deleteMessages(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.deleteMessages(chatId, messageIds)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Toggle a reaction on a message (pass "none"/"" to clear). Updates the
     *  affected message in place from the server's returned counts. */
    fun react(messageId: String, reaction: String) {
        viewModelScope.launch {
            try {
                val res = repository.react(chatId, messageId, reaction)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.map { m ->
                        if (m.id == messageId) {
                            m.copy(reactionCounts = res.reactionCounts, myReaction = res.myReaction)
                        } else {
                            m
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    // ---------------- message search ----------------

    fun openSearch() {
        _uiState.value = _uiState.value.copy(
            searchOpen = true, searchQuery = "", searchMatchIds = emptyList(), searchMatchIndex = 0,
        )
    }

    fun closeSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchOpen = false, searchQuery = "", searchMatchIds = emptyList(), searchMatchIndex = 0,
        )
    }

    /**
     * Debounced server-side message search (needs >=2 chars, matches web). The
     * returned match ids drive the counter and up/down navigation; results are
     * ordered newest-first so match 1 is the most recent hit.
     */
    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, searchMatchIndex = 0)
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.value = _uiState.value.copy(searchMatchIds = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val res = repository.search(chatId, trimmed)
                val ids = res.results
                    .sortedByDescending { result -> result.created }
                    .map { result -> result.id }
                _uiState.value = _uiState.value.copy(searchMatchIds = ids, searchMatchIndex = 0)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Select which match is active — its message is scrolled to and highlighted. */
    fun setSearchMatchIndex(index: Int) {
        _uiState.value = _uiState.value.copy(searchMatchIndex = index)
    }

    // ---------------- forward ----------------

    /** Open the forward sheet for [messageId]; load active chats (minus this
     *  one) as destinations. */
    fun openForward(messageId: String) = openForward(listOf(messageId))

    /** Open the forward sheet for several [messageIds] (used by selection mode). */
    fun openForward(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            forwardMessageIds = messageIds, forwardChats = emptyList(),
            forwardFriends = emptyList(), forwardLoading = true,
        )
        viewModelScope.launch {
            try {
                val chats = repository.listChats()
                    .filter { it.id != chatId && it.status == ChatStatus.ACTIVE }
                // Offer friends without an existing 1-on-1 chat as destinations
                // (those with one already appear in the chats list) — matching
                // web. Forwarding to a friend creates the chat server-side.
                val friends = runCatching {
                    repository.getNewChatData().friends.filter { it.chatId.isEmpty() }
                }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    forwardChats = chats, forwardFriends = friends, forwardLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(forwardLoading = false, error = e.toMochiError())
            }
        }
    }

    fun closeForward() {
        _uiState.value = _uiState.value.copy(
            forwardMessageIds = emptyList(), forwardChats = emptyList(),
            forwardFriends = emptyList(), forwardLoading = false,
        )
    }

    /** Forward the messages currently open in the forward sheet to [toChatId]. */
    fun forwardToChat(toChatId: String) {
        val messageIds = _uiState.value.forwardMessageIds
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.forwardMessages(chatId, messageIds, toChatId)
                _uiState.value = _uiState.value.copy(
                    forwardMessageIds = emptyList(),
                    forwardChats = emptyList(),
                    forwardFriends = emptyList(),
                    selectionMode = false,
                    selectedIds = emptySet(),
                )
                _events.emit(application.getString(R.string.chat_forward_success))
            } catch (e: Exception) {
                _events.emit(application.getString(R.string.chat_forward_failed))
            }
        }
    }

    /** Forward the open messages to [memberId]'s 1-on-1 chat. The server reuses
     *  or creates that chat atomically after validating the messages. */
    fun forwardToFriend(memberId: String) {
        val messageIds = _uiState.value.forwardMessageIds
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.forwardToFriend(chatId, messageIds, memberId)
                _uiState.value = _uiState.value.copy(
                    forwardMessageIds = emptyList(),
                    forwardChats = emptyList(),
                    forwardFriends = emptyList(),
                    selectionMode = false,
                    selectedIds = emptySet(),
                )
                _events.emit(application.getString(R.string.chat_forward_success))
            } catch (e: Exception) {
                _events.emit(application.getString(R.string.chat_forward_failed))
            }
        }
    }

    // ---------------- reply ----------------

    /** Start replying to [message]; the composer shows a preview until sent or cancelled. */
    fun startReply(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(replyingTo = message)
    }

    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingTo = null)
    }

    // ---------------- selection ----------------

    /** Enter multi-select mode with [messageId] selected. */
    fun enterSelection(messageId: String) {
        _uiState.value = _uiState.value.copy(selectionMode = true, selectedIds = setOf(messageId))
    }

    /** Toggle [messageId] in the selection; leaving selection mode when empty. */
    fun toggleSelection(messageId: String) {
        val current = _uiState.value.selectedIds
        val updated = if (messageId in current) current - messageId else current + messageId
        _uiState.value = _uiState.value.copy(
            selectedIds = updated,
            selectionMode = updated.isNotEmpty(),
        )
    }

    fun exitSelection() {
        _uiState.value = _uiState.value.copy(selectionMode = false, selectedIds = emptySet())
    }

    /** Delete every selected message (server keeps only the ones we own), then exit. */
    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        exitSelection()
        deleteMessages(ids)
    }

    /** Open the forward sheet for the current selection. */
    fun forwardSelected() {
        openForward(_uiState.value.selectedIds.toList())
    }

    fun sendMessage(body: String) {
        val trimmed = body.trim()
        val attachments = _uiState.value.pendingAttachments
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        val replyTo = _uiState.value.replyingTo?.id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                if (attachments.isEmpty()) {
                    repository.sendMessage(chatId, trimmed, replyTo = replyTo)
                } else {
                    repository.sendMessageFromUris(
                        chatId, trimmed, attachments, application.contentResolver, replyTo = replyTo,
                    )
                }
                _uiState.value = _uiState.value.copy(pendingAttachments = emptyList(), replyingTo = null)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            pendingAttachments = _uiState.value.pendingAttachments + uris,
        )
    }

    fun removeAttachment(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            pendingAttachments = _uiState.value.pendingAttachments - uri,
        )
    }

    fun moveAttachment(uri: Uri, direction: Int) {
        val current = _uiState.value.pendingAttachments.toMutableList()
        val index = current.indexOf(uri)
        if (index < 0) return
        val newIndex = (index + direction).coerceIn(0, current.size - 1)
        if (newIndex == index) return
        val tmp = current[index]
        current[index] = current[newIndex]
        current[newIndex] = tmp
        _uiState.value = _uiState.value.copy(pendingAttachments = current)
    }

    private fun subscribeWebSocket(key: String) {
        if (key.isEmpty() || subscriptionId != null) return
        viewModelScope.launch {
            val serverUrl = sessionManager.getServerUrlBlocking()
            subscriptionId = webSocket.subscribe(serverUrl, key) { event ->
                val ev = event.event
                when {
                    ev == "rename" -> {
                        val newName = event.name ?: return@subscribe
                        _uiState.value = _uiState.value.copy(
                            chat = _uiState.value.chat.copy(name = newName)
                        )
                    }
                    ev == "leave" || ev == "member/remove" -> {
                        val memberId = event.member ?: return@subscribe
                        _uiState.value = _uiState.value.copy(
                            chat = _uiState.value.chat.copy(
                                members = _uiState.value.chat.members.filterNot { it.id == memberId }
                            )
                        )
                    }
                    ev == "removed" -> {
                        _uiState.value = _uiState.value.copy(
                            chat = _uiState.value.chat.copy(status = ChatStatus.REMOVED)
                        )
                    }
                    ev == "member/add" -> {
                        // Refresh members from server
                        viewModelScope.launch {
                            try {
                                val members = repository.getMembers(chatId)
                                _uiState.value = _uiState.value.copy(
                                    chat = _uiState.value.chat.copy(members = members)
                                )
                            } catch (_: Exception) { }
                        }
                    }
                    ev == "delete" -> {
                        // A message was tombstoned — refresh to render "deleted".
                        refresh()
                    }
                    ev == "reaction" -> {
                        // A reaction changed on a message — refresh to update counts.
                        refresh()
                    }
                    ev == null && event.body != null -> {
                        // Incoming message — refresh
                        refresh()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        subscriptionId?.let { webSocket.unsubscribe(it) }
    }

    private companion object {

        /** Messages fetched per page on load, refresh, and load-more. */
        const val MESSAGE_PAGE_SIZE = 30
    }
}
