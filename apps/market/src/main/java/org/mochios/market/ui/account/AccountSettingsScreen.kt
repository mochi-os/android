package org.mochios.market.ui.account

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

                    ProfileCard(
                        biography = state.biographyDraft,
                        onBiographyChange = viewModel::updateBiography,
                        place = state.placeDraft,
                        onPlaceChange = viewModel::updatePlace,
                        isSaving = state.isSaving,
                        onSave = viewModel::save,
                    )

                    val stripeReady = state.stripeStatus?.let {
                        it.chargesEnabled && it.payoutsEnabled
                    } ?: false
                    BecomeSellerCard(
                        activated = account.seller == 1,
                        stripeConnected = account.onboarded == 1 && stripeReady,
                        connecting = state.stripeConnecting,
                        checking = state.stripeStatusLoading,
                        platformFeePercent = state.fees?.platform ?: DEFAULT_PLATFORM_FEE,
                        onConnect = {
                            viewModel.connectStripe("mochi://market/account") { url ->
                                CustomTabsIntent.Builder().build()
                                    .launchUrl(context, Uri.parse(url))
                            }
                        },
                        onCheckStatus = viewModel::checkStripeStatus,
                        onOpenDashboard = {
                            val url = if (account.stripeTestmode) {
                                "https://dashboard.stripe.com/test"
                            } else {
                                "https://dashboard.stripe.com"
                            }
                            CustomTabsIntent.Builder().build()
                                .launchUrl(context, Uri.parse(url))
                        },
                    )
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
 * "Become a seller" onboarding card: a blue gradient accent bar, intro, the
 * two onboarding steps (activate account → connect Stripe), a fee disclosure,
 * and the Connect/Check-status actions.
 */
@Composable
private fun BecomeSellerCard(
    activated: Boolean,
    stripeConnected: Boolean,
    connecting: Boolean,
    checking: Boolean,
    platformFeePercent: Double,
    onConnect: () -> Unit,
    onCheckStatus: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        // Gradient accent bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.market_account_seller_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = stringResource(R.string.market_account_seller_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SellerStep(
                index = 1,
                done = activated,
                title = stringResource(R.string.market_account_seller_step_activate_title),
                description = stringResource(R.string.market_account_seller_step_activate_desc),
            )
            SellerStep(
                index = 2,
                done = stripeConnected,
                title = stringResource(R.string.market_account_seller_step_stripe_title),
                description = stringResource(R.string.market_account_seller_step_stripe_desc),
            )

            FeesBox(platformFeePercent = platformFeePercent)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (stripeConnected) {
                    Button(
                        onClick = onOpenDashboard,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.market_account_stripe_dashboard))
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !connecting,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.market_account_seller_connect))
                    }
                }
                OutlinedButton(
                    onClick = onCheckStatus,
                    enabled = !checking,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.market_account_seller_check_status))
                }
            }
        }
    }
}

/** A single onboarding step row inside the seller card. */
@Composable
private fun SellerStep(
    index: Int,
    done: Boolean,
    title: String,
    description: String,
) {
    val bg = if (done) SellerGreenBg else MaterialTheme.colorScheme.primaryContainer
    val border = if (done) SellerGreenBorder else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SellerGreen,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (done) SellerGreen else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (done) {
                    OnPastelSubtext
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                },
            )
        }
    }
}

/** Amber fee-disclosure box: Mochi's platform cut + a vague Stripe pointer. */
@Composable
private fun FeesBox(platformFeePercent: Double) {
    val feeText = if (platformFeePercent % 1.0 == 0.0) {
        platformFeePercent.toInt().toString()
    } else {
        platformFeePercent.toString()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FeesBg)
            .border(1.dp, FeesBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.market_account_seller_fees_heading),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = OnPastelText,
        )
        FeeBullet(stringResource(R.string.market_account_seller_fees_mochi, feeText))
        FeeBullet(stringResource(R.string.market_account_seller_fees_stripe))
    }
}

@Composable
private fun FeeBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = OnPastelText,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = OnPastelText,
        )
    }
}
