package org.mochios.market.ui.checkout

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
import org.mochios.market.lib.cheapestMatchingZone
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Auction
import org.mochios.market.model.Bid
import org.mochios.market.model.DeliveryMethod
import org.mochios.market.model.Listing
import org.mochios.market.model.ShippingOption
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * UI state for [CheckoutScreen]. Holds everything the screen needs to
 * render a single checkout pass: the listing being purchased, the
 * available shipping zones, the auction context (when reached from a
 * "Complete purchase" CTA), and every editable form field.
 *
 * Money values held here are minor units; the free-text [amount] string
 * is converted on-submit via [toMinorUnits]. The selected zone is
 * carried as a [ShippingOption.id] string to match the dropdown's value
 * type — the auto-pick effect in [CheckoutViewModel.onCountryChanged]
 * fills it in for known countries.
 */
data class CheckoutUiState(
    val isLoading: Boolean = true,
    val listing: Listing? = null,
    val shipping: List<ShippingOption> = emptyList(),
    val auction: Auction? = null,
    val wonBid: Bid? = null,
    val delivery: DeliveryMethod? = null,
    val optionId: Long? = null,
    val amountText: String = "",
    val addressName: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val addressCity: String = "",
    val addressRegion: String = "",
    val addressPostcode: String = "",
    val addressCountry: String = "",
    val submitting: Boolean = false,
    val error: MochiError? = null,
)

/**
 * Side-effect events emitted by [CheckoutViewModel]. The screen collects
 * these inside a [androidx.compose.runtime.LaunchedEffect] so the
 * checkout URL can be opened in a [androidx.browser.customtabs.CustomTabsIntent]
 * once the order create succeeds.
 */
sealed interface CheckoutEvent {
    data class OpenStripe(val url: String) : CheckoutEvent
    data class OrderComplete(val orderId: Long) : CheckoutEvent
    data class ShowError(val message: String) : CheckoutEvent
}

