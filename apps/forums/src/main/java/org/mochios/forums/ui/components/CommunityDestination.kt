// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.forums.R
import org.mochios.forums.model.Destination

/**
 * Where a post or comment written in a community goes, and as which account:
 * the community's own server always has it, so there is nothing to choose.
 * Nothing for a forum of ours.
 */
@Composable
fun CommunityDestination(destination: Destination?, modifier: Modifier = Modifier) {
    if (destination == null || destination.community.isEmpty() || destination.account.isEmpty()) return
    Text(
        stringResource(R.string.forums_community_destination, destination.community, destination.account),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A comment bar's banner with the community's line above whatever else it shows. */
fun destinationBanner(destination: Destination?, banner: (@Composable () -> Unit)?): (@Composable () -> Unit)? {
    if (destination == null || destination.community.isEmpty() || destination.account.isEmpty()) return banner
    return {
        Column {
            CommunityDestination(destination, Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
            banner?.invoke()
        }
    }
}
