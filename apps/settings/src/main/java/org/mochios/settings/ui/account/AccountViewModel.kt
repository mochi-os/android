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
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.OAuthPkce
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.OAuthIdentity
import org.mochios.settings.api.Passkey
import org.mochios.settings.api.TotpSetupResponse
import retrofit2.Response
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
    val passkeys: List<Passkey> = emptyList(),
    val totpEnabled: Boolean = false,
    /** Available factors as advertised by `-/user/account/methods` (current selected set). */
    val enabledMethods: Set<String> = emptySet(),
    val recoveryCount: Int = 0,
    val oauth: List<OAuthIdentity> = emptyList(),
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val api: AccountApi,
    private val passkeyManager: PasskeyManager,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val serverUrl: String = sessionManager.getServerUrlBlocking().trimEnd('/')

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    /** Show-once data — emitted via separate flows so the UI can render the
     *  one-time display and clear after the user acknowledges. */
    private val _newTotpSetup = MutableStateFlow<TotpSetupResponse?>(null)
    val newTotpSetup: StateFlow<TotpSetupResponse?> = _newTotpSetup.asStateFlow()

    private val _newRecoveryCodes = MutableStateFlow<List<String>?>(null)
    val newRecoveryCodes: StateFlow<List<String>?> = _newRecoveryCodes.asStateFlow()

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
                val identityBody = api.getIdentity().bodyOrThrow()
                val identity = Identity(
                    entity = identityBody.entity,
                    fingerprint = identityBody.fingerprint,
                    username = identityBody.username,
                    name = identityBody.name,
                    privacy = identityBody.privacy,
                )
                val methods = api.getMethods().bodyOrThrow().methods
                val passkeys = api.listPasskeys().bodyOrThrow().passkeys
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                val totp = api.getTotp().bodyOrThrow().enabled
                val recovery = api.recoveryCount().bodyOrThrow().count
                val oauth = api.listOAuth().bodyOrThrow().identities
                    .sortedWith(compareBy(NaturalCompare) { it.provider })
                _uiState.value = state.copy(
                    isLoading = false,
                    identity = identity,
                    nameDraft = identity.name,
                    enabledMethods = methods.toSet(),
                    passkeys = passkeys,
                    totpEnabled = totp,
                    recoveryCount = recovery,
                    oauth = oauth,
                )
            } catch (e: Exception) {
                _uiState.value = state.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    // ---------- Identity ----------

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

    // ---------- Login methods ----------

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

    // ---------- Passkeys ----------

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

    // ---------- TOTP ----------

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
            _uiState.value = _uiState.value.copy(error = MochiError.Unknown("Invalid code"))
        }
    }

    fun cancelTotpSetup() {
        _newTotpSetup.value = null
    }

    fun disableTotp() = mutate {
        api.disableTotp().bodyOrThrow()
        _uiState.value = _uiState.value.copy(totpEnabled = false)
    }

    // ---------- Recovery codes ----------

    fun generateRecovery() = mutate {
        val codes = api.generateRecovery().bodyOrThrow().codes
        _newRecoveryCodes.value = codes
        _uiState.value = _uiState.value.copy(recoveryCount = codes.size)
    }

    fun acknowledgeRecoveryCodes() {
        _newRecoveryCodes.value = null
    }

    // ---------- OAuth identities ----------

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

    // ---------- Helpers ----------

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
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
