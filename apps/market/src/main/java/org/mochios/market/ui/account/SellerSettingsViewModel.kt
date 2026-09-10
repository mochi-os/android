// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.account

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.market.R
import org.mochios.market.model.Account
import org.mochios.market.model.AccountFees
import org.mochios.market.model.StripeStatus
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class SellerSettingsUiState(
    val account: Account? = null,
    val fees: AccountFees? = null,
    val stripeStatus: StripeStatus? = null,
    val isLoading: Boolean = true,
    val activating: Boolean = false,
    val connecting: Boolean = false,
    val checking: Boolean = false,
    val error: MochiError? = null,
) {
    val isSeller: Boolean get() = account?.seller == 1
    val isOnboarded: Boolean get() = account?.onboarded == 1
    val isSellerReady: Boolean get() = isSeller && isOnboarded
    val stripeLinked: Boolean get() = account?.stripe?.isNotBlank() == true
}

/**
 * One-shot events for the seller-settings snackbar host. Errors resolve via
 * [MochiError.userMessage].
 */
sealed interface SellerSettingsEvent {
    data class Error(val error: MochiError) : SellerSettingsEvent
    /** A completed action, named by resource so it is translated. */
    data class Notice(@StringRes val message: Int) : SellerSettingsEvent
}

@HiltViewModel
class SellerSettingsViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SellerSettingsUiState())
    val state: StateFlow<SellerSettingsUiState> = _state.asStateFlow()

    private val _events = Channel<SellerSettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load(initial = true)
        loadFees()
    }

    /**
     * Re-runs the initial load for the error state's retry button.
     */
    fun retry() {
        load(initial = true)
        loadFees()
    }

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            if (initial) _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val account = repo.getAccount()
                _state.value = _state.value.copy(account = account, isLoading = false)
                if (account.onboarded == 1) {
                    refreshStripe()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.toMochiError())
                if (!initial) _events.send(SellerSettingsEvent.Error(e.toMochiError()))
            }
        }
    }

    private fun loadFees() {
        viewModelScope.launch {
            try {
                val fees = repo.getFees()
                _state.value = _state.value.copy(fees = fees)
            } catch (_: Exception) {
                // Fees are non-critical; the page falls back to the loading label.
            }
        }
    }

    private fun refreshStripe() {
        viewModelScope.launch {
            try {
                val status = repo.stripeStatus()
                _state.value = _state.value.copy(stripeStatus = status)
            } catch (_: Exception) {
                // Leave the prior status in place; the summary degrades gracefully.
            }
        }
    }

    fun activate() {
        if (_state.value.activating) return
        viewModelScope.launch {
            _state.value = _state.value.copy(activating = true)
            try {
                val account = repo.activateAccount()
                _state.value = _state.value.copy(account = account, activating = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(activating = false)
                _events.send(SellerSettingsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun connectStripe(returnUrl: String, onUrl: (String) -> Unit) {
        if (_state.value.connecting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true)
            try {
                val resp = repo.stripeOnboarding(returnUrl)
                _state.value = _state.value.copy(connecting = false)
                if (resp.url.isNotBlank()) {
                    onUrl(resp.url)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(connecting = false)
                _events.send(SellerSettingsEvent.Error(e.toMochiError()))
            }
        }
    }

    /**
     * Finish the Stripe Connect ceremony the browser handed back through
     * `mochi://market/stripe/oauth`. The Comptroller answers a refused or
     * replayed state with an error, which reaches the snackbar as usual; a
     * completed exchange re-reads the account so the linked state shows.
     */
    fun completeStripeOauth(oauth: StripeOauthReturn) {
        if (_state.value.connecting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true)
            try {
                val result = repo.completeStripeOauth(
                    code = oauth.code,
                    state = oauth.state,
                    error = oauth.error,
                    errorDescription = oauth.errorDescription,
                )
                _state.value = _state.value.copy(connecting = false)
                if (result.connected) {
                    _events.send(SellerSettingsEvent.Notice(R.string.market_account_stripe_connected))
                    checkStatus()
                } else {
                    _events.send(
                        SellerSettingsEvent.Error(MochiError.Local(R.string.market_account_stripe_connect_failed)),
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(connecting = false)
                _events.send(SellerSettingsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun checkStatus() {
        if (_state.value.checking) return
        viewModelScope.launch {
            _state.value = _state.value.copy(checking = true)
            try {
                val status = repo.stripeStatus()
                val account = repo.getAccount()
                _state.value = _state.value.copy(
                    stripeStatus = status,
                    account = account,
                    checking = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(checking = false)
                _events.send(SellerSettingsEvent.Error(e.toMochiError()))
            }
        }
    }
}

/**
 * Stripe's raw OAuth return, as MainActivity hands it over from
 * `mochi://market/stripe/oauth`; absent when the screen opened normally.
 */
data class StripeOauthReturn(
    val code: String?,
    val state: String?,
    val error: String?,
    val errorDescription: String?,
) {
    companion object {
        fun from(arguments: Bundle?): StripeOauthReturn? {
            val state = arguments?.getString("state")?.takeIf { it.isNotBlank() } ?: return null
            return StripeOauthReturn(
                code = arguments.getString("code"),
                state = state,
                error = arguments.getString("error"),
                errorDescription = arguments.getString("error_description"),
            )
        }
    }
}
