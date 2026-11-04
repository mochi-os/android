// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * A forum screen's title: its name, and under it a community's handle in full
 * (!name@host), as two communities can share a name. A forum of ours gives
 * its name alone.
 */
@Composable
internal fun ForumTitle(name: String, handle: String) {
    Column {
        Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (handle.isNotEmpty()) {
            Text(
                text = handle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A post's forum as a list names it: the name, and a community's server muted beside it. */
@Composable
internal fun ForumLabel(name: String, server: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (server.isNotEmpty()) {
            Text(
                text = " $server",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
