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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    if (status == "suspended" || status == "banned") {
                        SuspensionWarning(
                            status = status,
                            reason = account.reason,
                        )
                    }

                    BiographyField(
                        value = state.biographyDraft,
                        onChange = viewModel::updateBiography,
                    )

                    LocationField(
                        place = state.placeDraft,
                        onChange = viewModel::updatePlace,
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
                            navController.navigate(
                                org.mochios.market.navigation.MarketApp.NOTIFICATION_PREFERENCES,
                            )
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

@Composable
private fun BiographyField(
    value: String,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.market_account_biography_label)) },
            minLines = 4,
            maxLines = 8,
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
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.size(6.dp))
        PlacePicker(place = place, onPlaceSelected = onChange)
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
