// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.mochios.android.R

/**
 * The refresh button of a timeline's top bar, badged with the [count] of posts
 * that have arrived since the list was loaded: they wait behind it rather than
 * being injected into the list while the user is reading. [label] names the
 * count for a screen reader ("3 new posts").
 */
@Composable
fun RefreshButton(
    count: Int,
    label: String,
    onClick: () -> Unit,
) {
    Box {
        MochiIconButton(
            onClick = onClick,
            modifier = if (count > 0) Modifier.semantics { stateDescription = label } else Modifier,
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = stringResource(R.string.common_refresh),
            )
        }
        // Primary where the bell beside it is the error colour, so the two
        // counts do not read as one kind. The button speaks the count itself.
        CountBadge(
            count = count,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics { hideFromAccessibility() },
        )
    }
}
