package org.mochios.wikis.ui.settings

import androidx.lifecycle.SavedStateHandle
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
import org.mochios.wikis.R
import org.mochios.wikis.model.Replica
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

data class ReplicasTabUiState(
    val isLoading: Boolean = true,
    val replicas: List<Replica> = emptyList(),
    val error: MochiError? = null,
    val isRemoving: Boolean = false,
)

data class ReplicasTabSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
)

/**
 * View model for [ReplicasTab]. Loads the list of remote replicas that have
 * subscribed to this wiki and exposes a "remove" mutation; the web tab uses
 * the same `replicas` / `replica/remove` endpoints.
 */
@HiltViewModel
class ReplicasTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    private val _uiState = MutableStateFlow(ReplicasTabUiState())
    val uiState: StateFlow<ReplicasTabUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<ReplicasTabSnackbar>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<ReplicasTabSnackbar> = _snackbar.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    replicas = repository.getReplicas(wikiId),
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun remove(replicaId: String) {
        _uiState.value = _uiState.value.copy(isRemoving = true)
        viewModelScope.launch {
            try {
                repository.removeReplica(wikiId, replicaId)
                _uiState.value = _uiState.value.copy(isRemoving = false)
                _snackbar.emit(ReplicasTabSnackbar(R.string.wikis_replicas_removed_success))
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRemoving = false,
                    error = e.toMochiError(),
                )
                _snackbar.emit(ReplicasTabSnackbar(R.string.wikis_replicas_remove_failed))
            }
        }
    }
}
