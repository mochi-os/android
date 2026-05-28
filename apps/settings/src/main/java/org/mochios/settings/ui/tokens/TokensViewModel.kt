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
) : ViewModel() {

    private val _uiState = MutableStateFlow(TokensUiState())
    val uiState: StateFlow<TokensUiState> = _uiState.asStateFlow()

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

    fun create(name: String) {
        viewModelScope.launch {
            try {
                val token = api.createToken(name).bodyOrThrow().token
                _newApiToken.value = token
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
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
