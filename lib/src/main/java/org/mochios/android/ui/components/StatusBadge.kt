// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The colour pair a [StatusBadge] wears: a pale [background] under dark
 * [foreground] text.
 */
data class StatusTone(val background: Color, val foreground: Color) {

    companion object {

        /** Settled, healthy states — active, paid, approved. */
        val Positive = StatusTone(Color(0xFFE2FBE8), Color(0xFF2B6536))

        /** Waiting on someone — pending, paused, under review. */
        val Waiting = StatusTone(Color(0xFFFBF3E2), Color(0xFF7A5A2B))

        /** Stopped or rejected — removed, cancelled, disputed. */
        val Negative = StatusTone(Color(0xFFFBE2E2), Color(0xFF7A2B2B))

        /** Anything with no state of its own to report. */
        val Neutral = StatusTone(Color(0xFFEDEDED), Color(0xFF555555))
    }
}

/** How much room a [StatusBadge] takes: [Compact] in a list, [Regular] on a detail. */
enum class StatusBadgeSize { Compact, Regular }

/**
 * Pill-shaped status badge: a pale fill under dark text, with an optional
 * leading icon. Mirrors the market listing chips, whose palette these tones are
 * sampled from, so a status reads the same wherever it appears.
 *
 * The tones are fixed rather than theme roles — a status colour carries meaning
 * (amber is waiting, red is stopped) that a server-driven scheme would override.
 * They are light-theme values by design, matching the market chips.
 */
@Composable
fun StatusBadge(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: StatusBadgeSize = StatusBadgeSize.Compact,
) {
    val compact = size == StatusBadgeSize.Compact
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.background)
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone.foreground,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = tone.foreground,
        )
    }
}
