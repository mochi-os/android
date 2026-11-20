// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.checkout

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.market.R
import org.mochios.market.lib.cheapestFor
import org.mochios.market.lib.currencyDecimals
import org.mochios.market.lib.optionsFor
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Account
import org.mochios.market.model.Auction
import org.mochios.market.model.AuctionStatus
import org.mochios.market.model.Currency
import org.mochios.market.model.DeliveryMethod
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingType
import org.mochios.market.model.PricingModel
import org.mochios.market.model.ShippingOption
import org.mochios.market.model.ShippingRegions
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * Money is in minor units; [amountText] is free text converted on submit.
 * [addressCountry] is a country code, or a country written before codes as it
 * was stored. [addressTouched] is set once the buyer edits the address, after
 * which a required field left empty is marked.
 */
data class CheckoutUiState(
    val isLoading: Boolean = true,
    val listing: Listing? = null,
    val shipping: List<ShippingOption> = emptyList(),
    val auction: Auction? = null,
    val regions: ShippingRegions? = null,
    val delivery: DeliveryMethod? = null,
    val optionId: String? = null,
    val amountText: String = "",
    val addressName: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val addressCity: String = "",
    val addressRegion: String = "",
    val addressPostcode: String = "",
    val addressCountry: String = "",
    val addressTouched: Boolean = false,
    val submitting: Boolean = false,
    val error: MochiError? = null,
) {
    /** An auction listing, which is bought only by completing the won auction. */
    val isAuction: Boolean get() = listing?.pricing == PricingModel.AUCTION

    /**
     * Whether the caller may complete this auction: it has ended with a sale,
     * and the caller is its winner. The listing detail says both, so no list
     * of the caller's bids is consulted.
     */
    val wonAuction: Boolean
        get() = isAuction && auction?.let { held -> held.status == AuctionStatus.ENDED_SOLD && held.mine == true } == true

    /** The shipping options a buyer in [addressCountry] may choose. */
    val options: List<ShippingOption> get() = optionsFor(addressCountry, shipping, regions)

    /** The option postage is charged for: only while delivery is by shipping. */
    val selectedOption: ShippingOption?
        get() = if (delivery == DeliveryMethod.SHIPPING) options.firstOrNull { it.id == optionId } else null

    private val currency: Currency get() = listing?.currency ?: Currency.GBP

    /** The item's price: a won auction's winning bid, or the amount a pay-what-you-want buyer offers. */
    val itemPrice: Long
        get() {
            val listing = listing ?: return 0L
            return when {
                isAuction -> auction?.bid ?: 0L
                listing.pricing == PricingModel.PWYW && amountText.isNotBlank() -> toMinorUnits(amountText, currency)
                else -> listing.price
            }
        }

    /** The item and, for shipping, the chosen option's postage. */
    val total: Long get() = itemPrice + (selectedOption?.price ?: 0L)

    /** A pay-what-you-want amount at or above the listing's minimum. */
    val amountOk: Boolean
        get() {
            val listing = listing ?: return false
            if (listing.pricing != PricingModel.PWYW) return true
            return amountText.isNotBlank() && toMinorUnits(amountText, currency) >= listing.price
        }

    /**
     * The address fields shipping needs and lacks, by their form field names,
     * as the Comptroller requires them: name, first line, city and country.
     */
    val missing: Set<String>
        get() = buildSet {
            if (addressName.isBlank()) add("address_name")
            if (addressLine1.isBlank()) add("address_line1")
            if (addressCity.isBlank()) add("address_city")
            if (addressCountry.isBlank()) add("address_country")
        }

    /**
     * Whether Pay can go: a delivery method, an amount the listing accepts,
     * and for shipping an option and the required address, as the server
     * checks them; an auction only for its winner.
     */
    val canSubmit: Boolean
        get() = listing != null && delivery != null && !submitting && amountOk &&
            (!isAuction || wonAuction) &&
            (delivery != DeliveryMethod.SHIPPING || (selectedOption != null && missing.isEmpty()))
}

