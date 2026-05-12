package org.mochios.forums.ui.settings

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
import org.mochios.forums.model.Forum
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class ForumSettingsUiState(
    val forum: Forum = Forum(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val deleted: Boolean = false
)

@HiltViewModel
class ForumSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository
) : ViewModel() {

    private val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _uiState = MutableStateFlow(ForumSettingsUiState())
    val uiState: StateFlow<ForumSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val r = repository.viewForum(forumId)
                _uiState.value = _uiState.value.copy(forum = r.forum, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repository.renameForum(forumId, newName)
                _uiState.value = _uiState.value.copy(
                    forum = _uiState.value.forum.copy(name = newName),
                    isSaving = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                repository.deleteForum(forumId)
                _uiState.value = _uiState.value.copy(deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
