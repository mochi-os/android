// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mochios.android.R
import org.mochios.android.ui.theme.LocalSpacing
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
 * While it loads, and behind the reason when it fails, the grey placeholder
 * [MochiImage] draws stands in, outlined by [shape].
 */
@Composable
fun AttachmentImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RectangleShape,
    // Loaded in place of [model] if [model] fails, before any failure shows.
    fallback: Any? = null,
) {
    var source by remember(model, fallback) { mutableStateOf(model) }
    var loaded by remember(model) { mutableStateOf(false) }
    val load = rememberAttachmentLoad(model)

    val current = load.failure
    if (current != null) {
        // The retry sits on a CHILD filling the tile, not chained onto the
        // caller's modifier: a child's click handler beats the parent chain's
        // by defined nesting semantics, where two clickables on one chain
        // (the caller often passes its own) have no reliable winner. Retry
        // rather than showing a viewer onto the same failure; for an
        // unavailable source a later attempt may simply work.
        Box(modifier = modifier) {
            ImagePlaceholder(shape = shape, modifier = Modifier.matchParentSize())
            AttachmentFailureNotice(
                failure = current,
                onRetry = load::retry,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.matchParentSize(),
            )
        }
        return
    }

    // The placeholder rides on the image's own modifier rather than a box
    // around it: some callers fix only the height and let the width follow
    // the image, which a wrapping box would collapse.
    key(load.attempt) {
        AsyncImage(
            model = source,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onSuccess = { loaded = true },
            onError = { state ->
                if (fallback != null && source != fallback) {
                    source = fallback
                } else {
                    load.fail(state.result.throwable)
                }
            },
            modifier = modifier.then(if (loaded) Modifier else Modifier.imagePlaceholder(shape)),
        )
    }
}

/**
 * A load of an attachment's bytes that can fail with the server's reason and
 * be tried again. The tile and the lightbox both hold one, so the two cannot
 * drift apart on what a failure says or how it is retried. Key the image on
 * [attempt]: a retry rebuilds the painter, where recomposing the same model
 * would replay the remembered failure instead of fetching again.
 */
@Stable
internal class AttachmentLoad {
    var failure by mutableStateOf<AttachmentFailure?>(null)
        private set
    var attempt by mutableIntStateOf(0)
        private set

    /** The load failed with [throwable], classed by the status it carries. */
    fun fail(throwable: Throwable?) {
        failure = attachmentFailure(attachmentStatus(throwable))
    }

    fun retry() {
        failure = null
        attempt++
    }
}

/** An [AttachmentLoad] for [model], begun again when the model changes. */
@Composable
internal fun rememberAttachmentLoad(model: Any?): AttachmentLoad = remember(model) { AttachmentLoad() }

/**
 * Why an attachment failed, filling [modifier]'s bounds, in [color]: "Unavailable"
 * for a source that cannot be reached now, "Not found" for bytes that are gone.
 * A tap retries. [large] draws it for a page of its own rather than a tile.
 */
@Composable
internal fun AttachmentFailureNotice(
    failure: AttachmentFailure,
    onRetry: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Column(
        modifier = modifier.clickable(onClick = onRetry),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.gap(if (large) 8.dp else 4.dp), Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (failure == AttachmentFailure.UNAVAILABLE) {
                Icons.Default.CloudOff
            } else {
                Icons.Default.ImageNotSupported
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(if (large) 64.dp else 24.dp),
        )
        Text(
            text = stringResource(
                if (failure == AttachmentFailure.UNAVAILABLE) {
                    R.string.common_unavailable
                } else {
                    R.string.common_not_found
                }
            ),
            style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
