// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.PricingModel
import org.mochios.market.repository.MarketRepository

/**
 * Read-only preview of how Mochi will break down a sale at the given
 * [price]: item / Mochi fee / processor (Stripe) fee / your payout.
 *
 * Backed by the seller's `account/fees` percentage from
 * [MarketRepository.getFees]. The processor fee is intentionally
 * displayed as a delta to the payout rather than a separate Stripe
 * percentage — per `feedback_dont_quote_third_party_rates` we don't
 * embed Stripe's rates here.
 *
 * Debounces re-fetches by 300ms so a slider/text-field can drive the
 * input without spamming the server.
 *
 * @param price        Current price in minor units.
 * @param currency     Currency to format the breakdown in.
 * @param pricingModel Pricing model (subscription vs fixed); reserved
 *                     for future per-model fee rules and accepted now
 *                     so call sites don't need to change later.
 */
@Composable
fun FeePreview(
    price: Long,
    currency: Currency,
    pricingModel: PricingModel,
    repository: MarketRepository,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE") val unused = pricingModel
    var feePercent by remember { mutableStateOf<Double?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(price, currency) {
        delay(300)
        failed = false
        try {
            feePercent = repository.getFees().platform
        } catch (_: Exception) {
            failed = true
            feePercent = null
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val percent = feePercent
            when {
                failed -> {
                    Text(
                        text = stringResource(R.string.market_fee_preview_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                percent == null -> {
                    Text(
                        text = stringResource(R.string.market_fee_preview_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val mochiFee = kotlin.math.round(price * percent / 100.0).toLong()
                    val payout = (price - mochiFee).coerceAtLeast(0L)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.market_fee_preview_item),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatPrice(price, currency),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.market_fee_preview_mochi_fee),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "−${formatPrice(mochiFee, currency)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.market_fee_preview_payout),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = formatPrice(payout, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
