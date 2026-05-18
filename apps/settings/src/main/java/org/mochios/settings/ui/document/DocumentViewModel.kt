package org.mochios.settings.ui.document

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
import org.mochios.settings.api.DocumentApi
import retrofit2.Response
import javax.inject.Inject

data class DocumentUiState(
    val kind: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedContent: String = "",
    val draft: String = "",
    val savedToast: Boolean = false,
    val error: MochiError? = null,
)

@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val api: DocumentApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val kind: String = savedStateHandle.get<String>("kind").orEmpty()

    private val _uiState = MutableStateFlow(DocumentUiState(kind = kind))
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.getDocument(kind).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    savedContent = data.content,
                    draft = data.content,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun onDraftChange(content: String) {
        _uiState.value = _uiState.value.copy(draft = content)
    }

    fun save() {
        val draft = _uiState.value.draft
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, savedToast = false)
            try {
                api.updateDocument(kind, draft).bodyOrThrowUnit()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savedContent = draft,
                    savedToast = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    fun consumeSavedToast() {
        _uiState.value = _uiState.value.copy(savedToast = false)
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }

    private fun Response<Unit>.bodyOrThrowUnit() {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
    }
}
