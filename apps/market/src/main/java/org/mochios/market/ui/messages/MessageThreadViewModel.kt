package org.mochios.market.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.model.Message
import org.mochios.market.model.ThreadListingPreview
import org.mochios.market.model.MarketThread
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * UI state for [MessageThreadScreen]. Holds the thread metadata, the
 * embedded listing preview (for the top-bar chip), the running message
 * list (ordered oldest first; the screen flips it for reverseLayout),
 * the in-progress draft, and an error / send-in-flight flag.
 */
data class MessageThreadUiState(
    val thread: MarketThread? = null,
    val listing: ThreadListingPreview = ThreadListingPreview(),
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: MochiError? = null,
)

/**
 * One-shot events surfaced to the screen. Errors land in a snackbar; the
 * screen also listens for new-message events to keep the auto-scroll
 * anchored on the latest message.
 */
sealed interface MessageThreadEvent {
    data class Error(val error: MochiError) : MessageThreadEvent
    data class Appended(val message: Message) : MessageThreadEvent
}

/**
 * ViewModel for a single buyer-seller conversation. Reads `listingId` and
 * `threadId` from [SavedStateHandle] (the nav graph wires both as
 * `NavType.StringType`), then:
 *
 *  1. fires [refresh] to load the thread + messages via
 *     [MarketRepository.getThread];
 *  2. calls [MarketRepository.markMessagesRead] once messages are loaded so
 *     the inbox unread count drops to zero;
 *  3. exposes [sendMessage] to append a new message (followed by an
 *     immediate optimistic update + a server-acked replacement on the
 *     response);
 *  4. accepts incoming WebSocket events via [ingestRemote] which the
 *     screen pumps in from the lib's `rememberGameWebSocket` helper.
 */
@HiltViewModel
class MessageThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MarketRepository,
) : ViewModel() {

    val listingId: String = savedStateHandle.get<String>("listingId").orEmpty()
    val threadId: String = savedStateHandle.get<String>("threadId").orEmpty()

    /**
     * Thread id used for every thread-scoped call. Seeded from the route arg;
     * when the screen is opened from a listing the arg is the sentinel "new"
     * (→ 0), and [refresh] fills this in via `threads/create` before loading.
     */
    private var resolvedThreadId: String = threadId.takeIf { it.isNotBlank() && it != "new" } ?: ""

    private val _state = MutableStateFlow(MessageThreadUiState())
    val state: StateFlow<MessageThreadUiState> = _state.asStateFlow()

    private val _events = Channel<MessageThreadEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // Opened from a listing? The route carries threadId="new", so
                // open (or reuse) the thread for this listing first — the
                // server returns the existing thread when one already exists.
                // Mirrors the web flow's threads/create → threads/get sequence.
                if (resolvedThreadId.isEmpty()) {
                    val listing = listingId.ifBlank {
                        throw IllegalStateException("Missing listing id for new thread")
                    }
                    resolvedThreadId = repo.createThread(listing).id
                }
                val response = repo.getThread(resolvedThreadId)
                _state.value = _state.value.copy(
                    thread = response.thread,
                    listing = response.listing,
                    messages = response.messages,
                    isLoading = false,
                )
                markRead()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    private fun markRead() {
        viewModelScope.launch {
            try {
                repo.markMessagesRead(resolvedThreadId)
            } catch (_: Exception) {
                // Non-fatal — read-state syncs the next time the thread loads.
            }
        }
    }

    fun updateDraft(value: String) {
        _state.value = _state.value.copy(draft = value)
    }

    fun sendMessage() {
        val trimmed = _state.value.draft.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSending = true)
            try {
                val saved = repo.sendMessage(resolvedThreadId, trimmed)
                _state.value = _state.value.copy(
                    draft = "",
                    isSending = false,
                    messages = _state.value.messages + saved,
                )
                _events.send(MessageThreadEvent.Appended(saved))
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSending = false)
                _events.send(MessageThreadEvent.Error(e.toMochiError()))
            }
        }
    }

    /**
     * Ingest a message that arrived over the per-thread WebSocket. The
     * server emits both directions on the same channel so both peers
     * stay in sync; we dedup on message id since `sendMessage` already
     * appended our own messages locally.
     */
    fun ingestRemote(message: Message) {
        val existing = _state.value.messages
        if (existing.any { it.id == message.id && message.id.isNotEmpty() }) return
        _state.value = _state.value.copy(messages = existing + message)
        viewModelScope.launch { _events.send(MessageThreadEvent.Appended(message)) }
    }
}
