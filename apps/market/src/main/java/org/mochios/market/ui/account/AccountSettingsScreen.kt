// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.account

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.PlacePicker
import org.mochios.market.R
import org.mochios.market.navigation.MarketApp

/**
 * Seller account settings. Mirrors web's `apps/market/web/src/features/
 * account/AccountSettings`. Layout (top → bottom):
 *
 *  1. Suspension warning banner (only when `status == "suspended"` or
 *     `"banned"`), surfacing `account.reason` and a fixed restrictions
 *     blurb.
 *  2. Biography multiline text field (capped at
 *     [AccountSettingsViewModel.BIOGRAPHY_LIMIT] characters).
 *  3. Place picker (shared lib component, drives an inline map).
 *  4. Stripe card with the latest `charges_enabled` / `payouts_enabled`
 *     flags plus links to the dashboard and a "Refresh" button.
 *  5. Save button at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val savedToast = stringResource(R.string.market_account_saved)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountSettingsEvent.Saved -> {
                    snackbarHostState.showSnackbar(savedToast)
                }
                is AccountSettingsEvent.Error -> {
                    snackbarHostState.showSnackbar(event.error.userMessage())
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_account_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.account == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error?.userMessage()
                            ?: stringResource(R.string.market_account_load_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val account = state.account!!
                    val status = account.status.lowercase()
                    val restricted = status == "suspended" || status == "banned"
                    if (restricted) {
                        SuspensionWarning(
                            status = status,
                            reason = account.reason,
                        )
                    }

                    if (!restricted) {
                        SellerStatusCard(
                            isSeller = account.seller == 1,
                            isOnboarded = account.onboarded == 1,
                            onNavigate = {
                                navController.navigate(
                                    org.mochios.market.navigation.MarketApp.SELLER_SETTINGS,
                                )
                            },
                        )
                    }

                    ProfileCard(
                        biography = state.biographyDraft,
                        onBiographyChange = viewModel::updateBiography,
                        place = state.placeDraft,
                        onPlaceChange = viewModel::updatePlace,
                        isSaving = state.isSaving,
                        onSave = viewModel::save,
                    )


                    if (account.seller == 1) {
                        BusinessDetailsSection(
                            business = state.businessDraft,
                            company = state.companyDraft,
                            vat = state.vatDraft,
                            onBusinessChange = viewModel::updateBusiness,
                            onCompanyChange = viewModel::updateCompany,
                            onVatChange = viewModel::updateVat,
                        )
                    }

                    ShippingAddressSection(
                        name = state.addressNameDraft,
                        line1 = state.addressLine1Draft,
                        line2 = state.addressLine2Draft,
                        city = state.addressCityDraft,
                        region = state.addressRegionDraft,
                        postcode = state.addressPostcodeDraft,
                        country = state.addressCountryDraft,
                        onNameChange = viewModel::updateAddressName,
                        onLine1Change = viewModel::updateAddressLine1,
                        onLine2Change = viewModel::updateAddressLine2,
                        onCityChange = viewModel::updateAddressCity,
                        onRegionChange = viewModel::updateAddressRegion,
                        onPostcodeChange = viewModel::updateAddressPostcode,
                        onCountryChange = viewModel::updateAddressCountry,
                    )

                    if (account.onboarded == 1) {
                        StripeCard(
                            chargesEnabled = state.stripeStatus?.chargesEnabled,
                            payoutsEnabled = state.stripeStatus?.payoutsEnabled,
                            testMode = account.stripeTestmode,
                            isLoading = state.stripeStatusLoading,
                            onDashboard = {
                                val url = if (account.stripeTestmode) {
                                    "https://dashboard.stripe.com/test"
                                } else {
                                    "https://dashboard.stripe.com"
                                }
                                CustomTabsIntent.Builder().build()
                                    .launchUrl(context, Uri.parse(url))
                            },
                            onRefresh = viewModel::refreshStripe,
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            navController.navigate(MarketApp.NOTIFICATION_PREFERENCES)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.market_notifications_title))
                    }

                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                        }
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.market_account_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun SuspensionWarning(status: String, reason: String) {
    val titleRes = if (status == "banned") {
        R.string.market_account_status_banned_title
    } else {
        R.string.market_account_status_suspended_title
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (reason.isNotEmpty()) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    text = stringResource(R.string.market_account_status_restrictions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** Default platform fee % shown before the live fees endpoint resolves. */
private const val DEFAULT_PLATFORM_FEE = 5.0

private val SellerGreen = Color(0xFF16A34A)
private val SellerGreenBg = Color(0xFFF0FDF4)
private val SellerGreenBorder = Color(0xFFBBF7D0)
private val FeesBg = Color(0xFFFEF6E7)
private val FeesBorder = Color(0xFFFDE68A)