sealed interface CheckoutEvent {
    data class OpenStripe(val url: String) : CheckoutEvent
    data class OrderComplete(val orderId: String) : CheckoutEvent
    /** A failure the app itself detected, named by resource so it is translated. */
    data class ShowError(@StringRes val message: Int) : CheckoutEvent
    /** A failure carrying the server's own (already localised) message. */
    data class ShowFailure(val message: String) : CheckoutEvent
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MarketRepository,
) : ViewModel() {

    val listingId: String = savedStateHandle.get<String>("listingId").orEmpty()

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CheckoutEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CheckoutEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val detail = repository.getListing(listingId)
                // The regions say which options cover the buyer's country, and
                // the account holds the buyer's saved address; neither is
                // needed to check out, so either may fail quietly.
                val regions = runCatching { repository.shippingRegions() }.getOrNull()
                val account = runCatching { repository.getAccount() }.getOrNull()
                val delivery = preselectDelivery(detail.listing, detail.shipping)
                // Seed the pay-what-you-want field with the listing minimum: an
                // empty field skips the client-side amount check and the server
                // rejects it.
                val amountSeed =
                    if (detail.listing.pricing == PricingModel.PWYW && detail.listing.currency != null) {
                        java.math.BigDecimal(detail.listing.price)
                            .movePointLeft(currencyDecimals(detail.listing.currency))
                            .toPlainString()
                    } else {
                        ""
                    }
                val loaded = _uiState.value.copy(
                    isLoading = false,
                    listing = detail.listing,
                    shipping = detail.shipping,
                    auction = detail.auction,
                    regions = regions,
                    delivery = delivery,
                    amountText = amountSeed,
                )
                _uiState.value = if (account != null && !loaded.addressTouched) seeded(loaded, account) else loaded
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /**
     * [state] with the buyer's saved address, and the cheapest option covering
     * its country.
     */
    private fun seeded(state: CheckoutUiState, account: Account): CheckoutUiState {
        val country = account.addressCountry
        return state.copy(
            addressName = account.addressName,
            addressLine1 = account.addressLine1,
            addressLine2 = account.addressLine2,
            addressCity = account.addressCity,
            addressRegion = account.addressRegion,
            addressPostcode = account.addressPostcode,
            addressCountry = country,
            optionId = cheapestFor(country, state.shipping, state.regions)?.id ?: state.optionId,
        )
    }

    fun onDeliveryChanged(method: DeliveryMethod) {
        _uiState.value = _uiState.value.copy(delivery = method)
    }

    fun onAmountChanged(text: String) {
        _uiState.value = _uiState.value.copy(amountText = text)
    }

    fun onAddressChanged(
        country: String = _uiState.value.addressCountry,
        name: String = _uiState.value.addressName,
        line1: String = _uiState.value.addressLine1,
        line2: String = _uiState.value.addressLine2,
        city: String = _uiState.value.addressCity,
        region: String = _uiState.value.addressRegion,
        postcode: String = _uiState.value.addressPostcode,
    ) {
        val before = _uiState.value
        // A new country picks the cheapest option covering it; with none known
        // to, the buyer's choice stays while it is still offered.
        val nextOption = if (country != before.addressCountry) {
            cheapestFor(country, before.shipping, before.regions)?.id
                ?: before.optionId?.takeIf { id -> optionsFor(country, before.shipping, before.regions).any { it.id == id } }
        } else {
            before.optionId
        }
        _uiState.value = before.copy(
            addressCountry = country,
            addressName = name,
            addressLine1 = line1,
            addressLine2 = line2,
            addressCity = city,
            addressRegion = region,
            addressPostcode = postcode,
            optionId = nextOption,
            addressTouched = true,
        )
    }

    fun onShippingOptionChanged(id: String?) {
        _uiState.value = _uiState.value.copy(optionId = id)
    }

    fun submit() {
        val state = _uiState.value
        val listing = state.listing ?: return
        val delivery = state.delivery
        val isSubscription = listing.pricing == PricingModel.SUBSCRIPTION
        if (!isSubscription && !state.canSubmit) return
        viewModelScope.launch {
            _uiState.value = state.copy(submitting = true)
            try {
                if (isSubscription) {
                    val r = repository.createSubscription(
                        listing = listing.id,
                        clientPlatform = ANDROID_PLATFORM,
                    )
                    if (r.checkoutUrl.isNotBlank()) {
                        _events.tryEmit(CheckoutEvent.OpenStripe(r.checkoutUrl))
                    } else {
                        _events.tryEmit(CheckoutEvent.ShowError(R.string.market_checkout_subscription_start_failed))
                    }
                    return@launch
                }

                // An auction listing is always bought through its completion,
                // whose winner the server checks.
                val fields = buildOrderFields(listing, delivery, state)
                val response = if (state.isAuction) {
                    repository.createAuctionOrder(fields)
                } else {
                    repository.createOrder(fields)
                }
                if (response.checkoutUrl.isNotBlank()) {
                    _events.tryEmit(CheckoutEvent.OpenStripe(response.checkoutUrl))
                } else if (response.order != null && response.order.id.isNotEmpty()) {
                    _events.tryEmit(CheckoutEvent.OrderComplete(response.order.id))
                } else {
                    _events.tryEmit(CheckoutEvent.ShowError(R.string.market_checkout_start_failed))
                }
            } catch (e: Exception) {
                _events.tryEmit(CheckoutEvent.ShowFailure(e.toMochiError().userMessage()))
            } finally {
                _uiState.value = _uiState.value.copy(submitting = false)
            }
        }
    }

    private fun buildOrderFields(
        listing: Listing,
        delivery: DeliveryMethod?,
        state: CheckoutUiState,
    ): Map<String, String?> {
        // The Comptroller mints the Stripe success_url / cancel_url when
        // client_platform=android so the redirect lands on a mochi:// URI
        // that MainActivity's intent filter catches — not an HTTPS page
        // that the Custom Tab would render in-place.
        val fields = mutableMapOf<String, String?>(
            "listing" to listing.id,
            "delivery" to delivery?.name?.lowercase(),
            "client_platform" to ANDROID_PLATFORM,
        )
        val option = state.selectedOption
        if (delivery == DeliveryMethod.SHIPPING && option != null) {
            fields["option"] = option.id
            fields["address_name"] = state.addressName
            fields["address_line1"] = state.addressLine1
            fields["address_line2"] = state.addressLine2
            fields["address_city"] = state.addressCity
            fields["address_region"] = state.addressRegion
            fields["address_postcode"] = state.addressPostcode
            fields["address_country"] = state.addressCountry
        }
        if (!state.isAuction &&
            listing.pricing == PricingModel.PWYW &&
            state.amountText.isNotBlank() &&
            listing.currency != null
        ) {
            fields["amount"] = toMinorUnits(state.amountText, listing.currency).toString()
        }
        return fields
    }

    companion object {
        /**
         * Makes the Comptroller mint `mochi://` return URLs for Stripe
         * Checkout; https ones never route back to the app from a Custom Tab.
         */
        private const val ANDROID_PLATFORM = "android"
    }
}

private fun preselectDelivery(
    listing: Listing,
    shipping: List<ShippingOption>,
): DeliveryMethod? {
    val options = buildList {
        if (listing.shipping > 0 || shipping.isNotEmpty()) add(DeliveryMethod.SHIPPING)
        if (listing.pickup > 0) add(DeliveryMethod.PICKUP)
        if (listing.type == ListingType.DIGITAL) add(DeliveryMethod.DOWNLOAD)
    }
    return options.singleOrNull()
}
