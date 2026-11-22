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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.share.SharedImages
import org.mochios.android.files.Upload
import org.mochios.android.files.UploadTracker
import org.mochios.android.model.Attachment
import org.mochios.android.model.WebSocketEvent
import org.mochios.android.push.VisibleEntity
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.util.REFRESH_DEBOUNCE
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.android.util.mergeNewest
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.REOPEN
import org.mochios.chat.R
import org.mochios.chat.data.PinnedChatsStore
import org.mochios.chat.data.UnreadChatsStore
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
import org.mochios.chat.ui.router.CHAT_FEATURE
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
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    /** A failure with nothing loaded; the page shows it in place of the list. */
    val error: MochiError? = null,
    /** A failure over a loaded conversation, shown once as a toast. */
    val notice: MochiError? = null,
    /** The composer text, held here so a failed send can put it back. */
    val draft: String = "",
    val pendingAttachments: List<Uri> = emptyList(),
    /**
     * Why the composer's message came back into it, where the send refused it
     * for want of encryption: said under the composer, which offers to send it
     * unencrypted until the next send.
     */
    val refusal: MochiError? = null,
    /** Messages whose send failed while the composer held the next one, each to retry or copy. */
    val unsent: List<Unsent> = emptyList(),
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    /** The search's matches, newest first. */
    val searchMatches: List<SearchMatch> = emptyList(),
    val searchMatchIndex: Int = 0,
    /** The messages the threads of the search's matches are under, by id. */
    val searchRoots: Map<String, ChatMessage> = emptyMap(),
    /** How many older pages have been read looking for the active match. */
    val searchPages: Int = 0,
    val forwardMessageIds: List<String> = emptyList(),
    val forwardChats: List<Chat> = emptyList(),
    val forwardFriends: List<Friend> = emptyList(),
    val forwardLoading: Boolean = false,
    /** The chat or friend the messages are being forwarded to, while that is under way. */
    val forwarding: String? = null,
    val replyingTo: ChatMessage? = null,
    /** The message being edited; the composer shows [editDraft] and Send becomes Save. */
    val editing: ChatMessage? = null,
    /** The edit's text, apart from [draft], which waits with the reply target and files for the edit to end. */
    val editDraft: String = "",
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
    /** Replies whose send failed while the box held the next one. */
    val unsent: List<Unsent> = emptyList(),
    /** Why the box's reply came back into it, where it was refused for want of encryption, as [ChatUiState.refusal]. */
    val refusal: MochiError? = null,
)

/**
 * A message whose send failed after the composer was given the next one: its
 * text, files, the message it replied to and the thread it was a reply in,
 * with why, to retry or copy, and whether it went [unencrypted] by the user's
 * choice, which a retry keeps.
 */
data class Unsent(
    val key: Long,
    val text: String,
    val attachments: List<Uri> = emptyList(),
    val reply: ChatMessage? = null,
    val thread: String = "",
    val error: MochiError,
    val unencrypted: Boolean = false,
) {
    /** Refused for want of encryption: the user may send it unencrypted. */
    val refused: Boolean get() = refusesEncryption(error)
}

/**
 * The send's refusal of a message that would go unencrypted in a linked
 * conversation that was encrypted, which the user may then send unencrypted
 * on purpose. Told apart by its status, as the web tells it: the server's
 * labelled refusal carries its message alone.
 */
internal const val ENCRYPTION_REFUSED = 409

/** Whether [error] is the send's refusal for want of encryption. */
internal fun refusesEncryption(error: MochiError?): Boolean =
    error is MochiError.Unknown && error.code == ENCRYPTION_REFUSED