// The step/fees boxes use fixed light fills in both themes, so their text must
// use fixed dark colours — theme onSurface/onSurfaceVariant turn white in dark
// mode and disappear against the light fills.
private val OnPastelText = Color(0xFF1F2937)
private val OnPastelSubtext = Color(0xFF6B7280)

/** White card holding the editable profile fields (biography + location). */
@Composable
private fun ProfileCard(
    biography: String,
    onBiographyChange: (String) -> Unit,
    place: org.mochios.android.model.PlaceData?,
    onPlaceChange: (org.mochios.android.model.PlaceData) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BiographyField(value = biography, onChange = onBiographyChange)
            LocationField(place = place, onChange = onPlaceChange)
            Button(
                onClick = onSave,
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.market_account_save))
            }
        }
    }
}

@Composable
private fun BiographyField(
    value: String,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.market_account_biography_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = {
                Text(stringResource(R.string.market_account_biography_placeholder))
            },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.market_account_biography_counter,
                value.length,
                AccountSettingsViewModel.BIOGRAPHY_LIMIT,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun LocationField(
    place: org.mochios.android.model.PlaceData?,
    onChange: (org.mochios.android.model.PlaceData) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.market_account_location_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(8.dp))
        PlacePicker(place = place, onPlaceSelected = onChange)
    }
}

/**
 * Compact seller-status card at the top of the Account screen. Mirrors web's
 * `SellerStatusCard` — nudges non-sellers to activate, points active sellers
 * at the seller-settings page, and flags incomplete Stripe onboarding. Hidden
 * for suspended / banned accounts (the suspension warning takes precedence).
 */
@Composable
private fun SellerStatusCard(
    isSeller: Boolean,
    isOnboarded: Boolean,
    onNavigate: () -> Unit,
) {
    val (titleRes, bodyRes, actionRes) = when {
        !isSeller -> Triple(
            R.string.market_account_seller_card_start_title,
            R.string.market_account_seller_card_start_body,
            R.string.market_sidebar_become_seller,
        )
        isOnboarded -> Triple(
            R.string.market_account_seller_card_active_title,
            R.string.market_account_seller_card_active_body,
            R.string.market_account_seller_card_view,
        )
        else -> Triple(
            R.string.market_account_seller_card_incomplete_title,
            R.string.market_account_seller_card_incomplete_body,
            R.string.market_account_seller_card_continue,
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(onClick = onNavigate) {
                    Text(stringResource(actionRes))
                }
            }
        }
    }
}

@Composable
private fun BusinessDetailsSection(
    business: Boolean,
    company: String,
    vat: String,
    onBusinessChange: (Boolean) -> Unit,
    onCompanyChange: (String) -> Unit,
    onVatChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.market_account_business_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.market_account_business_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.market_account_business_switch),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = business, onCheckedChange = onBusinessChange)
        }
        OutlinedTextField(
            value = company,
            onValueChange = onCompanyChange,
            label = { Text(stringResource(R.string.market_account_company_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vat,
            onValueChange = onVatChange,
            label = { Text(stringResource(R.string.market_account_vat_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StripeCard(
    chargesEnabled: Boolean?,
    payoutsEnabled: Boolean?,
    @Suppress("UNUSED_PARAMETER") testMode: Boolean,
    isLoading: Boolean,
    onDashboard: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.market_account_stripe_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StatusRow(
                label = if (chargesEnabled == true) {
                    stringResource(R.string.market_account_stripe_charges_enabled)
                } else {
                    stringResource(R.string.market_account_stripe_charges_disabled)
                },
                enabled = chargesEnabled == true,
            )
            StatusRow(
                label = if (payoutsEnabled == true) {
                    stringResource(R.string.market_account_stripe_payouts_enabled)
                } else {
                    stringResource(R.string.market_account_stripe_payouts_disabled)
                },
                enabled = payoutsEnabled == true,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDashboard,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.market_account_stripe_dashboard))
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.market_account_stripe_refresh))
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (enabled) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ShippingAddressSection(
    name: String,
    line1: String,
    line2: String,
    city: String,
    region: String,
    postcode: String,
    country: String,
    onNameChange: (String) -> Unit,
    onLine1Change: (String) -> Unit,
    onLine2Change: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onPostcodeChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.market_account_address_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.market_account_address_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.market_checkout_address_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = line1,
            onValueChange = onLine1Change,
            label = { Text(stringResource(R.string.market_checkout_address_line1)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = line2,
            onValueChange = onLine2Change,
            label = { Text(stringResource(R.string.market_checkout_address_line2)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = city,
            onValueChange = onCityChange,
            label = { Text(stringResource(R.string.market_checkout_address_city)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = region,
            onValueChange = onRegionChange,
            label = { Text(stringResource(R.string.market_checkout_address_region)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = postcode,
            onValueChange = onPostcodeChange,
            label = { Text(stringResource(R.string.market_checkout_address_postcode)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = country,
            onValueChange = onCountryChange,
            label = { Text(stringResource(R.string.market_checkout_address_country)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
