// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.android.model.Attachment
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.push.VisibleEntity
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.android.util.mergeNewest
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.REOPEN
import org.mochios.chat.R
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.model.Chat
import org.mochios.chat.model.ChatDetail
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatPresence
import org.mochios.chat.model.ChatQuote
import org.mochios.chat.model.ChatReceipt
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.model.Friend
import org.mochios.chat.model.PRESENCE_INTERVAL
import org.mochios.chat.model.TYPING_INTERVAL
import org.mochios.chat.model.Typists
import org.mochios.chat.model.presence
import org.mochios.chat.model.typing
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
    /** Each member's delivered and displayed watermarks, from the newest page and the receipts since. */
    val receipts: List<ChatReceipt> = emptyList(),
    /** The other member's presence in a one-to-one conversation, once asked. */
    val presence: ChatPresence? = null,
    /** The names of the members typing now. */
    val typing: List<String> = emptyList(),
    /** The thread open over the conversation, null when none is. */
    val thread: ThreadState? = null,
    /** An invitation being accepted or declined. */
    val answering: Boolean = false,
)

/** A message's thread: the message, its replies, and a reply being written. */
data class ThreadState(
    val root: ChatMessage,
    val replies: List<ChatMessage> = emptyList(),
    val more: Boolean = false,
    val cursor: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val draft: String = "",
    val isSending: Boolean = false,
    val error: MochiError? = null,
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

    private val tracker = UploadTracker()

    /** The files of the message being sent, for the composer. */
    val upload: StateFlow<Upload?> = tracker.state

    private var subscriptionId: String? = null
    private var searchJob: Job? = null

    /** The pending or in-flight [refresh]; cancelled when a newer one starts. */
    private var refreshJob: Job? = null

    /** The pending or in-flight [loadThread]; cancelled when a newer one starts. */
    private var threadJob: Job? = null

    /** The pending mark-read after a message arrived; cancelled by the next arrival. */
    private var readJob: Job? = null

    /**
     * How many frames have been applied. A read of the server that a frame
     * landed during answers from before that frame, and would undo it, so it
     * reads again instead.
     */
    private var frames = 0

    /** Who is typing, and the job that clears the line as each one lapses. */
    private val typists = Typists()
    private var typingJob: Job? = null

    /** When the user's typing was last told, by the uptime clock; null before the first. */
    private var typingSent: Long? = null

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

    /**
     * The composer's text. Typing goes to the other members while the user
     * writes, at most once an interval, as the text grows; of the
     * conversations on other networks, only a linked XMPP or Matrix one takes it.
     */
    fun setDraft(text: String) {
        val previous = _uiState.value.draft
        _uiState.value = _uiState.value.copy(draft = text)
        if (!_uiState.value.chat.typing() || text.isBlank() || text.length <= previous.length) return
        val now = SystemClock.uptimeMillis()
        val last = typingSent
        if (last != null && now - last < TYPING_INTERVAL) return
        typingSent = now
        viewModelScope.launch {
            try {
                repository.typing(chatId)
            } catch (_: Exception) {
                // Typing is a courtesy; a failure leaves nothing to tell.
            }
        }
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
                // The messages need only the id, so they are asked for beside
                // the chat. The chat is awaited first: one gone or closed to the
                // user reports that, whatever the messages' answer.
                val (view, msgs) = coroutineScope {
                    val chat = async { repository.viewChat(chatId) }
                    val messages = async { attempt { repository.getMessages(chatId, limit = MESSAGE_PAGE_SIZE) } }
                    chat.await() to messages.await().getOrThrow()
                }
                _uiState.value = _uiState.value.copy(
                    chat = view.chat,
                    identity = view.identity,
                    messages = msgs.messages,
                    more = msgs.more,
                    cursor = msgs.cursor,
                    receipts = msgs.receipts,
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
                val seen = frames
                val msgs = repository.getMessages(chatId, limit = MESSAGE_PAGE_SIZE)
                if (frames != seen) {
                    refresh()
                    return@launch
                }
                val held = _uiState.value.messages
                val merged = mergeNewest(
                    existing = held,
                    incoming = msgs.messages,
                    id = { message -> message.id },
                    created = { message -> message.created },
                )
                // Runs after the user's own sends, edits and deletes, on a
                // reopened socket and on frames that cannot be applied: keep
                // the paged-in scrollback, and keep the older-end cursor - the
                // refetch's only points below its own newest page.
                val stitched = merged !== msgs.messages && held.isNotEmpty()
                _uiState.value = _uiState.value.copy(
                    messages = merged,
                    more = if (stitched) _uiState.value.more else msgs.more,
                    cursor = if (stitched) _uiState.value.cursor else msgs.cursor,
                    receipts = msgs.receipts,
                    isRefreshing = false,
                    error = null
                )
                // Read only by a reader: a frame or a reopened socket refreshes a
                // chat that sits behind another screen, or in the background, too.
                if (VisibleEntity.isShowing("chat", chatId)) markRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false).failed(e.toMochiError())
            }
        }
    }

    /**
     * Read again everything the socket keeps current, once it is open again
     * after being down: messages, reactions and edits sent meanwhile, the
     * open thread, and the chat's name and members.
     */
    private fun catchUp() {
        refresh()
        loadThread()
        viewModelScope.launch {
            try {
                val chat = repository.viewChat(chatId).chat
                _uiState.value = _uiState.value.copy(chat = chat)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The chat keeps what it showed; the messages' refresh reports.
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
            } catch (e: CancellationException) {
                throw e
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
                    val sent = repository.sendMessage(chatId, trimmed, reply = reply)
                    _uiState.value = _uiState.value.afterSend(sent = true)
                    sent.unsent.orEmpty().forEach { _events.emit(it) }
                    refresh()
                } else {
                    tracker.track {
                        val sent = repository.sendMessageFromUris(
                            chatId, trimmed, attachments, application, reply = reply, tracker = tracker,
                        )
                        _uiState.value = _uiState.value.afterSend(sent = true)
                        // What the message went without on another network.
                        sent.unsent.orEmpty().forEach { _events.emit(it) }
                        // Inside the upload, so the files in flight give way
                        // to the message with nothing blank between.
                        refresh()
                    }
                }
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

    // ---------------- presence ----------------

    /**
     * Ask whether the other member of a one-to-one conversation is online,
     * now and each [PRESENCE_INTERVAL] after, for as long as the caller - the
     * screen, while it shows the conversation - keeps this running.
     */
    suspend fun watchPresence() {
        while (true) {
            if (_uiState.value.chat.presence()) {
                try {
                    _uiState.value = _uiState.value.copy(presence = repository.presence(chatId))
                } catch (_: Exception) {
                    // A presence the server could not ask is shown as none.
                }
            }
            delay(PRESENCE_INTERVAL)
        }
    }

    // ---------------- typing ----------------

    /** A typing event: [member] is typing, or with [stopped] has stopped. */
    internal fun typed(member: String, name: String, stopped: Boolean, held: Double?) {
        if (member.isEmpty() || member == _uiState.value.identity) return
        if (stopped) typists.clear(member) else typists.add(member, name, SystemClock.uptimeMillis(), held)
        showTyping()
    }

    /** The typing line as it stands, and a clear for when the next typist lapses. */
    private fun showTyping() {
        val now = SystemClock.uptimeMillis()
        _uiState.value = _uiState.value.copy(typing = typists.names(now))
        typingJob?.cancel()
        val next = typists.next(now) ?: return
        typingJob = viewModelScope.launch {
            delay(next - now)
            showTyping()
        }
    }

    // ---------------- receipts ----------------

    /** A member's watermarks, which only move forward. */
    internal fun received(member: String, delivered: Long, displayed: Long) {
        if (member.isEmpty()) return
        val receipts = _uiState.value.receipts
        val next = if (receipts.any { it.member == member }) {
            receipts.map { receipt ->
                if (receipt.member == member) {
                    receipt.copy(
                        delivered = maxOf(receipt.delivered, delivered),
                        displayed = maxOf(receipt.displayed, displayed),
                    )
                } else {
                    receipt
                }
            }
        } else {
            receipts + ChatReceipt(member = member, delivered = delivered, displayed = displayed)
        }
        _uiState.value = _uiState.value.copy(receipts = next)
    }

    // ---------------- threads ----------------

    /** Open [root]'s thread: the message, and its replies as the server has them. */
    fun openThread(root: ChatMessage) {
        _uiState.value = _uiState.value.copy(thread = ThreadState(root = root))
        loadThread(debounced = false)
    }

    fun closeThread() {
        _uiState.value = _uiState.value.copy(thread = null)
    }

    /**
     * The newest replies of the open thread, merged with those paged in. Each
     * call cancels the previous one; a [debounced] call waits [REFRESH_DEBOUNCE]
     * first, so a reply's response and its echo frame make one fetch.
     */
    private fun loadThread(debounced: Boolean = true) {
        val root = _uiState.value.thread?.root ?: return
        threadJob?.cancel()
        threadJob = viewModelScope.launch {
            if (debounced) {
                delay(REFRESH_DEBOUNCE)
            }
            try {
                val seen = frames
                val page = repository.getMessages(chatId, limit = MESSAGE_PAGE_SIZE, thread = root.id)
                if (frames != seen) {
                    loadThread()
                    return@launch
                }
                val thread = _uiState.value.thread?.takeIf { it.root.id == root.id } ?: return@launch
                val merged = mergeNewest(
                    existing = thread.replies,
                    incoming = page.messages,
                    id = { message -> message.id },
                    created = { message -> message.created },
                )
                val stitched = merged !== page.messages && thread.replies.isNotEmpty()
                _uiState.value = _uiState.value.copy(
                    thread = thread.copy(
                        replies = merged,
                        more = if (stitched) thread.more else page.more,
                        cursor = if (stitched) thread.cursor else page.cursor,
                        isLoading = false,
                        error = null,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val thread = _uiState.value.thread?.takeIf { it.root.id == root.id } ?: return@launch
                _uiState.value = _uiState.value.copy(thread = thread.copy(isLoading = false, error = e.toMochiError()))
            }
        }
    }

    /** The open thread's earlier replies. */
    fun loadEarlierReplies() {
        val thread = _uiState.value.thread ?: return
        val cursor = thread.cursor ?: return
        if (thread.isLoadingMore) return
        _uiState.value = _uiState.value.copy(thread = thread.copy(isLoadingMore = true))
        viewModelScope.launch {
            try {
                val page = repository.getMessages(chatId, cursor = cursor, limit = MESSAGE_PAGE_SIZE, thread = thread.root.id)
                val current = _uiState.value.thread?.takeIf { it.root.id == thread.root.id } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    thread = current.copy(
                        replies = (page.messages + current.replies).distinctBy { it.id },
                        more = page.more,
                        cursor = page.cursor,
                        isLoadingMore = false,
                    ),
                )
            } catch (e: Exception) {
                val current = _uiState.value.thread ?: return@launch
                _uiState.value = _uiState.value.copy(thread = current.copy(isLoadingMore = false, error = e.toMochiError()))
            }
        }
    }

    fun setThreadDraft(text: String) {
        val thread = _uiState.value.thread ?: return
        _uiState.value = _uiState.value.copy(thread = thread.copy(draft = text))
    }

    /** Reply in the open thread; the draft is only cleared once the server took it. */
    fun sendThreadReply() {
        val thread = _uiState.value.thread ?: return
        val text = thread.draft.trim()
        if (text.isEmpty() || thread.isSending) return
        _uiState.value = _uiState.value.copy(thread = thread.copy(isSending = true))
        viewModelScope.launch {
            try {
                repository.sendMessage(chatId, text, thread = thread.root.id)
                _uiState.value.thread?.let { current ->
                    _uiState.value = _uiState.value.copy(thread = current.copy(isSending = false, draft = ""))
                }
                loadThread()
                refresh()
            } catch (e: Exception) {
                _uiState.value.thread?.let { current ->
                    _uiState.value = _uiState.value.copy(thread = current.copy(isSending = false))
                }
                _uiState.value = _uiState.value.failed(e.toMochiError())
            }
        }
    }

    // ---------------- invitations ----------------

    /** Accept an invitation to a room on another network: the account joins it. */
    fun accept() {
        if (_uiState.value.answering) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(answering = true)
            try {
                repository.accept(chatId)
                _uiState.value = _uiState.value.copy(answering = false)
                _events.emit(application.getString(R.string.chat_invitation_joined))
                // The conversation as the server now has it, active.
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(answering = false).failed(e.toMochiError())
            }
        }
    }

    /** Decline an invitation; the conversation goes. */
    fun decline() {
        if (_uiState.value.answering) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(answering = true)
            try {
                repository.decline(chatId)
                _events.emit(application.getString(R.string.chat_invitation_declined))
                _uiState.value = _uiState.value.copy(answering = false, chatDeleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(answering = false).failed(e.toMochiError())
            }
        }
    }

    private fun subscribeWebSocket(key: String) {
        if (key.isEmpty() || subscriptionId != null) return
        viewModelScope.launch {
            val serverUrl = sessionManager.getServerUrlBlocking()
            subscriptionId = webSocket.subscribe(serverUrl, key, app = "chat") { event ->
                val ev = event.event
                when {
                    event.type == REOPEN -> catchUp()
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
                    // The socket calls back on its own thread; these change the
                    // list on the main one, as everything else that changes it does.
                    ev == "delete" -> viewModelScope.launch { deleted(event) }
                    ev == "edit" -> viewModelScope.launch { revised(event) }
                    ev == "reaction" -> viewModelScope.launch { reacted(event) }
                    ev == "decrypted" -> {
                        // A message whose keys came: its text and files are read again.
                        refresh()
                        if (_uiState.value.thread != null) loadThread()
                    }
                    ev == "typing" -> {
                        typed(event.member.orEmpty(), event.name.orEmpty(), event.typing == false, event.held)
                    }
                    ev == "receipt" -> {
                        received(event.member.orEmpty(), event.delivered ?: 0, event.displayed ?: 0)
                    }
                    ev == null && event.body != null -> viewModelScope.launch { arrived(event) }
                }
            }
        }
    }

    // ---------------- frames ----------------
    //
    // Each frame is applied to what is held, as the web applies it to its
    // cache, rather than reading the newest page again for every one.

    /**
     * A message from the socket: its sender has stopped typing, and it joins
     * the main timeline, or as a reply in a thread the open thread and its
     * root's count. A frame without the id or time a row needs is read from
     * the server instead.
     */
    private fun arrived(event: WebSocketEvent) {
        frames++
        event.member?.let { member -> typed(member, "", stopped = true, held = null) }
        val message = frameMessage(chatId, event)
        if (message == null) {
            refresh()
            val thread = event.thread.orEmpty()
            if (thread.isNotEmpty() && _uiState.value.thread?.root?.id == thread) loadThread()
            return
        }
        val added = if (message.thread.isEmpty()) post(message) else answer(message)
        // The user's own message was never unread.
        if (added && message.member != _uiState.value.identity) markReadSoon()
    }

    /** [message] on the main timeline, unless it is there already: our own send's echo is. */
    private fun post(message: ChatMessage): Boolean {
        val state = _uiState.value
        if (state.messages.any { held -> held.id == message.id }) return false
        _uiState.value = state.copy(messages = state.messages.adding(message, state.more))
        return true
    }

    /**
     * [reply] counted on its root, and added to its thread where that is open.
     * A reply the open thread already holds was counted when it was read.
     */
    private fun answer(reply: ChatMessage): Boolean {
        val state = _uiState.value
        val open = state.thread?.takeIf { thread -> thread.root.id == reply.thread }
        if (open != null && open.replies.any { held -> held.id == reply.id }) return false
        val counted = { root: ChatMessage ->
            root.copy(replies = root.replies + 1, latest = maxOf(root.latest, reply.created))
        }
        _uiState.value = state.copy(
            messages = state.messages.patched(reply.thread, counted),
            thread = open?.copy(root = counted(open.root), replies = open.replies.adding(reply, open.more))
                ?: state.thread,
        )
        return true
    }

    /**
     * A message deleted for everyone becomes a tombstone wherever it is held.
     * A reply in the open thread stops counting on its root. One held nowhere
     * may be a reply in a thread that is not open, whose root's count only the
     * server knows, so the newest page is read again where any count shows.
     */
    private fun deleted(event: WebSocketEvent) {
        frames++
        val id = event.message
        if (id.isNullOrEmpty()) {
            refresh()
            return
        }
        val state = _uiState.value
        val reply = state.thread?.replies?.find { held -> held.id == id && !held.deleted }
        if (!patch(id) { message -> message.tombstone() }) {
            if (state.messages.any { message -> message.replies > 0 }) refresh()
            return
        }
        if (reply != null) uncount(reply)
    }

    /** [reply], just deleted, no longer counted on its root. */
    private fun uncount(reply: ChatMessage) {
        val state = _uiState.value
        // The open thread holds its newest replies, so the newest left is the latest.
        val remaining = state.thread?.replies.orEmpty().filter { held -> held.thread == reply.thread && !held.deleted }
        val uncounted = { root: ChatMessage ->
            val replies = (root.replies - 1).coerceAtLeast(0)
            val latest = if (replies == 0) 0 else remaining.maxOfOrNull { held -> held.created } ?: root.latest
            root.copy(replies = replies, latest = latest)
        }
        _uiState.value = state.copy(
            messages = state.messages.patched(reply.thread, uncounted),
            thread = state.thread?.let { thread ->
                if (thread.root.id == reply.thread) thread.copy(root = uncounted(thread.root)) else thread
            },
        )
    }

    /** An edit, which replaces what is held only if it is newer: the last writer wins. */
    private fun revised(event: WebSocketEvent) {
        frames++
        val id = event.message
        val body = event.body
        val edited = event.edited
        if (id.isNullOrEmpty() || body == null || edited == null) {
            refresh()
            return
        }
        patch(id) { message -> message.rewritten(body, edited) }
    }

    /**
     * A message's reaction counts, and the user's own reaction where the frame
     * says it was theirs that changed: made on another of their devices, it
     * did not come from this one's react. A frame from an older server names
     * no member, and leaves the user's own reaction as it was.
     */
    private fun reacted(event: WebSocketEvent) {
        frames++
        val id = event.message
        if (id.isNullOrEmpty()) {
            refresh()
            return
        }
        val counts = event.reactions.orEmpty().filterValues { count -> count > 0 }
        val mine = event.member?.takeIf { member -> member.isNotEmpty() } == _uiState.value.identity
        patch(id) { message ->
            when {
                message.deleted -> message
                mine -> message.copy(reactions = counts, reaction = event.reaction?.takeIf { it.isNotEmpty() })
                else -> message.copy(reactions = counts)
            }
        }
    }

    /**
     * [transform] applied to message [id] wherever it is held: the main
     * timeline, the open thread's root and its replies. Answers whether it was
     * held anywhere.
     */
    private fun patch(id: String, transform: (ChatMessage) -> ChatMessage): Boolean {
        val state = _uiState.value
        val thread = state.thread
        val held = state.messages.any { message -> message.id == id } ||
            thread?.root?.id == id ||
            thread?.replies?.any { message -> message.id == id } == true
        if (!held) return false
        _uiState.value = state.copy(
            messages = state.messages.patched(id, transform),
            thread = thread?.copy(
                root = if (thread.root.id == id) transform(thread.root) else thread.root,
                replies = thread.replies.patched(id, transform),
            ),
        )
        return true
    }

    /**
     * Mark read, as [refresh] does, once a burst of arrivals has passed, and
     * only for a reader: the chat may sit behind another screen.
     */
    private fun markReadSoon() {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            delay(REFRESH_DEBOUNCE)
            if (VisibleEntity.isShowing("chat", chatId)) markRead()
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

/** Reads a frame's files and quote into the chat's own types. */
private val frames = Gson()

/**
 * The message a socket frame carries, as the messages page would give it;
 * null where the frame lacks the id or time a row needs. A frame is never a
 * tombstone and carries no reactions or replies yet. `encrypted` is absent
 * from an older server's frames, which is no encryption.
 */
internal fun frameMessage(chat: String, event: WebSocketEvent): ChatMessage? {
    val id = event.id?.takeIf { id -> id.isNotEmpty() } ?: return null
    val created = event.created ?: return null
    return ChatMessage(
        id = id,
        chat = chat,
        member = event.member.orEmpty(),
        name = event.name.orEmpty(),
        body = event.body.orEmpty(),
        created = created,
        attachments = frameAttachments(event.attachments),
        edited = event.edited ?: 0,
        reply = event.reply?.takeIf { reply -> reply.isNotEmpty() },
        quote = frameQuote(event.quote),
        thread = event.thread.orEmpty(),
        encrypted = event.encrypted ?: 0,
    )
}

/**
 * A frame's files, as the attachments library lists them for the messages
 * page too. An entry without the fields a file shows by is left out, as the
 * web leaves it out, rather than reaching the list half empty.
 */
private fun frameAttachments(element: JsonElement?): List<Attachment> {
    if (element == null || !element.isJsonArray) return emptyList()
    return element.asJsonArray.mapNotNull { item ->
        val fields = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        if (!fields.text("id") || !fields.text("name") || !fields.number("size") || !fields.text("type")) {
            return@mapNotNull null
        }
        try {
            frames.fromJson(fields, Attachment::class.java)
        } catch (_: JsonParseException) {
            null
        }
    }
}

/** A frame's quoted post; null, or anything without a link and a network, is none. */
private fun frameQuote(element: JsonElement?): ChatQuote? {
    val fields = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    if (!fields.text("url") || !fields.text("network")) return null
    return try {
        frames.fromJson(fields, ChatQuote::class.java)
    } catch (_: JsonParseException) {
        null
    }
}

private fun JsonObject.text(key: String): Boolean =
    get(key)?.let { value -> value.isJsonPrimitive && value.asJsonPrimitive.isString } == true

private fun JsonObject.number(key: String): Boolean =
    get(key)?.let { value -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber } == true

/**
 * [message] added in (created, id) order, the order the list is held in. One
 * older than everything loaded while older pages remain belongs among them,
 * so it is left for paging to bring rather than put at the top.
 */
internal fun List<ChatMessage>.adding(message: ChatMessage, more: Boolean): List<ChatMessage> {
    if (more && isNotEmpty() && message.created < first().created) return this
    val order = compareBy<ChatMessage>({ held -> held.created }, { held -> held.id })
    val index = indexOfFirst { held -> order.compare(held, message) > 0 }
    return if (index < 0) this + message else toMutableList().apply { add(index, message) }
}

/** [transform] applied to message [id]; the same list where it is not held. */
internal fun List<ChatMessage>.patched(id: String, transform: (ChatMessage) -> ChatMessage): List<ChatMessage> {
    val index = indexOfFirst { message -> message.id == id }
    if (index < 0) return this
    return toMutableList().apply { set(index, transform(get(index))) }
}

/** A message deleted for everyone, as the messages page shows one. */
internal fun ChatMessage.tombstone(): ChatMessage = copy(
    deleted = true,
    body = "",
    reply = null,
    attachments = emptyList(),
    quote = null,
    reactions = emptyMap(),
    reaction = null,
    replies = 0,
    latest = 0,
)

/**
 * An edit of this message, applied only if newer than what is held: edits
 * cross on the way, and the last writer wins. A tombstone is never rewritten.
 */
internal fun ChatMessage.rewritten(body: String, edited: Long): ChatMessage =
    if (deleted || this.edited >= edited) this else copy(body = body, edited = edited)

/** [block]'s answer as a [Result], except a cancellation, which still cancels. */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
