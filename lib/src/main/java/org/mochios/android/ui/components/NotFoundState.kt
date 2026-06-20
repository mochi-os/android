// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.R

/**
 * Rendered when a detail screen tries to load an entity (a forum, a chat,
 * a feed, a project, …) and gets a 404 from the server. The user typically
 * arrived here from a stale notification deep-link or home-screen shortcut.
 *
 * Caller supplies the localised title (e.g. "Forum not found"). The icon and
 * "Back" action are shared so the four apps render identically.
 */
@Composable
fun NotFoundState(
    title: String,
    onBack: () -> Unit,
) {
    EmptyState(
        icon = Icons.Default.SearchOff,
        title = title,
        action = {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.common_back))
            }
        },
    )
}
