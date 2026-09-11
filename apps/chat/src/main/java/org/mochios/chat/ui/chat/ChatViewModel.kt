// Copyright © 2026 Mochisoft OÜ
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
import kotlinx.coroutines.ensureActive
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
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.android.util.mergeNewest
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
    /** Whether older messages remain beyond the loaded scrollback. */
    val more: Boolean = false,
    /** The server's cursor for the next (older) page; null on the last. */
    val cursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    /** A failure with nothing loaded; the page shows it in place of the list. */
    val error: MochiError? = null,
    /** A failure over a loaded conversation, shown once as a toast. */
    val notice: MochiError? = null,
    /** The composer text, held here so a failed send keeps it. */
    val draft: String = "",
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
    /** The message being edited; the composer pre-fills and Send becomes Save. */
    val editing: ChatMessage? = null,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isPinned: Boolean = false,
    val chatDeleted: Boolean = false,
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

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    private val chatId: String = savedStateHandle["chatId"] ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var subscriptionId: String? = null
    private var searchJob: Job? = null

    /** The pending or in-flight [refresh]; cancelled when a newer one starts. */
    private var refreshJob: Job? = null

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

    /** The notice has been shown. */
    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun setDraft(text: String) {
        _uiState.value = _uiState.value.copy(draft = text)
    }

    /**
     * Leave server-side. The chat stays locally as a read-only tombstone and
     * is reloaded, unless [deleteLocally] also purges it from this device.
     */
    fun leaveChat(deleteLocally: Boolean = false) {
        viewModelScope.launch {
            try {
                repository.leaveChat(chatId, deleteLocally)
                if (deleteLocally) {
                    _uiState.value = _uiState.value.copy(chatDeleted = true)
                } else {
                    load()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
            }
        }
    }

    /**
     * Delete the local copy; only offered once the chat has been left or
     * removed server-side.
     */
    fun deleteChat() {
        viewModelScope.launch {
            try {
                repository.deleteChat(chatId)
                _uiState.value = _uiState.value.copy(chatDeleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
            }
        }
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
                    more = msgs.more,
                    cursor = msgs.cursor,
                    isLoading = false
                )
                subscribeWebSocket(view.chat.key)
                markRead()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false).failed(e.toMochiError())
            }
        }
    }

    /**
     * Advance the read watermark to the newest message. Fire-and-forget: it
     * only moves forward, so a failure is harmless.
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

    /**
     * Refetch the newest page of messages and mark it read. Each call cancels
     * the previous refresh and waits [REFRESH_DEBOUNCE] first, so a burst of
     * frames - our own send's echo lands with the send's response - makes one
     * fetch, and only the latest one updates the list.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val msgs = repository.getMessages(chatId, limit = MESSAGE_PAGE_SIZE)
                val held = _uiState.value.messages
                val merged = mergeNewest(
                    existing = held,
                    incoming = msgs.messages,
                    id = { message -> message.id },
                    created = { message -> message.created },
                )
                // Runs on every inbound message, reaction and delete: keep the
                // paged-in scrollback, and keep the older-end cursor - the
                // refetch's only points below its own newest page.
                val stitched = merged !== msgs.messages && held.isNotEmpty()
                _uiState.value = _uiState.value.copy(
                    messages = merged,
                    more = if (stitched) _uiState.value.more else msgs.more,
                    cursor = if (stitched) _uiState.value.cursor else msgs.cursor,
                    isRefreshing = false,
                    error = null
                )
                markRead()
            } catch (e: Exception) {
                ensureActive()
                _uiState.value = _uiState.value.copy(isRefreshing = false).failed(e.toMochiError())
            }
        }
    }

    fun loadMoreOlder() {
        val cursor = _uiState.value.cursor ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val older = repository.getMessages(chatId, cursor = cursor, limit = MESSAGE_PAGE_SIZE)
                // Defensive: the (created, id) keyset cursor returns no overlap,
                // but dedupe by id anyway so the LazyColumn keys can never collide.
                val merged = (older.messages + _uiState.value.messages)
                    .distinctBy { message -> message.id }
                _uiState.value = _uiState.value.copy(
                    messages = merged,
                    more = older.more,
                    cursor = older.cursor,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false).failed(e.toMochiError())
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
                // Drop them locally first: the merge in refresh() keeps rows the
                // newest page does not mention, so a message the server omits
                // rather than tombstoning would otherwise come straight back.
                val gone = messageIds.toSet()
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filterNot { gone.contains(it.id) },
                )
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
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
                            m.copy(reactions = res.reactions, reaction = res.reaction)
                        } else {
                            m
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
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
     * Debounced server-side search (2+ chars, as on web); match ids are
     * newest-first so match 1 is the latest hit.
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
            delay(SEARCH_DEBOUNCE)
            try {
                val res = repository.search(chatId, trimmed)
                val ids = res.results
                    .sortedByDescending { result -> result.created }
                    .map { result -> result.id }
                _uiState.value = _uiState.value.copy(searchMatchIds = ids, searchMatchIndex = 0)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
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
                    repository.getNewChatData().friends.filter { it.chat.isEmpty() }
                }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    forwardChats = chats, forwardFriends = friends, forwardLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(forwardLoading = false).failed(e.toMochiError())
            }
        }
    }

    fun closeForward() {
        _uiState.value = _uiState.value.copy(
            forwardMessageIds = emptyList(), forwardChats = emptyList(),
            forwardFriends = emptyList(), forwardLoading = false,
        )
    }

    /** Forward the messages currently open in the forward sheet to the chat [destination]. */
    fun forwardToChat(destination: String) {
        val messageIds = _uiState.value.forwardMessageIds
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.forwardMessages(chatId, messageIds, destination)
                _uiState.value = _uiState.value.copy(
                    forwardMessageIds = emptyList(),
                    forwardChats = emptyList(),
                    forwardFriends = emptyList(),
                    selectionMode = false,
                    selectedIds = emptySet(),
                )
                _events.emit(application.getString(R.string.chat_forward_success))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
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
                _uiState.value = _uiState.value.failed(e.toMochiError())
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

    // ---------------- edit ----------------

    /**
     * Start editing [message]. Editing and replying are mutually exclusive:
     * the composer has one body, and a reply-to on an edit would be discarded
     * by the server anyway.
     */
    fun startEdit(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(editing = message, replyingTo = null, draft = message.body)
    }

    /** Clears the composer too, so the old text never leaks into a new message. */
    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editing = null, draft = "")
    }

    /**
     * Save the edit. The server stamps `edited` and fans the new body out to
     * the other members; refresh so this client shows what they will see
     * rather than a locally-guessed row.
     */
    fun saveEdit() {
        val message = _uiState.value.editing ?: return
        val trimmed = _uiState.value.draft.trim()
        if (trimmed.isEmpty() || trimmed == message.body) {
            cancelEdit()
            return
        }
        viewModelScope.launch {
            try {
                repository.editMessage(chatId, message.id, trimmed)
                _uiState.value = _uiState.value.copy(editing = null, draft = "")
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
            }
        }
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

    /** Send the composer draft. It is only cleared once the server accepted it. */
    fun sendMessage() {
        val trimmed = _uiState.value.draft.trim()
        val attachments = _uiState.value.pendingAttachments
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        val reply = _uiState.value.replyingTo?.id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                if (attachments.isEmpty()) {
                    repository.sendMessage(chatId, trimmed, reply = reply)
                } else {
                    repository.sendMessageFromUris(
                        chatId, trimmed, attachments, application, reply = reply,
                    )
                }
                _uiState.value = _uiState.value.afterSend(sent = true)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
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
            subscriptionId = webSocket.subscribe(serverUrl, key, app = "chat") { event ->
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
                    ev == "edit" -> {
                        // An author rewrote a message. Without this the body
                        // changes under the reader on the next unrelated
                        // refresh, with no marker to explain it.
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

/**
 * Where a failure lands. With nothing loaded the page shows it in place of
 * the list; once messages are on screen that branch never renders, so it
 * becomes a one-shot notice instead.
 */
internal fun ChatUiState.failed(error: MochiError): ChatUiState =
    if (messages.isEmpty()) copy(error = error) else copy(notice = error)

/**
 * The composer after a send attempt. Only a send the server accepted clears
 * the draft, its attachments and the reply target; a failed one keeps them
 * all, so the text is there to retry or copy out.
 */
internal fun ChatUiState.afterSend(sent: Boolean): ChatUiState =
    if (sent) copy(draft = "", pendingAttachments = emptyList(), replyingTo = null) else this

/**
 * Whether the list should follow to the newest message. Only an append - a
 * message arriving or being sent - moves the reader; paging older history in
 * and deleting rows leave the reader where they are.
 */
internal fun followsNewest(shown: List<ChatMessage>, current: List<ChatMessage>): Boolean {
    val newest = current.lastOrNull()?.id ?: return false
    val previous = shown.lastOrNull()?.id ?: return true
    if (newest == previous) return false
    return current.any { message -> message.id == previous }
}
