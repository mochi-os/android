package org.mochios.forums.ui.forumlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.forums.model.Forum
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class ForumListUiState(
    val forums: List<Forum> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val searchQuery: String = "",
    val showSearch: Boolean = false,
    val showCreateDialog: Boolean = false
)

@HiltViewModel
class ForumListViewModel @Inject constructor(
    private val repository: ForumsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForumListUiState())
    val uiState: StateFlow<ForumListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val r = repository.listForums()
                _uiState.value = _uiState.value.copy(forums = r.forums, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val r = repository.listForums()
                _uiState.value = _uiState.value.copy(forums = r.forums, isRefreshing = false, error = null)
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

    fun updateSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q)
    }

    fun filteredForums(): List<Forum> {
        val q = _uiState.value.searchQuery.lowercase().trim()
        if (q.isEmpty()) return _uiState.value.forums
        return _uiState.value.forums.filter { it.name.lowercase().contains(q) }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun createForum(name: String, privacy: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            try {
                repository.createForum(name, privacy)
                _uiState.value = _uiState.value.copy(isCreating = false, showCreateDialog = false)
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false, error = e.toMochiError())
            }
        }
    }

    fun unsubscribe(forumId: String) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(forumId)
                _uiState.value = _uiState.value.copy(
                    forums = _uiState.value.forums.filterNot { it.id == forumId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
