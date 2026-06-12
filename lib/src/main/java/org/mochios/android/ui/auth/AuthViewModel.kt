package org.mochios.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.AuthRepository
import org.mochios.android.i18n.AppContext
import org.mochios.android.auth.AuthResult
import org.mochios.android.auth.BeginResult
import org.mochios.android.auth.MethodsResponse
import org.mochios.android.auth.OAuthPkce
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
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
    val identityPrivacy: String = "public",
    val recoveryCode: String = "",
    val authComplete: Boolean = false,
    val needsIdentity: Boolean = false,
    val serverValidated: Boolean = false,
    val methods: MethodsResponse? = null,
    val oauthLaunchUrl: String? = null
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
        viewModelScope.launch {
            sessionManager.oauthReturn.collect { (code, error) ->
                if (error != null) {
                    sessionManager.clearOAuthReturn()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = MochiError.Unknown("OAuth: $error")
                    )
                } else if (code != null) {
                    sessionManager.clearOAuthReturn()
                    completeOAuth(code)
                }
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

    fun updateIdentityPrivacy(privacy: String) {
        if (privacy != "public" && privacy != "private") return
        _uiState.value = _uiState.value.copy(identityPrivacy = privacy)
    }

    /** Abandon the in-progress identity-creation and drop the user back to
     *  the email entry step. Mirrors web's "Use a different account" link. */
    fun abandonIdentity() {
        _uiState.value = AuthUiState(serverUrl = _uiState.value.serverUrl)
    }

    fun updateRecoveryCode(code: String) {
        _uiState.value = _uiState.value.copy(recoveryCode = code, error = null)
    }

    /**
     * Clear all per-user login input so the flow restarts at the email step.
     *
     * The AuthViewModel is activity-scoped and outlives the AuthNavigation
     * composable, so after a logout it still holds the previous user's email
     * and [BeginResult]. AuthNavigation calls this on (re)entry to wipe that
     * stale state. Server-level config ([AuthUiState.serverValidated],
     * [AuthUiState.methods]) is preserved so the user isn't bounced back
     * through server setup.
     */
    fun resetForLogin() {
        _uiState.value = _uiState.value.copy(
            email = "",
            beginResult = null,
            codeSent = false,
            code = "",
            totpCode = "",
            mfaPartial = null,
            mfaRemaining = emptyList(),
            mfaEmailCode = "",
            mfaTotpCode = "",
            identityName = "",
            identityPrivacy = "public",
            recoveryCode = "",
            needsIdentity = false,
            error = null
        )
    }

    /** Return from the per-account method step to email entry, keeping the
     *  typed email and the server method config but clearing any in-progress
     *  code/recovery input. Mirrors the mockup's "Back" affordance. */
    fun backToEmailEntry() {
        _uiState.value = _uiState.value.copy(
            beginResult = null,
            codeSent = false,
            code = "",
            totpCode = "",
            recoveryCode = "",
            error = null
        )
    }

    /** Clear the recovery-code input when leaving the recovery step. */
    fun clearRecovery() {
        _uiState.value = _uiState.value.copy(recoveryCode = "", error = null)
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
                val methods = authRepository.getAvailableMethods()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    serverValidated = true,
                    methods = methods
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
                // When email code is the only allowed factor there's no choice
                // to make, so send the code immediately and drop the user
                // straight onto the code input.
                if (result.allowed.singleOrNull() == "email") {
                    requestEmailCode()
                }
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

        val privacy = _uiState.value.identityPrivacy
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                authRepository.createIdentity(name, privacy)
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

    fun startOAuth(provider: String, scheme: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val verifier = OAuthPkce.generateVerifier()
                sessionManager.saveOAuthVerifier(verifier)
                val challenge = OAuthPkce.challengeFor(verifier)
                val url = authRepository.beginOAuth(provider, scheme, challenge)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    oauthLaunchUrl = url
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    fun consumeOAuthLaunchUrl() {
        _uiState.value = _uiState.value.copy(oauthLaunchUrl = null)
    }

    /**
     * Reset transient one-shot signals that should not survive an
     * AuthNavigation re-mount. Critical for logout + later re-entry: the
     * AuthViewModel is scoped to the activity and outlives the AuthNavigation
     * composable, so without consuming `authComplete` the next mount
     * immediately calls onAuthenticated again and re-bootstraps the previous
     * session — making the new logout appear to do nothing because the
     * NeedsLogin → Bootstrapping → Ready cycle fires the moment AuthNavigation
     * comes back on screen.
     */
    fun consumeAuthComplete() {
        _uiState.value = _uiState.value.copy(authComplete = false)
    }

    private fun completeOAuth(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val verifier = sessionManager.consumeOAuthVerifier()
                if (verifier == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = MochiError.Unknown("OAuth: missing verifier")
                    )
                    return@launch
                }
                handleAuthResult(authRepository.exchangeOAuth(code, verifier))
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
