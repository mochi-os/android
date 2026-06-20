// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.account

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
import org.mochios.market.model.Account
import org.mochios.market.model.AccountFees
import org.mochios.market.model.StripeStatus
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * UI state for [SellerSettingsScreen]. Mirrors web's
 * `apps/market/web/src/features/account/SellerSettingsPage`. Holds the
 * caller's [Account], the platform [AccountFees], and (once onboarded) the
 * Stripe Connect capability flags so the page can render the seller-status
 * summary and drive the two-step activate → connect flow.
 */
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
}

/**
 * ViewModel for the market Seller Settings screen. Loads the caller's
 * [Account] plus the platform fee, and — when onboarded — the Stripe Connect
 * status. Drives [activate] (create seller profile), [connectStripe] (start
 * onboarding, returning the redirect URL for a Custom Tab), and
 * [checkStatus] (re-fetch Stripe capabilities and reload the account).
 */
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
                _state.value = _state.value.copy(fees = repo.getFees())
            } catch (_: Exception) {
                // Fees are non-critical; the page falls back to the loading label.
            }
        }
    }

    private fun refreshStripe() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(stripeStatus = repo.stripeStatus())
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

    /**
     * Start Stripe Connect onboarding and hand the redirect URL back to the
     * screen via [onUrl] so it can open a Custom Tab (the iframe shell sandbox
     * can't host Stripe). Failures surface on the snackbar.
     */
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
