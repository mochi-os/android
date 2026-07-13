// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Stone colour used by [GameHeaderStoneDot] in chess/go. */
enum class StoneColor { WHITE, BLACK }

/**
 * Strip variant of the cross-app GameHeader. Mirrors the
 * `variant='strip'` mode of `lib/web/src/components/layout/game-header.tsx`:
 * a single horizontal row with optional avatar + title + stats + actions,
 * a small muted status line beneath, and an optional banner slot for
 * draw-offer-style prompts.
 *
 * On phones we squash to a single line; the stats area pushes against the
 * title which is allowed to ellipsise. Callers can render multiple
 * [GameHeaderStat] entries inside the `stats` slot.
 *
 * @param title              Opponent name (or game title for solo modes).
 * @param status             Short status text rendered as a muted second
 *                           line ("Black to move", "You won", "Draw"). Pass
 *                           an empty string to omit the line.
 * @param myTurn             When non-null, renders a coloured dot before
 *                           the status — green for true, dim for false. Use
 *                           null when the game is over.
 * @param opponentFingerprint Entity ID for the opponent avatar. When null
 *                            no avatar is shown.
 * @param opponentName       Display name for the avatar's initials fallback.
 * @param stats              Slot for [GameHeaderStat] entries (e.g. "You: ♟",
 *                           "Captures: 4", "Score: 23"). Each stat appears
 *                           as a small pill.
 * @param actions            Slot for trailing IconButtons (chat toggle,
 *                           overflow menu).
 * @param banner             Slot for a full-width prompt below the status —
 *                           e.g. draw offer / rematch invite.
 */
@Composable
fun GameHeader(
    title: String,
    status: String,
    myTurn: Boolean? = null,
    opponentFingerprint: String? = null,
    opponentName: String? = null,
    stats: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    banner: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (opponentFingerprint != null) {
                EntityAvatar(
                    name = opponentName ?: title,
                    seed = opponentFingerprint,
                    size = 28.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = stats,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

        if (status.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (myTurn != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (myTurn) {
                                    Color(0xFF10B981) // emerald-500
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                }
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (banner != null) {
            Spacer(Modifier.height(6.dp))
            banner()
        }
    }
}

/**
 * Small rounded pill stat used inside [GameHeader]'s `stats` slot. Renders
 * an optional icon, label, and value side-by-side. Mirrors `GameHeaderStat`
 * from `lib/web/src/components/layout/game-header-stats.tsx`.
 *
 * @param isHighlighted Use a primary-tinted background — typically the
 *                      colour that's "to move".
 * @param isMe          Underline the label so the user can see at a glance
 *                      which side is them.
 * @param srLabel       Screen-reader-only label attached as a content
 *                      description. Used when the icon alone carries
 *                      meaning.
 */
@Composable
fun RowScope.GameHeaderStat(
    label: String,
    value: String? = null,
    icon: (@Composable () -> Unit)? = null,
    srLabel: String? = null,
    isHighlighted: Boolean = false,
    isMe: Boolean = false,
) {
    val bg = if (isHighlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val base = Modifier
        .clip(RoundedCornerShape(percent = 50))
        .background(bg)
        .border(width = 1.dp, color = border, shape = RoundedCornerShape(percent = 50))
        .padding(horizontal = 8.dp, vertical = 3.dp)
        .widthIn(max = 140.dp)
    val withSemantics = if (srLabel != null) {
        base.then(Modifier.semantics { contentDescription = srLabel })
    } else {
        base
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = withSemantics,
    ) {
        if (icon != null) icon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                textDecoration = if (isMe) TextDecoration.Underline else TextDecoration.None,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 8 dp filled circle for showing "white" vs "black" stone/piece colour.
 * The white dot gets a darker border so it remains visible against light
 * backgrounds; the black dot gets a faint border so it stands out in dark
 * theme.
 */
@Composable
fun GameHeaderStoneDot(color: StoneColor) {
    val fill: Color
    val ring: Color
    when (color) {
        StoneColor.BLACK -> {
            fill = Color(0xFF1F2937) // gray-800
            ring = Color(0xFF374151) // gray-700
        }
        StoneColor.WHITE -> {
            fill = Color(0xFFF3F4F6) // gray-100
            ring = Color(0xFF9CA3AF) // gray-400
        }
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(fill)
            .border(width = 1.dp, color = ring, shape = CircleShape),
    )
}
