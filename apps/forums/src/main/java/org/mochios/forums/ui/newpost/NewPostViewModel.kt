package org.mochios.forums.ui.newpost

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
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class NewPostUiState(
    val isPosting: Boolean = false,
    val createdForum: String = "",
    val createdPost: String = "",
    val error: MochiError? = null
)

@HiltViewModel
class NewPostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _uiState = MutableStateFlow(NewPostUiState())
    val uiState: StateFlow<NewPostUiState> = _uiState.asStateFlow()

    fun submit(title: String, body: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPosting = true, error = null)
            try {
                val r = repository.createPost(forumId, title, body)
                _uiState.value = _uiState.value.copy(
                    isPosting = false,
                    createdForum = r.forum,
                    createdPost = r.post
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isPosting = false, error = e.toMochiError())
            }
        }
    }
}
