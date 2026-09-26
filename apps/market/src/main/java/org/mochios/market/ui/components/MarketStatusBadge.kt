// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusBadgeSize
import org.mochios.android.ui.components.StatusTone
import org.mochios.android.ui.components.defaultIcon
import org.mochios.market.R
import org.mochios.market.model.BidStatus

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
    val tone = statusTone(key)
    StatusBadge(
        label = localised ?: knownStatusLabel(key) ?: key,
        tone = tone,
        icon = statusIcon(key) ?: tone.defaultIcon,
        modifier = modifier,
        size = size,
    )
}

/**
 * Badge for one of the buyer's bids. A bid's `active` means the auction is
 * still running, not a settled state, so bids keep their own tones.
 *
 * @param status the bid's status; null renders an empty neutral badge.
 */
@Composable
fun BidStatusBadge(status: BidStatus?, modifier: Modifier = Modifier) {
    val (label, tone) = when (status) {
        BidStatus.ACTIVE -> stringResource(R.string.market_bids_tab_active) to StatusTone.Waiting
        BidStatus.WON -> stringResource(R.string.market_bids_tab_won) to StatusTone.Positive
        BidStatus.PURCHASED ->
            stringResource(R.string.market_purchase_purchased) to StatusTone.Positive
        BidStatus.OUTBID -> stringResource(R.string.market_bids_tab_outbid) to StatusTone.Neutral
        BidStatus.LOST -> stringResource(R.string.market_bids_tab_lost) to StatusTone.Neutral
        BidStatus.EXPIRED, null -> status?.name?.lowercase().orEmpty() to StatusTone.Neutral
    }
    StatusBadge(
        label = label,
        tone = tone,
        icon = if (status == BidStatus.ACTIVE) Icons.Outlined.Gavel else tone.defaultIcon,
        modifier = modifier,
    )
}

internal fun statusTone(key: String): StatusTone = when (key) {
    "active", "paid", "shipped", "delivered", "completed" -> StatusTone.Positive
    "pending", "paused" -> StatusTone.Waiting
    "disputed", "cancelled", "past_due" -> StatusTone.Negative
    else -> StatusTone.Neutral
}

private fun statusIcon(key: String): ImageVector? = when (key) {
    "refunded" -> Icons.AutoMirrored.Outlined.Undo
    "disputed" -> Icons.Outlined.ReportProblem
    "past_due" -> Icons.Outlined.EventBusy
    else -> null
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
