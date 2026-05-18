package org.mochios.settings.ui.replication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.settings.api.ReplicationApi
import org.mochios.settings.api.ReplicationData
import org.mochios.settings.api.ReplicationHost
import org.mochios.settings.api.ReplicationLink
import retrofit2.Response
import javax.inject.Inject

data class ReplicationUiState(
    val isLoading: Boolean = true,
    val links: List<ReplicationLink> = emptyList(),
    val hosts: List<ReplicationHost> = emptyList(),
    val error: MochiError? = null,
)

@HiltViewModel
class ReplicationViewModel @Inject constructor(
    private val api: ReplicationApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReplicationUiState())
    val uiState: StateFlow<ReplicationUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.getReplication().bodyOrThrow()
                _uiState.value = ReplicationUiState(
                    isLoading = false,
                    links = data.links,
                    hosts = data.hosts,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun approve(peer: String) = mutate { api.approveLink(peer).bodyOrThrow() }
    fun deny(peer: String) = mutate { api.denyLink(peer).bodyOrThrow() }
    fun remove(peer: String) = mutate { api.removeHost(peer).bodyOrThrow() }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
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
