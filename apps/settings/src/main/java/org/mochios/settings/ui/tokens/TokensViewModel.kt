// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.tokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.api.ApiToken
import org.mochios.settings.api.TokensApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController
import retrofit2.Response
import javax.inject.Inject

data class TokensUiState(
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    val tokens: List<ApiToken> = emptyList(),
)

@HiltViewModel
class TokensViewModel @Inject constructor(
    private val api: TokensApi,
    stepUpClient: SettingsStepUpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TokensUiState())
    val uiState: StateFlow<TokensUiState> = _uiState.asStateFlow()

    /** Step-up gate: creating an API token re-verifies the user's login
     *  factor(s), since the token is a long-lived bearer credential. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.value = _uiState.value.copy(error = e.toMochiError()) },
    )

    /** Show-once: the freshly-minted plaintext token. The server only
     *  returns it once; the UI displays + lets the user copy, then clears. */
    private val _newApiToken = MutableStateFlow<String?>(null)
    val newApiToken: StateFlow<String?> = _newApiToken.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tokens = api.listTokens().bodyOrThrow().tokens
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = TokensUiState(isLoading = false, tokens = tokens)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun create(name: String) = stepUp.request { proof ->
        try {
            val token = api.createToken(proof, name).bodyOrThrow().data.token
            _newApiToken.value = token
            refresh()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.toMochiError())
        }
    }

    fun acknowledgeNewToken() {
        _newApiToken.value = null
    }

    fun delete(hash: String) {
        viewModelScope.launch {
            try {
                api.deleteToken(hash).bodyOrThrow()
                refresh()
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
