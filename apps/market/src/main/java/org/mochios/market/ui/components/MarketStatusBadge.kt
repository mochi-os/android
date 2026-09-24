// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusBadgeSize
import org.mochios.android.ui.components.StatusTone
import org.mochios.market.R

/**
 * Badge for a market order, listing or subscription status, drawn with the
 * shared status badge so a state reads the same here as in every other app.
 *
 * @param status the wire status, matched case-insensitively.
 * @param localised a label to show instead of the known one for [status].
 * @param size [StatusBadgeSize.Regular] on a detail screen, compact in a list.
 */
@Composable
fun MarketStatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    localised: String? = null,
    size: StatusBadgeSize = StatusBadgeSize.Compact,
) {
    val key = status.trim().lowercase()
    StatusBadge(
        label = localised ?: knownStatusLabel(key) ?: key,
        tone = statusTone(key),
        modifier = modifier,
        size = size,
    )
}

internal fun statusTone(key: String): StatusTone = when (key) {
    "active", "paid", "shipped", "delivered", "completed" -> StatusTone.Positive
    "pending", "paused", "refunded" -> StatusTone.Waiting
    "disputed", "cancelled", "past_due" -> StatusTone.Negative
    else -> StatusTone.Neutral
}

@Composable
internal fun knownStatusLabel(key: String): String? = when (key) {
    "pending" -> stringResource(R.string.market_status_pending)
    "paid" -> stringResource(R.string.market_status_paid)
    "shipped" -> stringResource(R.string.market_status_shipped)
    "delivered" -> stringResource(R.string.market_status_delivered)
    "completed" -> stringResource(R.string.market_status_completed)
    "disputed" -> stringResource(R.string.market_status_disputed)
    "refunded" -> stringResource(R.string.market_status_refunded)
    "cancelled" -> stringResource(R.string.market_status_cancelled)
    "active" -> stringResource(R.string.market_status_active)
    "paused" -> stringResource(R.string.market_status_paused)
    "past_due" -> stringResource(R.string.market_status_past_due)
    else -> null
}
