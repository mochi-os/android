package org.mochios.wikis.ui.redirects

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
import org.mochios.wikis.model.Redirect
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [RedirectsScreen]. Holds the redirects list and the in-flight
 * loading / error state. Mutations (set / delete) update the list optimistically
 * by reloading from the server once the call returns, matching how the web
 * page invalidates its React-Query cache.
 */
data class RedirectsUiState(
    val isLoading: Boolean = true,
    val redirects: List<Redirect> = emptyList(),
    val error: MochiError? = null,
)

/**
 * Snackbar message dispatched by [RedirectsViewModel]. Carries a string-resource
 * id (and optional positional args) so the composable can resolve the localised
 * text at render time via [stringResource].
 */
data class RedirectsSnackbar(
    val messageRes: Int,
    val args: List<Any> = emptyList(),
)

/**
 * ViewModel for [RedirectsScreen]. Reads `wikiId` from [SavedStateHandle] (set
 * by `WikisApp.REDIRECTS`) and exposes:
 *
 *  - [uiState] — the redirects list + loading/error
 *  - [snackbar] — one-shot success/failure messages routed to the screen's
 *    [SnackbarHostState]
 *
 * Mutations always reload the list from the server on success so a concurrent
 * change from another device or replica is reflected, rather than holding a
 * stale local edit.
 */
@HiltViewModel
class RedirectsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    private val _uiState = MutableStateFlow(RedirectsUiState())
    val uiState: StateFlow<RedirectsUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<RedirectsSnackbar>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<RedirectsSnackbar> = _snackbar.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val list = repository.getRedirects(wikiId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    redirects = list,
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

    fun create(source: String, target: String) {
        val s = source.trim()
        val t = target.trim()
        if (s.isEmpty() || t.isEmpty()) {
            viewModelScope.launch {
                _snackbar.emit(RedirectsSnackbar(R.string.wikis_redirect_required_fields))
            }
            return
        }
        viewModelScope.launch {
            try {
                repository.setRedirect(wikiId, s, t)
                _snackbar.emit(RedirectsSnackbar(R.string.wikis_redirect_created_success))
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
                _snackbar.emit(RedirectsSnackbar(R.string.wikis_redirect_create_failed))
            }
        }
    }

    fun delete(source: String) {
        viewModelScope.launch {
            try {
                repository.deleteRedirect(wikiId, source)
                _snackbar.emit(
                    RedirectsSnackbar(
                        R.string.wikis_redirect_deleted_success,
                        listOf(source),
                    )
                )
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
                _snackbar.emit(RedirectsSnackbar(R.string.wikis_redirect_delete_failed))
            }
        }
    }
}
