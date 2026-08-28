// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage

@Composable
fun ErrorState(
    error: MochiError,
    onRetry: (() -> Unit)? = null
) {
    val icon = when (error) {
        is MochiError.NetworkError -> Icons.Default.SignalWifiOff
        is MochiError.AuthError -> Icons.Default.Lock
        is MochiError.ForbiddenError -> Icons.Default.Lock
        else -> Icons.Default.ErrorOutline
    }
    ErrorState(icon = icon, message = error.userMessage(), onRetry = onRetry)
}

/**
 * The same error state for a screen that holds its failure as a message rather
 * than a [MochiError] - a caught exception's text, or a string the server sent.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    ErrorState(icon = Icons.Default.ErrorOutline, message = message, onRetry = onRetry)
}

@Composable
private fun ErrorState(
    icon: ImageVector,
    message: String,
    onRetry: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))
            MochiButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

/**
 * A compact error for one section of a screen - a friends picker that failed to
 * load inside a create form, a search result area, a timeline panel. The
 * full-size [ErrorState] centres itself in the whole viewport and is wrong
 * there; this stays inline and takes only the height it needs.
 */
@Composable
fun InlineErrorState(
    error: MochiError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    InlineErrorState(message = error.userMessage(), onRetry = onRetry, modifier = modifier)
}

@Composable
fun InlineErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRetry != null) {
            MochiTextButton(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}
