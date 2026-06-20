// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

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
import org.mochios.android.auth.StepUpClient
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.MethodInfo
import org.mochios.settings.api.OAuthIdentity
import org.mochios.settings.api.Passkey
import org.mochios.settings.api.TotpSetupResponse
import retrofit2.Response
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    /** Per-method tri-state from `-/user/account/methods`. */
    val methods: Map<String, MethodInfo> = emptyMap(),
    val methodBusy: Boolean = false,
    val passkeys: List<Passkey> = emptyList(),
    val totpEnabled: Boolean = false,
    val recoveryCount: Int = 0,
    val oauth: List<OAuthIdentity> = emptyList(),
    /** OAuth providers the server has configured (enabled), lowercase keys.
     *  Drives which providers are offered for linking — matching web, which
     *  reads the same `/_/auth/methods` oauth map. */
    val oauthProviders: List<String> = emptyList(),
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: AccountApi,
    private val passkeyManager: PasskeyManager,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    stepUp: SettingsStepUpClient,
) : ViewModel() {

    /** Injected into the StepUpDialog rendered by the screen. */
    val stepUpClient: StepUpClient = stepUp

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _newTotpSetup = MutableStateFlow<TotpSetupResponse?>(null)
    val newTotpSetup: StateFlow<TotpSetupResponse?> = _newTotpSetup.asStateFlow()

    private val _newRecoveryCodes = MutableStateFlow<List<String>?>(null)
    val newRecoveryCodes: StateFlow<List<String>?> = _newRecoveryCodes.asStateFlow()

    private val _oauthLaunchUrl = MutableStateFlow<String?>(null)
    val oauthLaunchUrl: StateFlow<String?> = _oauthLaunchUrl.asStateFlow()

    /** True while the step-up dialog is shown for a pending gated action. */
    private val _stepUpVisible = MutableStateFlow(false)
    val stepUpVisible: StateFlow<Boolean> = _stepUpVisible.asStateFlow()

    private var pendingStepUp: (suspend (String) -> Unit)? = null

    init {
        refresh()
        // OAuth-link callbacks the host MainActivity feeds into
        // sessionManager.oauthLinkReturn after parsing the return deep link.
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
            try {
                val methods = api.getMethods().bodyOrThrow().methods
                val passkeys = api.listPasskeys().bodyOrThrow().passkeys
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                val totp = api.getTotp().bodyOrThrow().enabled
                val recovery = api.recoveryCount().bodyOrThrow().count
                val oauth = api.listOAuth().bodyOrThrow().identities
                    .sortedWith(compareBy(NaturalCompare) { it.provider })
                // Server-configured OAuth providers (best-effort: a failure
                // here shouldn't blank the whole security page).
                val oauthProviders = runCatching {
                    authRepository.getAvailableMethods().oauth
                        .filterValues { it }
                        .keys
                        .map { it.lowercase() }
                        .sorted()
                }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    methods = methods,
                    passkeys = passkeys,
                    totpEnabled = totp,
                    recoveryCount = recovery,
                    oauth = oauth,
                    oauthProviders = oauthProviders,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    // ---------- Step-up plumbing ----------

    /** Open the step-up dialog; [run] fires with the proof token once the user
     *  re-verifies their login factor(s). */
    private fun requestStepUp(run: suspend (String) -> Unit) {
        pendingStepUp = run
        _stepUpVisible.value = true
    }

    fun onStepUpVerified(token: String) {
        _stepUpVisible.value = false
        val run = pendingStepUp
        pendingStepUp = null
        if (run != null) {
            viewModelScope.launch {
                try {
                    run(token)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = e.toMochiError())
                }
            }
        }
    }

    fun cancelStepUp() {
        _stepUpVisible.value = false
        pendingStepUp = null
    }

    // ---------- Login methods (tri-state) ----------

    fun setMethodState(method: String, state: String) = requestStepUp { token ->
        _uiState.value = _uiState.value.copy(methodBusy = true)
        try {
            api.configureMethod(token, method, state).bodyOrThrow()
            refresh()
        } finally {
            _uiState.value = _uiState.value.copy(methodBusy = false)
        }
    }

    // ---------- Passkeys ----------

    fun registerPasskey(name: String) = requestStepUp { token ->
        val begin = api.beginPasskeyRegister().bodyOrThrow()
        if (begin.ceremony.isBlank()) throw RuntimeException("missing ceremony")
        val credentialJson = passkeyManager.register(begin.options)
        val finishName = name.trim().ifBlank { "Passkey" }
        api.finishPasskeyRegister(token, begin.ceremony, credentialJson, finishName).bodyOrThrow()
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

    fun beginTotpSetup() = requestStepUp { token ->
        val setup = api.setupTotp(token).bodyOrThrow()
        _newTotpSetup.value = setup
    }

    fun verifyTotp(code: String) = mutate {
        val ok = api.verifyTotp(code).bodyOrThrow().ok == true
        if (ok) {
            _newTotpSetup.value = null
            _uiState.value = _uiState.value.copy(totpEnabled = true)
        } else {
            _uiState.value = _uiState.value.copy(error = MochiError.Unknown("Invalid code"))
        }
    }

    fun cancelTotpSetup() {
        _newTotpSetup.value = null
    }

    fun disableTotp() = requestStepUp { token ->
        api.disableTotp(token).bodyOrThrow()
        _uiState.value = _uiState.value.copy(totpEnabled = false)
    }

    // ---------- Recovery codes ----------

    fun generateRecovery() = requestStepUp { token ->
        val codes = api.generateRecovery(token).bodyOrThrow().codes
        _newRecoveryCodes.value = codes
        _uiState.value = _uiState.value.copy(recoveryCount = codes.size)
    }

    fun acknowledgeRecoveryCodes() {
        _newRecoveryCodes.value = null
    }

    // ---------- OAuth identities (linking is not step-up gated) ----------

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
