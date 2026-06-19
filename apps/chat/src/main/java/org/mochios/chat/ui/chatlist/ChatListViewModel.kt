package org.mochios.chat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.chat.model.Chat
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: MochiError? = null,
    val searchQuery: String = "",
    val showSearch: Boolean = false
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val chats = repository.listChats()
                _uiState.value = _uiState.value.copy(chats = chats, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val chats = repository.listChats()
                _uiState.value = _uiState.value.copy(chats = chats, isRefreshing = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = e.toMochiError())
            }
        }
    }

    fun toggleSearch() {
        val current = _uiState.value
        _uiState.value = current.copy(
            showSearch = !current.showSearch,
            searchQuery = if (current.showSearch) "" else current.searchQuery
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun filteredChats(): List<Chat> {
        val query = _uiState.value.searchQuery.lowercase().trim()
        val base = _uiState.value.chats
        val filtered = if (query.isEmpty()) base else base.filter { it.name.lowercase().contains(query) }
        // Most recent activity first; chats with no activity yet (updated=0)
        // sink to the bottom in name order so they remain reachable but don't
        // outrank chats with real messages.
        return filtered.sortedWith(
            compareByDescending<Chat> { it.updated }.thenBy(NaturalCompare) { it.name }
        )
    }

    fun deleteLeftChat(chatId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChat(chatId)
                _uiState.value = _uiState.value.copy(
                    chats = _uiState.value.chats.filterNot { it.id == chatId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
