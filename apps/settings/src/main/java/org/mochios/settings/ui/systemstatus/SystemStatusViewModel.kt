package org.mochios.settings.ui.systemstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.settings.api.NetworkInfo
import org.mochios.settings.api.PeerEntry
import org.mochios.settings.api.ServerCounts
import org.mochios.settings.api.SystemStatusApi
import org.mochios.settings.api.SystemUpdateInfo
import retrofit2.Response
import javax.inject.Inject

data class SystemStatusUiState(
    val isLoading: Boolean = true,
    val isInstalling: Boolean = false,
    val serverVersion: String = "",
    val serverStarted: Long = 0,
    val update: SystemUpdateInfo? = null,
    val peers: List<PeerEntry> = emptyList(),
    val network: NetworkInfo? = null,
    val counts: ServerCounts? = null,
    val error: MochiError? = null,
    val installError: MochiError? = null,
)

@HiltViewModel
class SystemStatusViewModel @Inject constructor(
    private val api: SystemStatusApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemStatusUiState())
    val uiState: StateFlow<SystemStatusUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val settingsData = api.listSettings().bodyOrThrow()
                val version = settingsData.settings.firstOrNull { it.name == "server_version" }?.value.orEmpty()
                val started = settingsData.settings.firstOrNull { it.name == "server_started" }?.value
                    ?.toLongOrNull() ?: 0L
                // Update is optional — failures here shouldn't blank the page.
                val update = try {
                    api.getUpdate().bodyOrNull()
                } catch (_: Exception) {
                    null
                }
                // Peers / network / counts are optional for the same reason
                // (older servers don't expose the endpoint).
                val peersData = try {
                    api.getPeers().bodyOrNull()
                } catch (_: Exception) {
                    null
                }
                _uiState.value = SystemStatusUiState(
                    isLoading = false,
                    serverVersion = version,
                    serverStarted = started,
                    update = update,
                    peers = (peersData?.peers ?: emptyList())
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.peer }),
                    network = peersData?.network,
                    counts = peersData?.counts,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun installUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInstalling = true, installError = null)
            try {
                api.installUpdate().bodyOrThrow()
                // Re-fetch update info; pending field will surface "Installing X…".
                val update = try {
                    api.getUpdate().bodyOrNull()
                } catch (_: Exception) {
                    _uiState.value.update
                }
                _uiState.value = _uiState.value.copy(isInstalling = false, update = update)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isInstalling = false, installError = e.toMochiError())
            }
        }
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }

    private fun <T> Response<T>.bodyOrNull(): T? = if (isSuccessful) body() else null
}
