package org.mochios.settings.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.OAuthPkce
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.api.ApiToken
import org.mochios.settings.api.OAuthIdentity
import org.mochios.settings.api.Passkey
import org.mochios.settings.api.SecurityApi
import org.mochios.settings.api.Session
import org.mochios.settings.api.TotpSetupResponse
import javax.inject.Inject

data class SecurityUiState(
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    /** Sorted accessed-desc. */
    val sessions: List<Session> = emptyList(),
    val passkeys: List<Passkey> = emptyList(),
    val totpEnabled: Boolean = false,
    /** Available factors as advertised by `-/user/account/methods` (current selected set). */
    val enabledMethods: Set<String> = emptySet(),
    val recoveryCount: Int = 0,
    val oauth: List<OAuthIdentity> = emptyList(),
    val tokens: List<ApiToken> = emptyList(),
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val api: SecurityApi,
    private val passkeyManager: PasskeyManager,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    /** Show-once data — emitted via separate flows so the UI can render the
     *  one-time display and clear after the user acknowledges. */
    private val _newTotpSetup = MutableStateFlow<TotpSetupResponse?>(null)
    val newTotpSetup: StateFlow<TotpSetupResponse?> = _newTotpSetup.asStateFlow()

    private val _newRecoveryCodes = MutableStateFlow<List<String>?>(null)
    val newRecoveryCodes: StateFlow<List<String>?> = _newRecoveryCodes.asStateFlow()

    private val _newApiToken = MutableStateFlow<String?>(null)
    val newApiToken: StateFlow<String?> = _newApiToken.asStateFlow()

    /** Browser launch URL for an in-progress OAuth-link flow. The screen
     *  observes; consumes via consumeOAuthLaunchUrl() after firing an Intent. */
    private val _oauthLaunchUrl = MutableStateFlow<String?>(null)
    val oauthLaunchUrl: StateFlow<String?> = _oauthLaunchUrl.asStateFlow()

    init {
        refresh()
        // Watch for OAuth-link callbacks the host MainActivity feeds into
        // sessionManager.oauthLinkReturn after parsing
        // `mochi:oauth-link-return?oauth_linked=...` (or `?oauth_error=...`).
        viewModelScope.launch {
            sessionManager.oauthLinkReturn.collect { (provider, error) ->
                if (provider == null && error == null) return@collect
                sessionManager.clearOAuthLinkReturn()
                if (error != null) {
                    _uiState.value = _uiState.value.copy(
                        error = MochiError.Unknown("OAuth link: $error"),
                    )
                }
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val state = _uiState.value
            try {
                val methods = api.getMethods().bodyOrThrow().methods
                val passkeys = api.listPasskeys().bodyOrThrow().passkeys
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                val totp = api.getTotp().bodyOrThrow().enabled
                val recovery = api.recoveryCount().bodyOrThrow().count
                val sessions = api.listSessions().bodyOrThrow().sessions.sortedByDescending { it.accessed }
                val oauth = api.listOAuth().bodyOrThrow().identities
                    .sortedWith(compareBy(NaturalCompare) { it.provider })
                val tokens = api.listTokens().bodyOrThrow().tokens
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = state.copy(
                    isLoading = false,
                    enabledMethods = methods.toSet(),
                    passkeys = passkeys,
                    totpEnabled = totp,
                    recoveryCount = recovery,
                    sessions = sessions,
                    oauth = oauth,
                    tokens = tokens,
                )
            } catch (e: Exception) {
                _uiState.value = state.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun toggleMethod(method: String, enabled: Boolean) {
        viewModelScope.launch {
            val cur = _uiState.value.enabledMethods.toMutableSet()
            if (enabled) cur += method else cur -= method
            if (cur.isEmpty()) return@launch
            try {
                val resp = api.setMethods(cur.joinToString(",")).bodyOrThrow().methods
                _uiState.value = _uiState.value.copy(enabledMethods = resp.toSet())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun registerPasskey(name: String) = mutate {
        val begin = api.beginPasskeyRegister().bodyOrThrow()
        if (begin.ceremony.isBlank()) throw RuntimeException("missing ceremony")
        val credentialJson = passkeyManager.register(begin.options)
        val finishName = name.trim().ifBlank { "Passkey" }
        api.finishPasskeyRegister(begin.ceremony, credentialJson, finishName).bodyOrThrow()
        refresh()
    }

    fun renamePasskey(id: String, name: String) = mutate {
        api.renamePasskey(id, name).bodyOrThrow()
        refresh()
    }

    fun deletePasskey(id: String) = mutate {
        api.deletePasskey(id).bodyOrThrow()
        refresh()
    }

    fun beginTotpSetup() = mutate {
        val setup = api.setupTotp().bodyOrThrow()
        _newTotpSetup.value = setup
    }

    fun verifyTotp(code: String, onSuccess: () -> Unit = {}) = mutate {
        val ok = api.verifyTotp(code).bodyOrThrow().ok
        if (ok) {
            _newTotpSetup.value = null
            _uiState.value = _uiState.value.copy(totpEnabled = true)
            onSuccess()
        } else {
            _uiState.value = _uiState.value.copy(
                error = MochiError.Unknown("Invalid code"),
            )
        }
    }

    fun cancelTotpSetup() {
        _newTotpSetup.value = null
    }

    fun disableTotp() = mutate {
        api.disableTotp().bodyOrThrow()
        _uiState.value = _uiState.value.copy(totpEnabled = false)
    }

    fun generateRecovery() = mutate {
        val codes = api.generateRecovery().bodyOrThrow().codes
        _newRecoveryCodes.value = codes
        _uiState.value = _uiState.value.copy(recoveryCount = codes.size)
    }

    fun acknowledgeRecoveryCodes() {
        _newRecoveryCodes.value = null
    }

    fun revokeSession(id: String) = mutate {
        api.revokeSession(id).bodyOrThrow()
        refresh()
    }

    fun linkOAuth(provider: String) = mutate {
        val token = sessionManager.getToken("settings")
            ?: throw RuntimeException("no settings token to authorise OAuth link")
        val verifier = OAuthPkce.generateVerifier()
        sessionManager.saveOAuthVerifier(verifier)
        val challenge = OAuthPkce.challengeFor(verifier)
        val url = authRepository.beginOAuthLink(
            provider = provider,
            scheme = "mochi",
            target = "mochi:oauth-link-return",
            challenge = challenge,
            bearerToken = token,
        )
        _oauthLaunchUrl.value = url
    }

    fun consumeOAuthLaunchUrl() {
        _oauthLaunchUrl.value = null
    }

    fun unlinkOAuth(provider: String) = mutate {
        api.unlinkOAuth(provider).bodyOrThrow()
        refresh()
    }

    fun createToken(name: String) = mutate {
        val token = api.createToken(name).bodyOrThrow().token
        _newApiToken.value = token
        refresh()
    }

    fun acknowledgeNewToken() {
        _newApiToken.value = null
    }

    fun deleteToken(hash: String) = mutate {
        api.deleteToken(hash).bodyOrThrow()
        refresh()
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun <T> retrofit2.Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        return body() ?: throw RuntimeException("empty body")
    }
}
