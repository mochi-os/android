package org.mochios.settings.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.settings.api.SessionsApi
import org.mochios.settings.api.Session
import retrofit2.Response
import javax.inject.Inject

data class SessionsUiState(
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    /** Sorted accessed-desc; index 0 is the current session. */
    val sessions: List<Session> = emptyList(),
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val api: SessionsApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val sessions = api.listSessions().bodyOrThrow().sessions
                    .sortedByDescending { it.accessed }
                _uiState.value = SessionsUiState(isLoading = false, sessions = sessions)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun revoke(id: String) {
        viewModelScope.launch {
            try {
                api.revokeSession(id).bodyOrThrow()
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }
}
