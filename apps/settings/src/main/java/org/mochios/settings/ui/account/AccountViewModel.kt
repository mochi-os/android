package org.mochios.settings.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.settings.api.AccountApi
import javax.inject.Inject

data class Identity(
    val entity: String = "",
    val fingerprint: String = "",
    val username: String = "",
    val name: String = "",
    val privacy: String = "private",
)

data class AccountUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val identity: Identity = Identity(),
    val nameDraft: String = "",
)

/**
 * The Account screen is identity-only (name, username, fingerprint, directory
 * visibility). Login factors — methods, passkeys, authenticator, recovery
 * codes, third-party login — live on the separate Login screen
 * ([org.mochios.settings.ui.login.LoginScreen]), mirroring the web split.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val api: AccountApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = api.getIdentity().bodyOrThrow()
                val identity = Identity(
                    entity = body.entity,
                    fingerprint = body.fingerprint,
                    username = body.username,
                    name = body.name,
                    privacy = body.privacy,
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    identity = identity,
                    nameDraft = identity.name,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun updateName(draft: String) {
        _uiState.value = _uiState.value.copy(nameDraft = draft)
    }

    fun saveName() {
        val name = _uiState.value.nameDraft.trim()
        if (name.isBlank() || name == _uiState.value.identity.name) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                api.updateIdentity(name = name).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    identity = _uiState.value.identity.copy(name = name),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    fun setPrivacy(privacy: String) {
        if (privacy != "public" && privacy != "private") return
        if (privacy == _uiState.value.identity.privacy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                api.updateIdentity(privacy = privacy).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    identity = _uiState.value.identity.copy(privacy = privacy),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    private fun <T> retrofit2.Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }
}
