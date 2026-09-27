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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The colours a [StatusBadge] wears: a pale [background] under dark
 * [foreground] text on a light theme, and a deep [darkBackground] under light
 * [darkForeground] text on a dark one.
 */
data class StatusTone(
    val background: Color,
    val foreground: Color,
    val darkBackground: Color,
    val darkForeground: Color,
) {

    companion object {

        /** Settled, healthy states — active, paid, approved. */
        val Positive = StatusTone(
            Color(0xFFE2FBE8),
            Color(0xFF2B6536),
            Color(0xFF1F3A26),
            Color(0xFFB8EBC4),
        )

        /** Waiting on someone — pending, paused, under review. */
        val Waiting = StatusTone(
            Color(0xFFFBF3E2),
            Color(0xFF7A5A2B),
            Color(0xFF3D3020),
            Color(0xFFF0D9A8),
        )

        /** Stopped or rejected — removed, cancelled, disputed. */
        val Negative = StatusTone(
            Color(0xFFFBE2E2),
            Color(0xFF7A2B2B),
            Color(0xFF3F2222),
            Color(0xFFF2B8B8),
        )

        /** Anything with no state of its own to report. */
        val Neutral = StatusTone(
            Color(0xFFEDEDED),
            Color(0xFF555555),
            Color(0xFF333333),
            Color(0xFFCCCCCC),
        )

        /** Not a state but a rank worth singling out, such as an admin role. */
        val Accent = StatusTone(
            Color(0xFFECE2FB),
            Color(0xFF4F2B7A),
            Color(0xFF30264A),
            Color(0xFFD6C4F5),
        )
    }
}

/**
 * The icon a [StatusBadge] of this tone shows when its caller names none, so
 * every app's badge for the same kind of state carries the same glyph.
 */
val StatusTone.defaultIcon: ImageVector?
    get() = when (this) {
        StatusTone.Positive -> Icons.Outlined.CheckCircle
        StatusTone.Waiting -> Icons.Outlined.Schedule
        StatusTone.Negative -> Icons.Outlined.Block
        else -> null
    }

/** How much room a [StatusBadge] takes: [Compact] in a list, [Regular] on a detail. */
enum class StatusBadgeSize { Compact, Regular }

/**
 * Pill-shaped status badge with a leading icon, sampled from the market listing
 * chips; the icon defaults to the tone's [defaultIcon] and `null` drops it.
 * The tones are fixed values, not theme roles: a status colour carries meaning
 * a server-driven scheme would override. The dark pair is picked when the
 * theme's surface is dark.
 */
@Composable
fun StatusBadge(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = tone.defaultIcon,
    size: StatusBadgeSize = StatusBadgeSize.Compact,
) {
    val compact = size == StatusBadgeSize.Compact
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val background = if (dark) tone.darkBackground else tone.background
    val foreground = if (dark) tone.darkForeground else tone.foreground
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
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
                tint = foreground,
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
            color = foreground,
        )
    }
}
