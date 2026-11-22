// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * The app's floating action button: the one thing a screen adds, reachable from
 * anywhere on it.
 *
 * Drop-in for [androidx.compose.material3.FloatingActionButton] — same
 * parameter names and order — with [tone] added ahead of the colour parameters,
 * so a screen names the role rather than picking a container colour by hand.
 * Pass [containerColor] or [contentColor] only for something no tone covers;
 * they win over [tone] when given.
 */
@Composable
fun MochiFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: MochiButtonTone = MochiButtonTone.Primary,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor ?: fabContainerColor(tone),
        contentColor = contentColor ?: fabContentColor(tone),
        elevation = elevation,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun fabContainerColor(tone: MochiButtonTone): Color = when (tone) {
    MochiButtonTone.Primary -> MaterialTheme.colorScheme.primaryContainer
    MochiButtonTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
    MochiButtonTone.Destructive -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun fabContentColor(tone: MochiButtonTone): Color = when (tone) {
    MochiButtonTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    MochiButtonTone.Neutral -> MaterialTheme.colorScheme.onSurface
    MochiButtonTone.Destructive -> MaterialTheme.colorScheme.onErrorContainer
}
