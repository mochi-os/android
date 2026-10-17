// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.SessionManager
import org.mochios.market.model.MarketThread
import org.mochios.market.model.Message
import org.mochios.market.model.ThreadListingPreview
import org.mochios.market.repository.MarketRepository

/**
 * [messages] are oldest first; the screen reverses them for its reverseLayout
 * list. [me] is the signed-in identity, whose messages sit on the right
 * whether they are the buyer or the seller. [photo] is the listing's
 * primary photo id for the header's thumbnail, blank when it has none.
 */
data class MessageThreadUiState(
    val thread: MarketThread? = null,
    val listing: ThreadListingPreview = ThreadListingPreview(),
    val messages: List<Message> = emptyList(),
    val me: String = "",
    val photo: String = "",
    val draft: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: MochiError? = null,
)

sealed interface MessageThreadEvent {
    data class Error(val error: MochiError) : MessageThreadEvent
    data class Appended(val message: Message) : MessageThreadEvent
}

@HiltViewModel
class MessageThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MarketRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val listingId: String = savedStateHandle.get<String>("listingId").orEmpty()
    val threadId: String = savedStateHandle.get<String>("threadId").orEmpty()

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
                val me = sessionManager.boundIdentity.first().orEmpty()
                val photo = response.listing.photo?.id.orEmpty()
                    .ifBlank { listingPhoto(response.listing.id.ifBlank { listingId }) }
                _state.value = _state.value.copy(
                    thread = response.thread,
                    me = me,
                    photo = photo,
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

    /**
     * The first photo of [listing] by rank, read from its photo list when the
     * thread's preview of it carries none, as the listing page reads them, and
     * from its detail's primary photo after that; blank when neither has one.
     */
    private suspend fun listingPhoto(listing: String): String {
        if (listing.isBlank()) {
            return ""
        }
        return try {
            repo.listPhotos(listing).minByOrNull { photo -> photo.rank }?.id.orEmpty()
                .ifBlank { repo.getListing(listing).listing.photo?.id.orEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
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
}
