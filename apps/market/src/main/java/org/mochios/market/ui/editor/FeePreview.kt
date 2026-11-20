// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.PricingModel
import org.mochios.market.ui.components.formatPercent

/**
 * Mochi's fee on a sale at [price] minor units, as the Comptroller takes it:
 * [percent] of the price, rounded down.
 */
internal fun mochiFee(price: Long, percent: Double): Long =
    kotlin.math.floor(price * percent / 100.0).toLong()

/**
 * What Mochi takes from a sale at [price] and what the seller keeps, from the
 * platform [percent] the editor already holds. An auction's price is only its
 * starting bid, so for one it says only that the percentage applies to the
 * winning bid; with no price yet it gives the percentage alone. Stripe's own
 * fees are never quoted.
 */
@Composable
internal fun FeePreview(
    price: Long,
    currency: Currency,
    pricing: PricingModel,
    percent: Double,
    modifier: Modifier = Modifier,
) {
    val shown = formatPercent(LocalFormat.current, percent)
    if (pricing == PricingModel.AUCTION || price <= 0L) {
        Text(
            text = if (pricing == PricingModel.AUCTION) {
                stringResource(R.string.market_fee_preview_auction, shown)
            } else {
                stringResource(R.string.market_fee_preview_sale, shown)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val fee = mochiFee(price, percent)
    val payout = (price - fee).coerceAtLeast(0L)
    val payoutLabel = when (pricing) {
        PricingModel.PWYW -> R.string.market_fee_preview_payout_minimum
        PricingModel.SUBSCRIPTION -> R.string.market_fee_preview_payout_period
        else -> R.string.market_fee_preview_payout
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeeRow(label = stringResource(R.string.market_fee_preview_item), value = formatPrice(price, currency))
        FeeRow(
            label = stringResource(R.string.market_fee_preview_fee, shown),
            value = "−" + formatPrice(fee, currency),
            muted = true,
        )
        HorizontalDivider()
        FeeRow(label = stringResource(payoutLabel), value = formatPrice(payout, currency), strong = true)
    }
}

@Composable
private fun FeeRow(label: String, value: String, muted: Boolean = false, strong: Boolean = false) {
    val colour = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val weight = if (strong) FontWeight.SemiBold else null
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
            fontWeight = weight,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = colour, fontWeight = weight)
    }
}
