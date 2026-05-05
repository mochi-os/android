package org.mochi.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochi.android.R
import org.mochi.android.api.MochiError
import org.mochi.android.api.toMochiError
import org.mochi.android.auth.AuthRepository
import org.mochi.android.i18n.AppContext
import org.mochi.android.auth.AuthResult
import org.mochi.android.auth.BeginResult
import org.mochi.android.auth.PasskeyManager
import org.mochi.android.auth.SessionManager
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val serverUrl: String = "https://mochi-os.org",
    val email: String = "",
    val beginResult: BeginResult? = null,
    val codeSent: Boolean = false,
    val code: String = "",
    val totpCode: String = "",
    val mfaPartial: String? = null,
    val mfaRemaining: List<String> = emptyList(),
    val mfaEmailCode: String = "",
    val mfaTotpCode: String = "",
    val identityName: String = "",
    val recoveryCode: String = "",
    val showRecovery: Boolean = false,
    val authComplete: Boolean = false,
    val needsIdentity: Boolean = false,
    val serverValidated: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val passkeyManager: PasskeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.serverUrl.collect { url ->
                _uiState.value = _uiState.value.copy(serverUrl = url)
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun updateCode(code: String) {
        _uiState.value = _uiState.value.copy(code = code, error = null)
    }

    fun updateTotpCode(code: String) {
        _uiState.value = _uiState.value.copy(totpCode = code, error = null)
    }

    fun updateMfaEmailCode(code: String) {
        _uiState.value = _uiState.value.copy(mfaEmailCode = code, error = null)
    }

    fun updateMfaTotpCode(code: String) {
        _uiState.value = _uiState.value.copy(mfaTotpCode = code, error = null)
    }

    fun updateIdentityName(name: String) {
        _uiState.value = _uiState.value.copy(identityName = name, error = null)
    }

    fun updateRecoveryCode(code: String) {
        _uiState.value = _uiState.value.copy(recoveryCode = code, error = null)
    }

    fun toggleRecovery() {
        _uiState.value = _uiState.value.copy(
            showRecovery = !_uiState.value.showRecovery,
            error = null
        )
    }

    fun validateServer() {
        val url = _uiState.value.serverUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = MochiError.Unknown(AppContext.get().getString(R.string.error_enter_server_url))
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                sessionManager.setServerUrl(url)
                authRepository.getAvailableMethods()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    serverValidated = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun beginLogin() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = MochiError.Unknown(AppContext.get().getString(R.string.error_enter_email))
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = authRepository.beginLogin(email)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    beginResult = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun requestEmailCode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                authRepository.requestCode(_uiState.value.email.trim())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    codeSent = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun verifyCode() {
        val code = _uiState.value.code.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                handleAuthResult(authRepository.verifyCode(code))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun verifyTotp() {
        val code = _uiState.value.totpCode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                handleAuthResult(
                    authRepository.verifyTotp(_uiState.value.email.trim(), code)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun completeMfa() {
        val partial = _uiState.value.mfaPartial ?: return
        val emailCode = _uiState.value.mfaEmailCode.trim().ifBlank { null }
        val totpCode = _uiState.value.mfaTotpCode.trim().ifBlank { null }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                handleAuthResult(authRepository.completeMfa(partial, emailCode, totpCode))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun beginPasskeyAuth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val challenge = authRepository.beginPasskey()
                val credential = passkeyManager.authenticate(challenge.options)
                handleAuthResult(
                    authRepository.finishPasskey(
                        ceremony = challenge.ceremony,
                        id = credential.id,
                        rawId = credential.rawId,
                        type = credential.type,
                        clientDataJSON = credential.clientDataJSON,
                        authenticatorData = credential.authenticatorData,
                        signature = credential.signature
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun verifyRecoveryCode() {
        val code = _uiState.value.recoveryCode.trim()
        if (code.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                handleAuthResult(
                    authRepository.verifyRecoveryCode(_uiState.value.email.trim(), code)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun createIdentity() {
        val name = _uiState.value.identityName.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = MochiError.Unknown(AppContext.get().getString(R.string.error_enter_name))
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                authRepository.createIdentity(name)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    authComplete = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    private fun handleAuthResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    authComplete = true
                )
            }
            is AuthResult.NeedsIdentity -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    needsIdentity = true
                )
            }
            is AuthResult.NeedsMfa -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    mfaPartial = result.partial,
                    mfaRemaining = result.remaining
                )
            }
        }
    }
}
