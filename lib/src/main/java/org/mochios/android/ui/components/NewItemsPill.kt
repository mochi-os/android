// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A floating "N new posts" pill shown at the top of a timeline. Real-time posts
 * are queued behind it (see the per-screen view models) rather than injected
 * into the list while the user is reading; tapping the pill reveals them and
 * scrolls to the top. Mirrors the web client's shared NewItemsPill.
 *
 * Render this as an overlay anchored to the top of the list container (e.g. in a
 * Box above a LazyColumn/Pager), aligned [Alignment.TopCenter]. Hidden when
 * [count] is zero.
 */
@Composable
fun NewItemsPill(
    count: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = count > 0,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValuesPill,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(label)
            }
        }
    }
}

private val PaddingValuesPill =
    androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)
