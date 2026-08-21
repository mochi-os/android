// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.market.R

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    localised: String? = null,
) {
    val key = status.trim().lowercase()
    val tone = statusTone(key)
    val label = localised ?: knownStatusLabel(key) ?: key

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = tone.foreground,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

internal data class StatusTone(val background: Color, val foreground: Color)

@Composable
internal fun statusTone(key: String): StatusTone {
    val scheme = MaterialTheme.colorScheme
    return when (key) {
        "paid", "shipped", "active" ->
            StatusTone(scheme.primaryContainer, scheme.onPrimaryContainer)
        "delivered", "completed" ->
            StatusTone(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        "disputed", "cancelled", "past_due" ->
            StatusTone(scheme.errorContainer, scheme.onErrorContainer)
        else -> // pending, refunded, paused, unknown
            StatusTone(scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
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
