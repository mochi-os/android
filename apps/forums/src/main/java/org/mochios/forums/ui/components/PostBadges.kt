// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusBadgeSize
import org.mochios.android.ui.components.StatusTone
import org.mochios.forums.R

/** One badge to render: what it says, how it reads, and its glyph. */
private data class PostBadge(
    val label: String,
    val tone: StatusTone,
    val icon: ImageVector,
)

/**
 * The post's moderation [status] as a badge. Pinned and locked stay as title
 * icons — they describe the post, where a badge says the post is not live.
 *
 * An ordinary live post has no status and renders nothing at all, so callers can
 * place this unconditionally without leaving a gap behind.
 */
@Composable
internal fun PostBadges(
    status: String,
    modifier: Modifier = Modifier,
    size: StatusBadgeSize = StatusBadgeSize.Compact,
) {
    val badges = buildList {
        when (status) {
            "pending" -> add(
                PostBadge(
                    label = stringResource(R.string.forums_post_pending),
                    tone = StatusTone.Waiting,
                    icon = Icons.Outlined.Schedule,
                )
            )

            "removed" -> add(
                PostBadge(
                    label = stringResource(R.string.forums_post_removed),
                    tone = StatusTone.Negative,
                    icon = Icons.Outlined.Block,
                )
            )
        }
    }
    if (badges.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badges.forEach { badge ->
            StatusBadge(
                label = badge.label,
                tone = badge.tone,
                icon = badge.icon,
                size = size,
            )
        }
    }
}
