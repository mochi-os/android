package org.mochios.settings.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.i18n.PreferencesManager
import javax.inject.Inject

data class UserSettingsUiState(
    val isLoading: Boolean = true,
    /** Raw key → value map mirroring what the server stores. Blank == use default. */
    val values: Map<String, String> = emptyMap(),
    val error: MochiError? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class UserSettingsViewModel @Inject constructor(
    private val preferences: PreferencesManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSettingsUiState())
    val uiState: StateFlow<UserSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                preferences.refresh()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    values = preferences.rawPreferences(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun set(key: String, value: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                preferences.setPreference(key, value)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    values = preferences.rawPreferences(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }
}