/**
 * ViewModel for [CheckoutScreen]. Reads `listingId` from the
 * [SavedStateHandle] (set by the `MarketApp.CHECKOUT` route), loads the
 * listing detail in `init`, and tries to match the buyer's already-won
 * bid for this listing so the "Complete purchase" path skips the
 * shipping-amount UI and posts to `orders/auction` instead of
 * `orders/create`.
 *
 * Shipping address fields trigger a re-evaluation of
 * [cheapestMatchingZone] on every country change so the dropdown
 * pre-selects a covering zone without the user picking manually.
 */
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MarketRepository,
) : ViewModel() {

    val listingId: Long = savedStateHandle.get<String>("listingId")?.toLongOrNull() ?: 0L

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
                // Try to find a won bid for this listing so the screen
                // knows to fall through to the auction-completion flow.
                val wonBid = if (detail.listing.pricing == org.mochios.market.model.PricingModel.AUCTION) {
                    runCatching { repository.myBids(status = "won").bids }
                        .getOrDefault(emptyList())
                        .firstOrNull { it.listing == listingId }
                } else {
                    null
                }
                val delivery = preselectDelivery(detail.listing, detail.shipping)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    listing = detail.listing,
                    shipping = detail.shipping,
                    auction = detail.auction,
                    wonBid = wonBid,
                    delivery = delivery,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
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
        // Re-pick the cheapest matching zone whenever country changes;
        // honour any explicit override the buyer has already made.
        val nextOption = if (country != before.addressCountry) {
            cheapestMatchingZone(country, before.shipping)?.id ?: before.optionId
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
        )
    }

    fun onShippingOptionChanged(id: Long?) {
        _uiState.value = _uiState.value.copy(optionId = id)
    }

    /**
     * Submit either an `orders/create`, an `orders/auction`, or a
     * `subscriptions/create` depending on the listing's pricing model.
     * Subscriptions don't surface the shipping form — that branch is
     * guarded by the screen, not here. Emits [CheckoutEvent.OpenStripe]
     * (or [CheckoutEvent.OrderComplete] for free orders) on success and
     * [CheckoutEvent.ShowError] on failure.
     */
    fun submit() {
        val state = _uiState.value
        val listing = state.listing ?: return
        val delivery = state.delivery
        viewModelScope.launch {
            _uiState.value = state.copy(submitting = true)
            try {
                val isAuctionCompletion = listing.pricing ==
                    org.mochios.market.model.PricingModel.AUCTION && state.wonBid != null
                val isSubscription = listing.pricing ==
                    org.mochios.market.model.PricingModel.SUBSCRIPTION

                if (isSubscription) {
                    val r = repository.createSubscription(
                        listing = listing.id,
                        clientPlatform = ANDROID_PLATFORM,
                    )
                    if (r.checkoutUrl.isNotBlank()) {
                        _events.tryEmit(CheckoutEvent.OpenStripe(r.checkoutUrl))
                    } else {
                        _events.tryEmit(CheckoutEvent.ShowError(SUBSCRIBE_FAILED))
                    }
                    return@launch
                }

                val fields = buildOrderFields(listing, delivery, state, isAuctionCompletion)
                val response = if (isAuctionCompletion) {
                    repository.createAuctionOrder(fields)
                } else {
                    repository.createOrder(fields)
                }
                if (response.checkoutUrl.isNotBlank()) {
                    _events.tryEmit(CheckoutEvent.OpenStripe(response.checkoutUrl))
                } else if (response.order != null && response.order.id > 0) {
                    _events.tryEmit(CheckoutEvent.OrderComplete(response.order.id))
                } else {
                    _events.tryEmit(CheckoutEvent.ShowError(CHECKOUT_FAILED))
                }
            } catch (e: Exception) {
                _events.tryEmit(CheckoutEvent.ShowError(e.toMochiError().userMessage()))
            } finally {
                _uiState.value = _uiState.value.copy(submitting = false)
            }
        }
    }

    private fun buildOrderFields(
        listing: Listing,
        delivery: DeliveryMethod?,
        state: CheckoutUiState,
        isAuctionCompletion: Boolean,
    ): Map<String, String?> {
        // The Comptroller mints the Stripe success_url / cancel_url when
        // client_platform=android so the redirect lands on a mochi:// URI
        // that MainActivity's intent filter catches — not an HTTPS page
        // that the Custom Tab would render in-place.
        val fields = mutableMapOf<String, String?>(
            "listing" to listing.id.toString(),
            "delivery" to delivery?.name?.lowercase(),
            "client_platform" to ANDROID_PLATFORM,
        )
        if (delivery == DeliveryMethod.SHIPPING && state.optionId != null) {
            fields["option"] = state.optionId.toString()
            fields["address_name"] = state.addressName
            fields["address_line1"] = state.addressLine1
            fields["address_line2"] = state.addressLine2
            fields["address_city"] = state.addressCity
            fields["address_region"] = state.addressRegion
            fields["address_postcode"] = state.addressPostcode
            fields["address_country"] = state.addressCountry
        }
        if (!isAuctionCompletion &&
            listing.pricing == org.mochios.market.model.PricingModel.PWYW &&
            state.amountText.isNotBlank() &&
            listing.currency != null
        ) {
            fields["amount"] = toMinorUnits(state.amountText, listing.currency).toString()
        }
        return fields
    }

    companion object {
        // Plain strings to avoid pulling Context into the VM. Screens
        // override via stringResource when they intercept errors before
        // they bubble.
        private const val CHECKOUT_FAILED = "Could not start checkout"
        private const val SUBSCRIBE_FAILED = "Could not start subscription"

        /**
         * Tells the Comptroller to mint mochi:// return URLs for Stripe Checkout
         * instead of https URLs. Without this, the Stripe-hosted page redirects
         * to a mochi-os.org URL the Custom Tab can't hand back to the app.
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
        if (listing.type == org.mochios.market.model.ListingType.DIGITAL) add(DeliveryMethod.DOWNLOAD)
    }
    return options.singleOrNull()
}

