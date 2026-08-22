// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiCard
import org.mochios.market.R

/**
 * Quotes the Mochi platform fee only - never embed Stripe's rates, they go
 * stale.
 */
@Composable
fun FeeDisclosure(
    platformFeePercent: Double,
    modifier: Modifier = Modifier,
) {
    MochiCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.market_fee_disclosure_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = stringResource(
                    R.string.market_fee_disclosure_body,
                    formatPercent(LocalFormat.current, platformFeePercent),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatPercent(format: Format, value: Double): String {
    val whole = value.toLong()
    return if (kotlin.math.abs(value - whole) < 0.005) {
        format.formatNumber(whole, 0)
    } else {
        format.formatNumber(value, 1)
    }
}
