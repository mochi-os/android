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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.mochios.android.i18n.LocalFormat
import org.mochios.market.R
import org.mochios.market.ui.components.FeeDisclosure
import org.mochios.market.ui.components.formatPercent

/**
 * Seller settings / activation screen. Mirrors web's
 * `apps/market/web/src/features/account/SellerSettingsPage`. Consolidates
 * seller activation and Stripe Connect onboarding into one place:
 *
 *  1. Status summary (account status / payments / fees).
 *  2. Setup card with a two-step indicator (activate → connect Stripe), the
 *     platform-fee disclosure, and the primary action for the current state.
 *
 * The TopAppBar title flips between "Seller settings" (already a seller) and
 * "Become a seller" (not yet activated).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerSettingsScreen(
    navController: NavController,
    viewModel: SellerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SellerSettingsEvent.Error ->
                    snackbarHostState.showSnackbar(event.error.userMessage())
            }
        }
    }

    val titleRes = if (state.isSeller) {
        R.string.market_seller_settings_title
    } else {
        R.string.market_seller_become_title
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
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
            state.isLoading && state.account == null -> {
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
                val account = state.account!!
                val status = account.status.lowercase()
                val suspended = status == "suspended"
                val banned = status == "banned"
                val dashboardUrl = if (account.stripeTestmode) {
                    "https://dashboard.stripe.com/test"
                } else {
                    "https://dashboard.stripe.com"
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusSummary(
                        state = state,
                        suspended = suspended,
                        banned = banned,
                        reason = account.reason,
                    )

                    SetupCard(
                        state = state,
                        onActivate = viewModel::activate,
                        onConnect = {
                            viewModel.connectStripe(RETURN_URL) { url ->
                                CustomTabsIntent.Builder().build()
                                    .launchUrl(context, Uri.parse(url))
                            }
                        },
                        onDashboard = {
                            CustomTabsIntent.Builder().build()
                                .launchUrl(context, Uri.parse(dashboardUrl))
                        },
                        onCheck = viewModel::checkStatus,
                    )

                    if (state.isSeller) {
                        FutureControlsCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun FutureControlsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.market_seller_future_controls_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.market_seller_future_controls_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusSummary(
    state: SellerSettingsUiState,
    suspended: Boolean,
    banned: Boolean,
    reason: String,
) {
    val accountStatus = when {
        banned -> stringResource(R.string.market_seller_status_banned)
        suspended -> stringResource(R.string.market_seller_status_suspended)
        state.isSellerReady -> stringResource(R.string.market_seller_status_active)
        state.isSeller -> stringResource(R.string.market_seller_status_setup_incomplete)
        else -> stringResource(R.string.market_seller_status_not_activated)
    }
    val payments = when {
        !state.isSeller -> stringResource(R.string.market_seller_payments_activate_first)
        state.isOnboarded -> stringResource(R.string.market_seller_payments_connected)
        state.stripeLinked -> stringResource(R.string.market_seller_payments_needs_info)
        else -> stringResource(R.string.market_seller_payments_not_connected)
    }
    val fees = state.fees?.let {
        stringResource(
            R.string.market_seller_fee_value,
            formatPercent(LocalFormat.current, it.platform),
        )
    } ?: stringResource(R.string.market_seller_fee_loading)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryRow(
                label = stringResource(R.string.market_seller_summary_account_status),
                value = accountStatus,
            )
            if ((suspended || banned) && reason.isNotEmpty()) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            HorizontalDivider()
            SummaryRow(
                label = stringResource(R.string.market_seller_summary_payments),
                value = payments,
            )
            HorizontalDivider()
            SummaryRow(
                label = stringResource(R.string.market_seller_summary_fees),
                value = fees,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SetupCard(
    state: SellerSettingsUiState,
    onActivate: () -> Unit,
    onConnect: () -> Unit,
    onDashboard: () -> Unit,
    onCheck: () -> Unit,
) {
    val headingRes = if (state.isSeller) {
        R.string.market_seller_setup_heading
    } else {
        R.string.market_seller_become_heading
    }
    val descRes = when {
        !state.isSeller -> R.string.market_seller_setup_desc_new
        state.isSellerReady -> R.string.market_seller_setup_desc_ready
        else -> R.string.market_seller_setup_desc_connect
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(headingRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StepRow(
                done = state.isSeller,
                titleRes = R.string.market_seller_step_activate_title,
                descRes = R.string.market_seller_step_activate_desc,
            )
            StepRow(
                done = state.isOnboarded,
                titleRes = R.string.market_seller_step_stripe_title,
                descRes = R.string.market_seller_step_stripe_desc,
            )

            FeeDisclosure(platformFeePercent = state.fees?.platform ?: 0.0)

            if (state.isSeller && state.stripeLinked && !state.isOnboarded) {
                Text(
                    text = stringResource(R.string.market_seller_stripe_more_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Primary action varies with the seller / Stripe state.
            when {
                !state.isSeller -> {
                    Button(
                        onClick = onActivate,
                        enabled = !state.activating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.activating) {
                                stringResource(R.string.market_seller_activating)
                            } else {
                                stringResource(R.string.market_seller_activate_action)
                            },
                        )
                    }
                }
                state.stripeLinked -> {
                    Button(
                        onClick = onDashboard,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.isOnboarded) {
                                stringResource(R.string.market_seller_manage_stripe)
                            } else {
                                stringResource(R.string.market_seller_open_dashboard)
                            },
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = onConnect,
                        enabled = !state.connecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.connecting) {
                                stringResource(R.string.market_seller_connecting)
                            } else {
                                stringResource(R.string.market_seller_connect_stripe)
                            },
                        )
                    }
                }
            }

            if (state.isSeller) {
                OutlinedButton(
                    onClick = onCheck,
                    enabled = !state.checking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.checking) {
                            stringResource(R.string.market_seller_checking)
                        } else {
                            stringResource(R.string.market_seller_check_status)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(done: Boolean, titleRes: Int, descRes: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val RETURN_URL = "mochi://market/account/seller"
