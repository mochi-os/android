// Copyright © 2026 Mochisoft OÜ
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
 * Shown when a detail screen's entity 404s - typically a stale deep link or
 * home-screen shortcut. Caller supplies the localised title.
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
