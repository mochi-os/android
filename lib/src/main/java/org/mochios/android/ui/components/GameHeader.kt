// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
 * Game identity for a detail screen's top bar: the opponent's avatar and the
 * game's title on one line. The status line lives in the content area, under
 * the bar — see [GameStatusLine].
 */
@Composable
fun GameTopBarTitle(
    title: String,
    opponentFingerprint: String? = null,
    opponentName: String? = null,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (opponentFingerprint != null) {
            EntityAvatar(
                name = opponentName ?: title,
                src = avatarUrl,
                seed = opponentFingerprint,
                size = 32.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The strip under a detail screen's top bar: the [stats] chips on one line
 * that scrolls sideways across the full width of the pane, with whose move it is on the line below. A long
 * player name or a four-player game used to squeeze both onto one line,
 * leaving the status broken across two characters and the last chip cut to a
 * dot. [myTurn] draws a coloured dot before the status; pass null once the
 * game ends.
 */
@Composable
fun GameStatusBar(
    status: String,
    myTurn: Boolean? = null,
    modifier: Modifier = Modifier,
    stats: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The scroller spans the pane so the chips run to both edges; the
        // gutter is padding on its content, which scrolls with it. It matches
        // the top bar's 16 dp gutter at rest.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = STATUS_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = stats,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = STATUS_GUTTER),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (myTurn != null) {
                val dotColor = if (myTurn) {
                    Color(0xFF10B981) // emerald-500
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One stat in [GameStatusBar], drawn as a Material assist chip so it stays
 * legible at arm's length. [isHighlighted] tints the side to move, [isMe]
 * underlines the viewer's own, and [srLabel] carries the meaning when the icon
 * alone does. The chip is a readout, not a control: its click is a no-op.
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
    val container = if (isHighlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val chipModifier = if (srLabel != null) {
        Modifier.semantics { contentDescription = srLabel }
    } else {
        Modifier
    }
    AssistChip(
        onClick = {},
        modifier = chipModifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        leadingIcon = icon,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.widthIn(max = 160.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        textDecoration = if (isMe) {
                            TextDecoration.Underline
                        } else {
                            TextDecoration.None
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

/**
 * 8 dp filled circle for white vs black stones. Each dot carries a ring so it
 * stays visible against a light or dark background.
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

/** Gutter on the status strip's content, matching the top bar's. */
private val STATUS_GUTTER = 16.dp
