// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The shared empty state: a tinted circular badge over a title, an optional
 * subtitle, and an optional action. [modifier] is applied before the column
 * fills, so padding insets the fill rather than being swallowed by it.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    action: (@Composable () -> Unit)? = null
) {
    EmptyStateLayout(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        action = action
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * [EmptyState] drawn from a raster [icon] rather than a vector — an app's own
 * launcher glyph, say. The glyph is tinted like the vector variant, so a
 * single-colour icon still follows the theme.
 */
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    action: (@Composable () -> Unit)? = null
) {
    EmptyStateLayout(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        action = action
    ) {
        // Wider than the 48dp vector: a launcher glyph is drawn inside its own
        // adaptive-icon margin, so it reads smaller at the same size.
        Image(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun EmptyStateLayout(
    title: String,
    subtitle: String?,
    modifier: Modifier,
    verticalArrangement: Arrangement.Vertical,
    action: (@Composable () -> Unit)?,
    badge: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement
    ) {
        // Icon on a tinted disc, matching the web empty states: the disc is the
        // primary colour held well back, so the icon still carries the accent.
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            badge()
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        if (action != null) {
            Spacer(modifier = Modifier.height(24.dp))
            action()
        }
    }
}
