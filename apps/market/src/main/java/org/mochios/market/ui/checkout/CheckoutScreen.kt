// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.checkout

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.market.R
import org.mochios.market.lib.currencyDecimals
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Currency
import org.mochios.market.model.DeliveryMethod
import org.mochios.market.model.Listing
import org.mochios.market.model.PricingModel
import org.mochios.market.model.ShippingOption
import org.mochios.market.navigation.MarketApp

/**
 * Checkout flow for a single listing.
 *
 * Mirrors `apps/market/web/src/features/buying/checkout-page.tsx`. The
 * screen branches by pricing model:
 *  - Subscription: a simple "Subscribe" button that creates a Stripe
 *    Checkout session and opens it.
 *  - Fixed / PWYW / Auction: the full delivery + shipping + summary
 *    layout below. PWYW exposes an "Your price" input; auction-completion
 *    (when the buyer is the winning bidder) hides the price input and
 *    posts to `orders/auction` so the won bid amount is honoured.
 *
 * The Pay with Stripe button uses [CustomTabsIntent] to open the
 * Stripe-hosted checkout. The `mochi://` URI scheme is already claimed
 * by MainActivity so the success / cancel return URLs route back without
 * any extra intent filter here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    navController: NavController,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val browserUnavailable = stringResource(R.string.market_checkout_browser_unavailable)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CheckoutEvent.OpenStripe -> {
                    try {
                        CustomTabsIntent.Builder().build()
                            .launchUrl(context, event.url.toUri())
                    } catch (_: ActivityNotFoundException) {
                        snackbar.showSnackbar(browserUnavailable)
                    }
                }
                is CheckoutEvent.OrderComplete -> {
                    navController.navigate(MarketApp.purchaseDetail(event.orderId.toString())) {
                        popUpTo(MarketApp.CHECKOUT) { inclusive = true }
                    }
                }
                is CheckoutEvent.ShowError -> snackbar.showSnackbar(event.message)
            }
        }
    }

    val title = if (state.listing?.pricing == PricingModel.SUBSCRIPTION) {
        stringResource(R.string.market_checkout_subscribe_title)
    } else {
        stringResource(R.string.market_checkout_title)
    }

    MochiScaffold(
        title = title,
        onBack = { navController.popBackStack() },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> ErrorState(error = state.error!!, onRetry = { viewModel.load() })
                state.listing == null -> ErrorState(
                    error = org.mochios.android.api.MochiError.NotFoundError(
                        stringResource(R.string.market_checkout_listing_not_found),
                    ),
                )
                state.listing!!.pricing == PricingModel.SUBSCRIPTION -> SubscriptionBody(
                    listing = state.listing!!,
                    submitting = state.submitting,
                    onSubscribe = { viewModel.submit() },
                )
                else -> CheckoutBody(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun SubscriptionBody(
    listing: Listing,
    submitting: Boolean,
    onSubscribe: () -> Unit,
) {
    val currency = listing.currency ?: Currency.GBP
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.market_checkout_subscribe_intro),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(listing.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatPrice(listing.price, currency), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(4.dp))
                    val unit = if (listing.interval == org.mochios.market.model.Interval.YEARLY) {
                        stringResource(R.string.market_price_per_year, "").replace("%1\$s", "").trim()
                    } else {
                        stringResource(R.string.market_price_per_month, "").replace("%1\$s", "").trim()
                    }
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(
            onClick = onSubscribe,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                if (submitting) stringResource(R.string.market_checkout_subscribing)
                else stringResource(R.string.market_checkout_subscribe_button),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutBody(state: CheckoutUiState, viewModel: CheckoutViewModel) {
    val listing = state.listing ?: return
    val currency = listing.currency ?: Currency.GBP
    val isAuctionCompletion =
        listing.pricing == PricingModel.AUCTION && state.wonBid != null

    val itemPrice = when {
        isAuctionCompletion -> state.wonBid?.amount ?: 0L
        listing.pricing == PricingModel.PWYW && state.amountText.isNotBlank() ->
            toMinorUnits(state.amountText, currency)
        listing.pricing == PricingModel.AUCTION -> state.auction?.bid ?: 0L
        else -> listing.price
    }
    val selectedZone = state.shipping.firstOrNull { it.id == state.optionId }
    val total = itemPrice + (selectedZone?.price ?: 0L)

    // A pay-what-you-want listing must carry an amount at or above the
    // minimum before checkout can proceed; otherwise an empty/too-low field
    // reaches the server and returns "Amount must be at least X".
    val pwywAmountOk = listing.pricing != PricingModel.PWYW || isAuctionCompletion ||
        (state.amountText.isNotBlank() && toMinorUnits(state.amountText, currency) >= listing.price)

    val available = remember(listing, state.shipping) {
        buildList {
            if (listing.shipping > 0 || state.shipping.isNotEmpty()) add(DeliveryMethod.SHIPPING)
            if (listing.pickup > 0) add(DeliveryMethod.PICKUP)
            if (listing.type == org.mochios.market.model.ListingType.DIGITAL) add(DeliveryMethod.DOWNLOAD)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ListingSummaryCard(listing = listing, itemPrice = itemPrice, currency = currency)

        if (listing.pricing == PricingModel.PWYW && !isAuctionCompletion) {
            PwywAmountInput(
                value = state.amountText,
                currency = currency,
                minimumPrice = listing.price,
                onValueChange = { viewModel.onAmountChanged(it) },
            )
        }

        if (available.size > 1) {
            DeliveryRadioGroup(
                available = available,
                selected = state.delivery,
                onSelected = { viewModel.onDeliveryChanged(it) },
            )
        }

        if (state.delivery == DeliveryMethod.SHIPPING && state.shipping.isNotEmpty()) {
            AddressForm(state = state, onChange = viewModel::onAddressChanged)
            ShippingZoneDropdown(
                zones = state.shipping,
                selectedId = state.optionId,
                onSelected = { viewModel.onShippingOptionChanged(it) },
            )
        }

        PricingSummary(
            itemPrice = itemPrice,
            selectedZone = selectedZone,
            total = total,
            currency = currency,
            deliveryIsShipping = state.delivery == DeliveryMethod.SHIPPING,
        )

        Button(
            onClick = { viewModel.submit() },
            enabled = state.delivery != null && !state.submitting && pwywAmountOk,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                when {
                    state.submitting -> stringResource(R.string.market_checkout_processing)
                    total == 0L -> stringResource(R.string.market_checkout_get_free)
                    else -> stringResource(R.string.market_checkout_pay_button)
                },
            )
        }
        Text(
            stringResource(R.string.market_checkout_secure),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ListingSummaryCard(listing: Listing, itemPrice: Long, currency: Currency) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.market_checkout_item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listing.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatPrice(itemPrice, currency),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PwywAmountInput(
    value: String,
    currency: Currency,
    minimumPrice: Long,
    onValueChange: (String) -> Unit,
) {
    val decimals = currencyDecimals(currency)
    val label = stringResource(R.string.market_checkout_pwyw_label, formatPrice(minimumPrice, currency))
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            // Accept digits + optional decimal mark; up to N decimals.
            val regex = if (decimals == 0) Regex("""^\d*$""")
            else Regex("""^\d*(?:[.,]\d{0,$decimals})?$""")
            if (input.isEmpty() || regex.matches(input)) onValueChange(input)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeliveryRadioGroup(
    available: List<DeliveryMethod>,
    selected: DeliveryMethod?,
    onSelected: (DeliveryMethod) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.market_checkout_delivery_method),
            style = MaterialTheme.typography.labelLarge,
        )
        available.forEach { method ->
            val labelRes = when (method) {
                DeliveryMethod.SHIPPING -> R.string.market_checkout_delivery_shipping
                DeliveryMethod.PICKUP -> R.string.market_checkout_delivery_pickup
                DeliveryMethod.DOWNLOAD -> R.string.market_checkout_delivery_download
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = method == selected, onClick = { onSelected(method) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = method == selected, onClick = { onSelected(method) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(labelRes))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressForm(state: CheckoutUiState, onChange: (String, String, String, String, String, String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.market_checkout_address_heading),
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedTextField(
            value = state.addressCountry,
            onValueChange = {
                onChange(it, state.addressName, state.addressLine1, state.addressLine2,
                    state.addressCity, state.addressRegion, state.addressPostcode)
            },
            label = { Text(stringResource(R.string.market_checkout_address_country)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.addressName,
            onValueChange = {
                onChange(state.addressCountry, it, state.addressLine1, state.addressLine2,
                    state.addressCity, state.addressRegion, state.addressPostcode)
            },
            label = { Text(stringResource(R.string.market_checkout_address_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.addressLine1,
            onValueChange = {
                onChange(state.addressCountry, state.addressName, it, state.addressLine2,
                    state.addressCity, state.addressRegion, state.addressPostcode)
            },
            label = { Text(stringResource(R.string.market_checkout_address_line1)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.addressLine2,
            onValueChange = {
                onChange(state.addressCountry, state.addressName, state.addressLine1, it,
                    state.addressCity, state.addressRegion, state.addressPostcode)
            },
            label = { Text(stringResource(R.string.market_checkout_address_line2)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.addressCity,
                onValueChange = {
                    onChange(state.addressCountry, state.addressName, state.addressLine1, state.addressLine2,
                        it, state.addressRegion, state.addressPostcode)
                },
                label = { Text(stringResource(R.string.market_checkout_address_city)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.addressRegion,
                onValueChange = {
                    onChange(state.addressCountry, state.addressName, state.addressLine1, state.addressLine2,
                        state.addressCity, it, state.addressPostcode)
                },
                label = { Text(stringResource(R.string.market_checkout_address_region)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = state.addressPostcode,
            onValueChange = {
                onChange(state.addressCountry, state.addressName, state.addressLine1, state.addressLine2,
                    state.addressCity, state.addressRegion, it)
            },
            label = { Text(stringResource(R.string.market_checkout_address_postcode)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShippingZoneDropdown(
    zones: List<ShippingOption>,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = zones.firstOrNull { it.id == selectedId }
    val display = selected?.let { zone ->
        val currency = runCatching { Currency.valueOf(zone.currency.uppercase()) }
            .getOrDefault(Currency.GBP)
        if (zone.days.isNotBlank()) {
            stringResource(
                R.string.market_checkout_shipping_zone_with_days,
                zone.region, formatPrice(zone.price, currency), zone.days,
            )
        } else {
            stringResource(R.string.market_checkout_shipping_zone, zone.region, formatPrice(zone.price, currency))
        }
    } ?: ""

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.market_checkout_shipping_option),
            style = MaterialTheme.typography.labelLarge,
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.market_checkout_shipping_placeholder)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                zones.forEach { zone ->
                    val currency = runCatching { Currency.valueOf(zone.currency.uppercase()) }
                        .getOrDefault(Currency.GBP)
                    val rowText = if (zone.days.isNotBlank()) {
                        stringResource(
                            R.string.market_checkout_shipping_zone_with_days,
                            zone.region, formatPrice(zone.price, currency), zone.days,
                        )
                    } else {
                        stringResource(
                            R.string.market_checkout_shipping_zone,
                            zone.region, formatPrice(zone.price, currency),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(rowText) },
                        onClick = {
                            onSelected(zone.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PricingSummary(
    itemPrice: Long,
    selectedZone: ShippingOption?,
    total: Long,
    currency: Currency,
    deliveryIsShipping: Boolean,
) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.market_checkout_summary),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.market_purchase_item), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPrice(itemPrice, currency))
            }
            when {
                selectedZone != null -> {
                    val zoneCurrency = runCatching {
                        Currency.valueOf(selectedZone.currency.uppercase())
                    }.getOrDefault(currency)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.market_checkout_shipping),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(formatPrice(selectedZone.price, zoneCurrency))
                    }
                }
                deliveryIsShipping -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.market_checkout_shipping),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.market_checkout_select_option),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.market_checkout_total), style = MaterialTheme.typography.titleSmall)
                Text(formatPrice(total, currency), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

