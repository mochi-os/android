package org.mochios.settings.ui.systemreplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.settings.api.BootstrapEntry
import org.mochios.settings.api.PendingJoin
import org.mochios.settings.api.SystemReplicationApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController
import retrofit2.Response
import javax.inject.Inject

data class SystemReplicationUiState(
    val isLoading: Boolean = true,
    val peer: String = "",
    val pair: List<String> = emptyList(),
    val joins: List<PendingJoin> = emptyList(),
    val bootstrap: List<BootstrapEntry> = emptyList(),
    val error: MochiError? = null,
)

sealed class SystemReplicationEvent {
    data class JoinApproved(val success: Boolean) : SystemReplicationEvent()
    data class JoinDenied(val success: Boolean) : SystemReplicationEvent()
    data class PairRemoved(val success: Boolean) : SystemReplicationEvent()
    data class PeerCopied(val success: Boolean) : SystemReplicationEvent()
}

@HiltViewModel
class SystemReplicationViewModel @Inject constructor(
    private val api: SystemReplicationApi,
    stepUpClient: SettingsStepUpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemReplicationUiState())
    val uiState: StateFlow<SystemReplicationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SystemReplicationEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SystemReplicationEvent> = _events.asSharedFlow()

    /** Step-up gate: approving a join replicates every user's private keys to
     *  the peer, so the operator re-verifies their login factor(s) first. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.value = _uiState.value.copy(error = e.toMochiError()) },
    )

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.getSystemReplication().bodyOrThrow()
                _uiState.value = SystemReplicationUiState(
                    isLoading = false,
                    peer = data.peer,
                    pair = data.pair,
                    joins = data.joins,
                    bootstrap = data.bootstrap,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun approveJoin(peer: String) = stepUp.request { token ->
        try {
            api.approveJoin(peer, token).bodyOrThrow()
            _events.emit(SystemReplicationEvent.JoinApproved(true))
            refresh()
        } catch (e: Exception) {
            _events.emit(SystemReplicationEvent.JoinApproved(false))
            _uiState.value = _uiState.value.copy(error = e.toMochiError())
        }
    }

    fun denyJoin(peer: String) {
        viewModelScope.launch {
            try {
                api.denyJoin(peer).bodyOrThrow()
                _events.emit(SystemReplicationEvent.JoinDenied(true))
                refresh()
            } catch (e: Exception) {
                _events.emit(SystemReplicationEvent.JoinDenied(false))
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun removePair(peer: String) {
        viewModelScope.launch {
            try {
                api.removePair(peer).bodyOrThrow()
                _events.emit(SystemReplicationEvent.PairRemoved(true))
                refresh()
            } catch (e: Exception) {
                _events.emit(SystemReplicationEvent.PairRemoved(false))
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun reportPeerCopied(success: Boolean) {
        viewModelScope.launch { _events.emit(SystemReplicationEvent.PeerCopied(success)) }
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        @Suppress("UNCHECKED_CAST")
        return (body() ?: Unit) as T
    }
}
