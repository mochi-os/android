package org.mochios.wikis.ui.join

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
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [JoinWikiScreen] — the "enter an entity ID directly" form.
 * Mirrors web's `JoinWikiPage`
 * (`apps/wikis/web/src/routes/_authenticated/join.tsx`): a single text
 * field for the wiki entity id, plus a Replicate button that calls
 * `POST -/subscribe` with no server hint and navigates to the wiki on
 * success.
 */
data class JoinWikiUiState(
    val target: String = "",
    val isSubmitting: Boolean = false,
    val error: MochiError? = null,
)

sealed interface JoinEvent {
    data class Success(val wikiId: String) : JoinEvent
    data class Failed(val error: MochiError) : JoinEvent
}

@HiltViewModel
class JoinWikiViewModel @Inject constructor(
    private val repository: WikisRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinWikiUiState())
    val uiState: StateFlow<JoinWikiUiState> = _uiState.asStateFlow()

    private val _events = Channel<JoinEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun updateTarget(target: String) {
        _uiState.value = _uiState.value.copy(target = target, error = null)
    }

    fun submit() {
        val target = _uiState.value.target.trim()
        if (target.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            try {
                val result = repository.joinWiki(target = target, server = null)
                val landingId = result.fingerprint.ifEmpty { result.id }
                _uiState.value = _uiState.value.copy(isSubmitting = false)
                _events.send(JoinEvent.Success(landingId))
            } catch (e: Exception) {
                val mochiError = e.toMochiError()
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = mochiError,
                )
                _events.send(JoinEvent.Failed(mochiError))
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