/** A search match: the message, and the thread it is a reply in, empty on the main timeline. */
data class SearchMatch(val id: String, val thread: String = "")

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val pinnedStore: PinnedChatsStore,
    private val application: Application,
    private val unreadStore: UnreadChatsStore,
) : ViewModel() {

    /** The picked file's real name, for labelling a draft attachment. */
    suspend fun fileName(uri: Uri): String = repository.fileName(uri)

    private val chatId: String = savedStateHandle["chatId"] ?: ""

    /** The share whose images the message box opened with, let go when the screen goes. */
    private val shared: String? = savedStateHandle.get<String>("images")

    // What another app shares opens in the message box, its images with it,
    // for the user to send or not.
    private val _uiState = MutableStateFlow(
        ChatUiState(
            draft = savedStateHandle.get<String>("message").orEmpty(),
            pendingAttachments = SharedImages.take(application, shared),
        ),
    )
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

    /** Whether the reader is at the newest message, as the screen last said. */
    private var bottom = true

    /** Messages are here that were not read: the screen was not showing, or the reader was up the list. */
    private var unseen = false

    /** The key the next unsent message is listed by. */
    private var unsentKey = 0L

    // One-shot toast messages (already localised) — e.g. forward success/failure.
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        load()
        viewModelScope.launch {
            pinnedStore.pinned.collect { pins ->
                _uiState.update { state -> state.copy(isPinned = chatId in pins) }
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
                    gone()
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
                gone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.failed(e.toMochiError())
            }
        }
    }

    /**
     * The chat is no longer the user's: deleted here, or an invitation
     * declined. It is forgotten as the one last viewed, or the router would
     * open it again, and the screen leaves it.
     */
    private fun gone() {
        LastViewedStore.drop(application, CHAT_FEATURE, chatId)
        _uiState.update { state -> state.copy(chatDeleted = true) }
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
                // Kept as the one to reopen only once it has loaded: an id from a
                // link or notification may name a chat that is gone.
                LastViewedStore.set(application, CHAT_FEATURE, chatId)
                subscribeWebSocket(view.chat.key)
                readIfShown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.toMochiError()
                // Gone, or the user is no longer in it: the router must not open it again.
                if (error is MochiError.NotFoundError || error is MochiError.ForbiddenError) {
                    LastViewedStore.drop(application, CHAT_FEATURE, chatId)
                }
                _uiState.update { state -> state.copy(isLoading = false).failed(error) }
            }
        }
    }

    /**
     * Mark the chat read for a reader: one whose screen is showing and who is
     * at the newest message. Otherwise what came is held until [shown] or
     * [atBottom] says the reader has it in front of them.
     */
    private fun readIfShown() {
        if (VisibleEntity.isShowing("chat", chatId) && bottom) {
            unseen = false
            markRead()
        } else {
            unseen = true
        }
    }

    /** The screen is back in front of the user: what came while it was not is read now. */
    fun shown() {
        if (unseen) readIfShown()
    }

    /** Whether the reader [at] the newest message, held read until they come down to it. */
    fun atBottom(at: Boolean) {
        bottom = at
        if (at && unseen) readIfShown()
    }

    /**
     * Advance the read watermark to the newest message. Fire-and-forget: it
     * only moves forward, so a failure is harmless.
     */
    private fun markRead() {
        // Read now, so a mark of unread from the list no longer holds.
        unreadStore.clear(chatId)
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
                _uiState.update { state ->
                    state.copy(
                        messages = merged,
                        more = if (stitched) state.more else msgs.more,
                        cursor = if (stitched) state.cursor else msgs.cursor,
                        receipts = msgs.receipts,
                        error = null,
                    )
                }
                // Read only by a reader: a frame or a reopened socket refreshes a
                // chat that sits behind another screen, or in the background, too.
                readIfShown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state -> state.failed(e.toMochiError()) }
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

    /**
     * Delete messages for everyone. The server deletes only the caller's own
     * and lists those it did, which become tombstones here at once, as the
     * delete frame makes them for every other member.
     */
    fun deleteMessages(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val deleted = repository.deleteMessages(chatId, messageIds).deleted
                deleted.forEach { id -> withdraw(id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state -> state.failed(e.toMochiError()) }
            }
        }
    }

    /**
     * Toggle a reaction on a message (pass "none"/"" to clear), on the main
     * timeline or in the open thread, set from the server's returned counts.
     */
    fun react(messageId: String, reaction: String) {
        viewModelScope.launch {
            try {
                val res = repository.react(chatId, messageId, reaction)
                patch(messageId) { message -> message.copy(reactions = res.reactions, reaction = res.reaction) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state -> state.failed(e.toMochiError()) }
            }
        }
    }

    // ---------------- message search ----------------

    fun openSearch() {
        _uiState.update { state ->
            state.copy(searchOpen = true, searchQuery = "", searchMatches = emptyList(), searchMatchIndex = 0, searchPages = 0)
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _uiState.update { state ->
            state.copy(searchOpen = false, searchQuery = "", searchMatches = emptyList(), searchMatchIndex = 0, searchPages = 0)
        }
    }

    /**
     * Debounced server-side search (2+ chars, as on web); matches are
     * newest-first so match 1 is the latest hit. A reply in a thread comes
     * with the thread it is in, and the message that thread is under.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { state -> state.copy(searchQuery = query, searchMatchIndex = 0, searchPages = 0) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { state -> state.copy(searchMatches = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            try {
                val res = repository.search(chatId, trimmed)
                val matches = res.results
                    .sortedByDescending { result -> result.created }
                    .map { result -> SearchMatch(result.id, result.thread) }
                val roots = res.roots.associateBy { root -> root.id }
                _uiState.update { state ->
                    state.copy(searchMatches = matches, searchRoots = roots, searchMatchIndex = 0, searchPages = 0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state -> state.failed(e.toMochiError()) }
            }
        }
    }

    /** Select which match is active — its message is scrolled to and highlighted. */
    fun setSearchMatchIndex(index: Int) {
        _uiState.update { state -> state.copy(searchMatchIndex = index, searchPages = 0) }
    }

    /**
     * Bring the active match on screen. A reply in a thread is never on the
     * main timeline, so its thread opens; a message of the main timeline not
     * yet loaded is looked for one older page at a time, for at most
     * [SEARCH_PAGES] pages a match, as the web looks.
     */
    fun seekMatch() {
        val state = _uiState.value
        val match = state.searchMatches.getOrNull(state.searchMatchIndex) ?: return
        if (match.thread.isNotEmpty()) {
            if (state.thread?.root?.id == match.thread) return
            val root = state.messages.find { message -> message.id == match.thread }
                ?: state.searchRoots[match.thread]
                ?: return
            openThread(root)
            return
        }
        if (state.messages.any { message -> message.id == match.id }) return
        if (!state.more || state.isLoadingMore || state.searchPages >= SEARCH_PAGES) return
        _uiState.update { current -> current.copy(searchPages = current.searchPages + 1) }
        loadMoreOlder()
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

    /** Close the forward sheet; a forward under way still goes, once. */
    fun closeForward() {
        _uiState.update { state ->
            state.copy(
                forwardMessageIds = emptyList(), forwardChats = emptyList(),
                forwardFriends = emptyList(), forwardLoading = false,
            )
        }
    }

    /** Forward the messages currently open in the forward sheet to the chat [destination]. */
    fun forwardToChat(destination: String) = forward(destination) { messageIds ->
        repository.forwardMessages(chatId, messageIds, destination)
    }

    /** Forward the open messages to [memberId]'s 1-on-1 chat. The server reuses
     *  or creates that chat atomically after validating the messages. */
    fun forwardToFriend(memberId: String) = forward(memberId) { messageIds ->
        repository.forwardToFriend(chatId, messageIds, memberId)
    }

    /**
     * The open messages forwarded to [destination] by [send], once: a tap
     * while one is under way is refused, as the sheet shows that row busy and
     * the others off, so the messages never go twice.
     */
    private fun forward(destination: String, send: suspend (List<String>) -> Unit) {
        val state = _uiState.value
        val messageIds = state.forwardMessageIds
        if (messageIds.isEmpty() || state.forwarding != null) return
        _uiState.update { current -> current.copy(forwarding = destination) }
        viewModelScope.launch {
            try {
                send(messageIds)
                _uiState.update { current ->
                    current.copy(
                        forwardMessageIds = emptyList(),
                        forwardChats = emptyList(),
                        forwardFriends = emptyList(),
                        forwarding = null,
                        selectionMode = false,
                        selectedIds = emptySet(),
                    )
                }
                _events.emit(application.getString(R.string.chat_forward_success))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { current -> current.copy(forwarding = null).failed(e.toMochiError()) }
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
     * Start editing [message], in the main composer or, for a reply in the
     * open thread, in the thread's. The edit has a text of its own: what was
     * being written, the message it replied to and its files wait, untouched,
     * for the edit to end.
     */
    fun startEdit(message: ChatMessage) {
        _uiState.update { state -> state.copy(editing = message, editDraft = message.body) }
    }

    fun setEditDraft(text: String) {
        _uiState.update { state -> if (state.editing == null) state else state.copy(editDraft = text) }
    }

    /** The edit ends; the composer has what it had before it began. */
    fun cancelEdit() {
        _uiState.update { state -> state.copy(editing = null, editDraft = "") }
    }

    /**
     * Save the edit. The server stamps `edited` and fans the new body out to
     * the other members; refresh so this client shows what they will see
     * rather than a locally-guessed row. A text past [MESSAGE_MAXIMUM] is not
     * sent: the composer says by how much it is over.
     */
    fun saveEdit() {
        val state = _uiState.value
        val message = state.editing ?: return
        val trimmed = state.editDraft.trim()
        if (overLimit(trimmed)) return
        if (trimmed.isEmpty() || trimmed == message.body) {
            cancelEdit()
            return
        }
        viewModelScope.launch {
            try {
                repository.editMessage(chatId, message.id, trimmed)
                _uiState.update { current ->
                    if (current.editing?.id == message.id) current.copy(editing = null, editDraft = "") else current
                }
                refresh()
                if (message.thread.isNotEmpty()) loadThread()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { current -> current.failed(e.toMochiError()) }
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

    /**
     * Send the composer's message. Its text, files and reply target leave the
     * composer as the send starts, so whatever is typed meanwhile belongs to
     * the next message; a send that fails puts them back if the composer is
     * still empty, and lists the message as unsent otherwise. A text past
     * [MESSAGE_MAXIMUM] is not sent, its files not uploaded: the composer
     * says by how much it is over. [unencrypted] sends it so, where the user
     * chose to after the send refused it for want of encryption.
     */
    fun sendMessage(unencrypted: Boolean = false) {
        val state = _uiState.value
        if (state.isSending) return
        val text = state.draft.trim()
        if (text.isEmpty() && state.pendingAttachments.isEmpty()) return
        if (overLimit(text)) return
        // The message leaves the composer, which the user may then write the next one in.
        _uiState.update { current -> current.sending() }
        send(text, state.pendingAttachments, state.replyingTo, unencrypted)
    }

    /**
     * Send again a message whose send failed; it leaves the unsent list while
     * it goes. [unencrypted] sends it so, where it was refused for want of
     * encryption; one already sent so goes so again.
     */
    fun retry(key: Long, unencrypted: Boolean = false) {
        val state = _uiState.value
        if (state.isSending) return
        val message = state.unsent.find { unsent -> unsent.key == key } ?: return
        val clear = unencrypted || message.unencrypted
        _uiState.update { current -> current.copy(unsent = current.unsent.filterNot { unsent -> unsent.key == key }) }
        if (message.thread.isNotEmpty()) {
            sendReply(message.thread, message.text, clear)
        } else {
            // From its row, not the composer, which keeps the next message.
            _uiState.update { current -> current.copy(isSending = true) }
            send(message.text, message.attachments, message.reply, clear)
        }
    }

    /** Give up on a message whose send failed. */
    fun discard(key: Long) {
        _uiState.update { state -> state.copy(unsent = state.unsent.filterNot { unsent -> unsent.key == key }) }
    }

    private fun send(text: String, attachments: List<Uri>, reply: ChatMessage?, unencrypted: Boolean) {
        viewModelScope.launch {
            try {
                if (attachments.isEmpty()) {
                    val sent = repository.sendMessage(chatId, text, reply = reply?.id, unencrypted = unencrypted)
                    sent.unsent.orEmpty().forEach { _events.emit(it) }
                    refresh()
                } else {
                    tracker.track {
                        val sent = repository.sendMessageFromUris(
                            chatId, text, attachments, reply = reply?.id, tracker = tracker, unencrypted = unencrypted,
                        )
                        // What the message went without on another network.
                        sent.unsent.orEmpty().forEach { _events.emit(it) }
                        // Inside the upload, so the files in flight give way
                        // to the message with nothing blank between.
                        refresh()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failed = Unsent(unsentKey++, text, attachments, reply, error = e.toMochiError(), unencrypted = unencrypted)
                _uiState.update { state -> state.unsent(failed) }
            } finally {
                _uiState.update { state -> state.copy(isSending = false) }
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
                    // The answer first, then the state as it stands by then:
                    // the request lasts as long as the other server takes.
                    val presence = repository.presence(chatId)
                    _uiState.update { state -> state.copy(presence = presence) }
                } catch (e: CancellationException) {
                    throw e
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

    /** Close the thread, and any edit of one of its replies with it. */
    fun closeThread() {
        _uiState.update { state ->
            if (state.editing?.thread?.isNotEmpty() == true) {
                state.copy(thread = null, editing = null, editDraft = "")
            } else {
                state.copy(thread = null)
            }
        }
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

    /**
     * Reply in the open thread. As in the main composer, the text leaves the
     * box as the send starts; a send that fails puts it back if the box is
     * still empty, and lists the reply as unsent in the thread otherwise.
     * [unencrypted] sends it so, as [sendMessage] does.
     */
    fun sendThreadReply(unencrypted: Boolean = false) {
        val thread = _uiState.value.thread ?: return
        val text = thread.draft.trim()
        if (text.isEmpty() || thread.isSending || overLimit(text)) return
        // The reply leaves the box, which the user may then write the next one in.
        _uiState.update { state -> state.copy(thread = state.thread?.copy(draft = "", refusal = null)) }
        sendReply(thread.root.id, text, unencrypted)
    }

    /** Send again a reply whose send failed; [unencrypted] as [retry]. */
    fun retryReply(key: Long, unencrypted: Boolean = false) {
        val thread = _uiState.value.thread ?: return
        if (thread.isSending) return
        val reply = thread.unsent.find { unsent -> unsent.key == key } ?: return
        _uiState.update { state ->
            state.copy(thread = state.thread?.copy(unsent = thread.unsent.filterNot { unsent -> unsent.key == key }))
        }
        sendReply(thread.root.id, reply.text, unencrypted || reply.unencrypted)
    }

    /** Give up on a reply whose send failed. */
    fun discardReply(key: Long) {
        _uiState.update { state ->
            state.copy(thread = state.thread?.let { thread -> thread.copy(unsent = thread.unsent.filterNot { unsent -> unsent.key == key }) })
        }
    }

    /** [text] sent into [root]'s thread; a box holding the next reply keeps it. */
    private fun sendReply(root: String, text: String, unencrypted: Boolean) {
        _uiState.update { state -> state.copy(thread = state.thread?.copy(isSending = true)) }
        viewModelScope.launch {
            try {
                repository.sendMessage(chatId, text, thread = root, unencrypted = unencrypted)
                _uiState.update { state -> state.copy(thread = state.thread?.copy(isSending = false)) }
                loadThread()
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failed = Unsent(unsentKey++, text, thread = root, error = e.toMochiError(), unencrypted = unencrypted)
                _uiState.update { state ->
                    val thread = state.thread?.takeIf { thread -> thread.root.id == root }
                    when {
                        // The thread closed meanwhile: the reply waits with the
                        // conversation's unsent messages, to go into it again.
                        thread == null -> state.copy(unsent = state.unsent + failed)
                        // Back into the empty box, the refusal said under it
                        // with the offer to send it unencrypted.
                        thread.draft.isBlank() && failed.refused ->
                            state.copy(thread = thread.copy(isSending = false, draft = text, refusal = failed.error))
                        thread.draft.isBlank() ->
                            state.copy(thread = thread.copy(isSending = false, draft = text, refusal = null)).failed(failed.error)
                        else -> state.copy(thread = thread.copy(isSending = false, unsent = thread.unsent + failed))
                    }
                }
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
                _uiState.update { state -> state.copy(answering = false) }
                gone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(answering = false).failed(e.toMochiError())
            }
        }
    }

    private fun subscribeWebSocket(key: String) {
        if (key.isEmpty() || subscriptionId != null) return
        val serverUrl = sessionManager.getServerUrlBlocking()
        // The socket hands each frame over on the main thread, where every
        // other change to the state is made.
        subscriptionId = webSocket.subscribe(serverUrl, key, app = "chat") { event -> framed(event) }
    }

    /** One frame from the chat's socket, applied to what is held. */
    internal fun framed(event: WebSocketEvent) {
        val ev = event.event
        when {
            event.type == REOPEN -> catchUp()
            ev == "rename" -> {
                val newName = event.name ?: return
                _uiState.update { state -> state.copy(chat = state.chat.copy(name = newName)) }
            }
            ev == "leave" || ev == "member/remove" -> {
                val memberId = event.member ?: return
                _uiState.update { state ->
                    state.copy(chat = state.chat.copy(members = state.chat.members.filterNot { it.id == memberId }))
                }
            }
            ev == "removed" -> {
                _uiState.update { state -> state.copy(chat = state.chat.copy(status = ChatStatus.REMOVED)) }
            }
            ev == "member/add" -> {
                // The members as the server now has them.
                viewModelScope.launch {
                    try {
                        val members = repository.getMembers(chatId)
                        _uiState.update { state -> state.copy(chat = state.chat.copy(members = members)) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // The members stay as they were; the next read brings the new one.
                    }
                }
            }
            ev == "delete" -> deleted(event)
            ev == "edit" -> revised(event)
            ev == "reaction" -> reacted(event)
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
            ev == null && event.body != null -> arrived(event)
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
        val added = if (message.thread.isEmpty()) post(message) else answer(message, frameThread(event.root))
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
     * [reply] counted on its root and shown in its bubble, and added to its
     * thread where that is open. A reply the open thread or the bubble already
     * holds was counted when it was read. The bubble keeps the newest
     * [THREAD_ANSWERS] replies, as the server sends them; the count says how
     * many came before.
     */
    private fun answer(reply: ChatMessage, summary: ThreadSummary? = null): Boolean {
        val state = _uiState.value
        val open = state.thread?.takeIf { thread -> thread.root.id == reply.thread }
        val shown = state.messages.find { message -> message.id == reply.thread }
        val held = open?.replies?.any { message -> message.id == reply.id } == true ||
            shown?.answers?.any { message -> message.id == reply.id } == true
        // The thread as the server now has it, this reply included: set, not
        // counted, so a frame sent twice or a reply never held leaves it right.
        if (summary != null) {
            _uiState.value = state.copy(
                messages = state.messages.patched(reply.thread, summary::on),
                thread = open?.copy(
                    root = summary.on(open.root),
                    replies = if (held) open.replies else open.replies.adding(reply, open.more),
                ) ?: state.thread,
            )
            return !held
        }
        if (held) return false
        // One older than every reply the bubble shows, on a root with earlier
        // replies than it shows, may be one of those, already counted: the
        // page is read again rather than guessed at, as the web does.
        val first = shown?.answers?.firstOrNull()
        if (shown != null && first != null && shown.replies > shown.answers.size &&
            compareValuesBy(reply, first, { it.created }, { it.id }) < 0
        ) {
            refresh()
            if (open != null) loadThread()
            return true
        }
        val counted = { root: ChatMessage ->
            root.copy(
                replies = root.replies + 1,
                latest = maxOf(root.latest, reply.created),
                answers = (root.answers + reply).sortedWith(compareBy({ it.created }, { it.id })).takeLast(THREAD_ANSWERS),
            )
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
     * A reply in the open thread or in its root's bubble stops counting on its
     * root and leaves the bubble. One held nowhere may be a reply in a thread
     * the list does not hold, whose root's count only the server knows, so the
     * newest page is read again where any count shows.
     */
    private fun deleted(event: WebSocketEvent) {
        frames++
        val id = event.message
        if (id.isNullOrEmpty()) {
            refresh()
            return
        }
        val summary = event.thread?.takeIf { thread -> thread.isNotEmpty() }?.let { thread ->
            frameThread(event.root)?.let { root -> thread to root }
        }
        if (summary != null) {
            // The thread the reply was in, as the server now has it.
            patch(id) { message -> message.tombstone() }
            patch(summary.first, summary.second::on)
            return
        }
        withdraw(id)
    }

    /**
     * Message [id], deleted for everyone, as a tombstone wherever it is held,
     * no longer counted where it was a reply, when no frame says how its
     * thread now stands.
     */
    private fun withdraw(id: String) {
        val state = _uiState.value
        val reply = state.thread?.replies?.find { held -> held.id == id && !held.deleted }
            ?: state.messages.firstNotNullOfOrNull { root -> root.answers.find { held -> held.id == id } }
        val held = patch(id) { message -> message.tombstone() }
        if (reply != null) {
            // A root with earlier replies than its bubble shows has the next
            // one to show in a shown one's place, which only the server holds.
            val root = state.messages.find { message -> message.id == reply.thread }
            uncount(reply)
            if (root != null && root.replies > root.answers.size && root.answers.any { held -> held.id == id }) refresh()
        } else if (!held && state.messages.any { message -> message.replies > 0 }) {
            refresh()
        }
    }

    /** [reply], just deleted, no longer counted on its root nor shown in its bubble. */
    private fun uncount(reply: ChatMessage) {
        val state = _uiState.value
        val uncounted = { root: ChatMessage ->
            val replies = (root.replies - 1).coerceAtLeast(0)
            val answers = root.answers.filter { held -> held.id != reply.id }
            val latest = if (replies == 0) 0 else answers.maxOfOrNull { held -> held.created } ?: root.latest
            root.copy(replies = replies, latest = latest, answers = answers)
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
        // The edit's warning replaces the message's; an older server's says none.
        val warning = event.warning.orEmpty()
        patch(id) { message -> message.rewritten(body, edited, warning) }
        // A reply in its root's bubble, held there with the thread closed.
        val state = _uiState.value
        if (state.messages.any { root -> root.answers.any { held -> held.id == id } }) {
            _uiState.value = state.copy(
                messages = state.messages.map { root ->
                    if (root.answers.any { held -> held.id == id }) {
                        root.copy(answers = root.answers.patched(id) { held -> held.rewritten(body, edited, warning) })
                    } else {
                        root
                    }
                },
            )
        }
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
        SharedImages.release(application, shared)
    }

    private companion object {

        /** Messages fetched per page on load, refresh, and load-more. */
        const val MESSAGE_PAGE_SIZE = 30

        /** How many older pages are read looking for one search match, as the web reads. */
        const val SEARCH_PAGES = 20
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
 * The state as a send starts: the message leaves the composer, which the
 * user may then write the next one in.
 */
internal fun ChatUiState.sending(): ChatUiState =
    copy(draft = "", pendingAttachments = emptyList(), replyingTo = null, isSending = true, refusal = null)

/**
 * The state after [message]'s send failed. Into a composer still empty it
 * goes back, text, files and reply target, with the failure said once, or,
 * refused for want of encryption, said under the composer with the offer to
 * send it unencrypted; a composer holding the next message keeps it, and
 * [message] is listed as unsent, saying why itself.
 */
internal fun ChatUiState.unsent(message: Unsent): ChatUiState =
    if (message.thread.isEmpty() && draft.isBlank() && pendingAttachments.isEmpty() && replyingTo == null) {
        val back = copy(draft = message.text, pendingAttachments = message.attachments, replyingTo = message.reply)
        if (message.refused) back.copy(refusal = message.error) else back.copy(refusal = null).failed(message.error)
    } else {
        copy(unsent = unsent + message)
    }

/** The longest message the server takes, in code points, as the web counts it. */
internal const val MESSAGE_MAXIMUM = 10_000

/** From how long a message the composer shows its length, as the web shows it. */
internal const val MESSAGE_COUNTED = MESSAGE_MAXIMUM - 500

/** How long [text] is as the server counts it: in code points. */
internal fun length(text: String): Int = text.codePointCount(0, text.length)

/** Whether [text] is longer than a message may be. */
internal fun overLimit(text: String): Boolean = length(text) > MESSAGE_MAXIMUM

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
        warning = event.warning.orEmpty(),
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

/** A root's thread as a frame carries it: how many replies, the latest's time, and the newest. */
internal data class ThreadSummary(val replies: Int, val latest: Long, val answers: List<ChatMessage>) {
    /** [root] with its thread set to this. */
    fun on(root: ChatMessage): ChatMessage = root.copy(replies = replies, latest = latest, answers = answers)
}

/** The root's thread a chat app's reply or delete frame carries; null from an older one, or one of another shape. */
internal fun frameThread(element: JsonElement?): ThreadSummary? {
    val fields = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    if (!fields.number("replies") || !fields.number("latest")) return null
    val answers = fields.get("answers")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    return ThreadSummary(
        replies = fields.get("replies").asInt,
        latest = fields.get("latest").asLong,
        answers = answers.mapNotNull { item ->
            val answer = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            if (!answer.text("id") || !answer.number("created")) return@mapNotNull null
            try {
                frames.fromJson(answer, ChatMessage::class.java)
                    ?.copy(attachments = frameAttachments(answer.get("attachments")), quote = frameQuote(answer.get("quote")))
            } catch (_: JsonParseException) {
                null
            }
        },
    )
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

/**
 * A message deleted for everyone, as the messages page shows one. Its thread
 * stays, counted, for the members' replies under it are still there to read.
 */
internal fun ChatMessage.tombstone(): ChatMessage = copy(
    deleted = true,
    body = "",
    reply = null,
    attachments = emptyList(),
    quote = null,
    reactions = emptyMap(),
    reaction = null,
    warning = "",
)

/**
 * An edit of this message, applied only if newer than what is held: edits
 * cross on the way, and the last writer wins. A tombstone is never rewritten.
 * The edit's [warning] replaces the message's.
 */
internal fun ChatMessage.rewritten(body: String, edited: Long, warning: String = ""): ChatMessage =
    if (deleted || this.edited >= edited) this else copy(body = body, edited = edited, warning = warning)

/** [block]'s answer as a [Result], except a cancellation, which still cancels. */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
