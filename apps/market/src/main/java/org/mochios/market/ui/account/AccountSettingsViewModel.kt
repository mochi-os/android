// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.model.PlaceData
import org.mochios.market.R
import org.mochios.market.lib.ParsedLocation
import org.mochios.market.lib.parseLocation
import org.mochios.market.model.Account
import org.mochios.market.model.AccountFees
import org.mochios.market.model.StripeStatus
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

data class AccountSettingsUiState(
    val account: Account? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val biographyDraft: String = "",
    val placeDraft: PlaceData? = null,
    val businessDraft: Boolean = false,
    val companyDraft: String = "",
    val vatDraft: String = "",
    val addressNameDraft: String = "",
    val addressLine1Draft: String = "",
    val addressLine2Draft: String = "",
    val addressCityDraft: String = "",
    val addressRegionDraft: String = "",
    val addressPostcodeDraft: String = "",
    val addressCountryDraft: String = "",
    val stripeStatus: StripeStatus? = null,
    val stripeStatusLoading: Boolean = false,
    val stripeConnecting: Boolean = false,
    val fees: AccountFees? = null,
    val error: MochiError? = null,
)

sealed interface AccountSettingsEvent {
    object Saved : AccountSettingsEvent
    data class Error(val error: MochiError) : AccountSettingsEvent
}

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val repo: MarketRepository,
) : ViewModel() {

    private val gson = Gson()

    private val _state = MutableStateFlow(AccountSettingsUiState())
    val state: StateFlow<AccountSettingsUiState> = _state.asStateFlow()

    private val _events = Channel<AccountSettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadAccount()
    }

    /** Re-runs the initial load for the error state's retry button. */
    fun retry() {
        loadAccount()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val account = repo.getAccount()
                _state.value = _state.value.withDrafts(account).copy(isLoading = false)
                if (account.onboarded == 1) {
                    refreshStripe()
                }
                loadFees()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Platform fee % for the "Become a seller" disclosure. Best-effort. */
    private fun loadFees() {
        viewModelScope.launch {
            val fees = runCatching { repo.getFees() }.getOrNull() ?: return@launch
            _state.value = _state.value.copy(fees = fees)
        }
    }

    fun updateBiography(value: String) {
        _state.value = _state.value.copy(
            biographyDraft = value.take(BIOGRAPHY_LIMIT),
        )
    }

    fun updatePlace(value: PlaceData) {
        _state.value = _state.value.copy(placeDraft = value)
    }

    fun updateBusiness(value: Boolean) {
        _state.value = _state.value.copy(businessDraft = value)
    }

    fun updateCompany(value: String) {
        _state.value = _state.value.copy(companyDraft = value)
    }

    fun updateVat(value: String) {
        _state.value = _state.value.copy(vatDraft = value)
    }

    fun updateAddressName(value: String) {
        _state.value = _state.value.copy(addressNameDraft = value)
    }

    fun updateAddressLine1(value: String) {
        _state.value = _state.value.copy(addressLine1Draft = value)
    }

    fun updateAddressLine2(value: String) {
        _state.value = _state.value.copy(addressLine2Draft = value)
    }

    fun updateAddressCity(value: String) {
        _state.value = _state.value.copy(addressCityDraft = value)
    }

    fun updateAddressRegion(value: String) {
        _state.value = _state.value.copy(addressRegionDraft = value)
    }

    fun updateAddressPostcode(value: String) {
        _state.value = _state.value.copy(addressPostcodeDraft = value)
    }

    fun updateAddressCountry(value: String) {
        _state.value = _state.value.copy(addressCountryDraft = value)
    }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        viewModelScope.launch {
            _state.value = current.copy(isSaving = true)
            try {
                val locationJson = current.placeDraft?.let { gson.toJson(it) }
                val account = repo.updateAccount(
                    mapOf(
                        "biography" to current.biographyDraft,
                        "location" to locationJson,
                        "business" to if (current.businessDraft) "1" else "0",
                        "company" to current.companyDraft,
                        "vat" to current.vatDraft,
                        "address_name" to current.addressNameDraft,
                        "address_line1" to current.addressLine1Draft,
                        "address_line2" to current.addressLine2Draft,
                        "address_city" to current.addressCityDraft,
                        "address_region" to current.addressRegionDraft,
                        "address_postcode" to current.addressPostcodeDraft,
                        "address_country" to current.addressCountryDraft,
                    ),
                )
                _state.value = _state.value.withDrafts(account).copy(isSaving = false)
                _events.send(AccountSettingsEvent.Saved)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false)
                _events.send(AccountSettingsEvent.Error(e.toMochiError()))
            }
        }
    }

    /**
     * Stripe onboarding requires an activated seller account, so activate first
     * when the caller is not one yet.
     */
    fun connectStripe(returnUrl: String, onUrl: (String) -> Unit) {
        if (_state.value.stripeConnecting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(stripeConnecting = true)
            try {
                var account = _state.value.account
                if (account == null || account.seller != 1) {
                    account = repo.activateAccount(returnUrl)
                    _state.value = _state.value.copy(account = account)
                }
                val resp = repo.stripeOnboarding(returnUrl)
                if (resp.url.isNotBlank()) {
                    onUrl(resp.url)
                } else {
                    _events.send(
                        AccountSettingsEvent.Error(MochiError.Local(R.string.market_account_stripe_open_failed)),
                    )
                }
            } catch (e: Exception) {
                _events.send(AccountSettingsEvent.Error(e.toMochiError()))
            } finally {
                _state.value = _state.value.copy(stripeConnecting = false)
            }
        }
    }

    /**
     * Reloads the account row as well as the Stripe flags, unlike
     * [refreshStripe].
     */
    fun checkStripeStatus() {
        if (_state.value.stripeStatusLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(stripeStatusLoading = true)
            try {
                val account = repo.getAccount()
                val status = runCatching { repo.stripeStatus() }.getOrNull()
                _state.value = _state.value.copy(
                    account = account,
                    stripeStatus = status ?: _state.value.stripeStatus,
                    stripeStatusLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(stripeStatusLoading = false)
                _events.send(AccountSettingsEvent.Error(e.toMochiError()))
            }
        }
    }

    fun refreshStripe() {
        viewModelScope.launch {
            _state.value = _state.value.copy(stripeStatusLoading = true)
            try {
                val status = repo.stripeStatus()
                _state.value = _state.value.copy(
                    stripeStatus = status,
                    stripeStatusLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(stripeStatusLoading = false)
                _events.send(AccountSettingsEvent.Error(e.toMochiError()))
            }
        }
    }

    private fun AccountSettingsUiState.withDrafts(account: Account): AccountSettingsUiState =
        copy(
            account = account,
            biographyDraft = account.biography,
            placeDraft = parseLocationToPlace(account.location),
            businessDraft = account.business == 1,
            companyDraft = account.company,
            vatDraft = account.vat,
            addressNameDraft = account.addressName,
            addressLine1Draft = account.addressLine1,
            addressLine2Draft = account.addressLine2,
            addressCityDraft = account.addressCity,
            addressRegionDraft = account.addressRegion,
            addressPostcodeDraft = account.addressPostcode,
            addressCountryDraft = account.addressCountry,
        )

    private fun parseLocationToPlace(json: String): PlaceData? {
        val parsed: ParsedLocation = parseLocation(json) ?: return null
        return PlaceData(
            name = parsed.name,
            lat = parsed.lat,
            lon = parsed.lon,
            country = parsed.country,
            state = parsed.region,
            category = parsed.category,
        )
    }

    companion object {
        const val BIOGRAPHY_LIMIT = 500
    }
}
