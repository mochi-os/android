// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mochios.market.R
import org.mochios.market.repository.MarketRepository

/**
 * Stripe Connect onboarding banner. Shown when the seller's Stripe
 * account does not yet have both `charges_enabled` and
 * `payouts_enabled`.
 *
 * Tapping "Continue setup" hits
 * [MarketRepository.stripeOnboarding] for a one-time onboarding URL and
 * opens it in a Custom Tab (top-level navigation — the iframe shell
 * sandbox can't host Stripe). Tapping "Check status" re-fetches
 * [MarketRepository.stripeStatus] and auto-hides the banner once both
 * flags flip to true.
 *
 * @param returnUrl Where Stripe should redirect after onboarding. The
 *                  caller chooses a per-app deep link or the market
 *                  selling-settings URL.
 */
@Composable
fun StripeOnboardingBanner(
    repository: MarketRepository,
    returnUrl: String,
    modifier: Modifier = Modifier,
    initialCharges: Boolean = false,
    initialPayouts: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var charges by remember { mutableStateOf(initialCharges) }
    var payouts by remember { mutableStateOf(initialPayouts) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val failedToOpen = stringResource(R.string.market_stripe_onboarding_failed)

    LaunchedEffect(Unit) {
        // Refresh from server once on first composition so we don't show
        // the banner for sellers who completed onboarding off-device.
        try {
            val status = repository.stripeStatus()
            charges = status.chargesEnabled
            payouts = status.payoutsEnabled
        } catch (_: Exception) {
            // Keep the banner visible on error — better to over-show than under.
        }
    }

    if (charges && payouts) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.market_stripe_onboarding_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.market_stripe_onboarding_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val resp = repository.stripeOnboarding(returnUrl)
                                if (resp.url.isNotBlank()) {
                                    val intent = CustomTabsIntent.Builder().build()
                                    intent.launchUrl(context, Uri.parse(resp.url))
                                } else {
                                    error = failedToOpen
                                }
                            } catch (_: Exception) {
                                error = failedToOpen
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = if (busy)
                            stringResource(R.string.market_stripe_onboarding_opening)
                        else
                            stringResource(R.string.market_stripe_onboarding_action),
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val status = repository.stripeStatus()
                                charges = status.chargesEnabled
                                payouts = status.payoutsEnabled
                            } catch (_: Exception) {
                                // Leave flags alone; the banner stays visible.
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.market_stripe_onboarding_check))
                }
            }
        }
    }
}
