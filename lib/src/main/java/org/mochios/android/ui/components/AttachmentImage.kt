// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.android.R
import org.mochios.android.util.AttachmentFailure
import org.mochios.android.util.attachmentFailure
import org.mochios.android.util.attachmentStatus

/**
 * An attachment image that says WHY it failed. An image's error state carries
 * no reason on its own, so this reads the status Coil's failure holds and
 * renders the server's answer: "Unavailable" for a source that cannot be
 * reached right now (the server retries after a backoff, so tapping the tile
 * retries the load), "Not found" for bytes that are gone. One broken glyph
 * for both was the client contradicting a server that tells them apart.
 */
@Composable
fun AttachmentImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var failure by remember(model) { mutableStateOf<AttachmentFailure?>(null) }
    var attempt by remember(model) { mutableIntStateOf(0) }

    val current = failure
    if (current != null) {
        // The retry sits on a CHILD filling the tile, not chained onto the
        // caller's modifier: a child's click handler beats the parent chain's
        // by defined nesting semantics, where two clickables on one chain
        // (the caller often passes its own) have no reliable winner. Retry
        // rather than showing a viewer onto the same failure; for an
        // unavailable source a later attempt may simply work.
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    failure = null
                    attempt++
                },
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (current == AttachmentFailure.UNAVAILABLE) {
                    Icons.Default.CloudOff
                } else {
                    Icons.Default.ImageNotSupported
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(
                    if (current == AttachmentFailure.UNAVAILABLE) {
                        R.string.common_unavailable
                    } else {
                        R.string.common_not_found
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
        return
    }

    // key(attempt) rebuilds the painter on retry; recomposing the same model
    // would replay the remembered failure instead of refetching.
    key(attempt) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onError = { state ->
                failure = attachmentFailure(attachmentStatus(state.result.throwable))
            },
            modifier = modifier,
        )
    }
}
