package org.mochios.chat.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.chat.model.ChatDetail
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

data class ChatUiState(
    val chat: ChatDetail = ChatDetail(),
    val identity: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Long? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val error: MochiError? = null,
    val pendingAttachments: List<Uri> = emptyList(),
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val webSocket: MochiWebSocket,
    private val sessionManager: SessionManager,
    private val application: Application,
) : ViewModel() {

    private val chatId: String = savedStateHandle["chatId"] ?: ""
    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var subscriptionId: String? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val view = repository.viewChat(chatId)
                val msgs = repository.getMessages(chatId)
                _uiState.value = _uiState.value.copy(
                    chat = view.chat,
                    identity = view.identity,
                    messages = msgs.messages,
                    hasMore = msgs.hasMore,
                    nextCursor = msgs.nextCursor,
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
                val msgs = repository.getMessages(chatId)
                _uiState.value = _uiState.value.copy(
                    messages = msgs.messages,
                    hasMore = msgs.hasMore,
                    nextCursor = msgs.nextCursor,
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
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val older = repository.getMessages(chatId, before = cursor)
                _uiState.value = _uiState.value.copy(
                    messages = older.messages + _uiState.value.messages,
                    hasMore = older.hasMore,
                    nextCursor = older.nextCursor,
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

    fun sendMessage(body: String) {
        val trimmed = body.trim()
        val attachments = _uiState.value.pendingAttachments
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                if (attachments.isEmpty()) {
                    repository.sendMessage(chatId, trimmed)
                } else {
                    repository.sendMessageFromUris(chatId, trimmed, attachments, application.contentResolver)
                }
                _uiState.value = _uiState.value.copy(pendingAttachments = emptyList())
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
}
