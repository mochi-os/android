// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * A network image with its in-between states drawn, so a slow or missing
 * image never leaves blank space: a grey box with a thin edge while it loads
 * and when it fails, a spinner while loading, and a broken-image glyph that
 * retries on tap after a failure. Below [COMPACT_SIZE] there is no room for
 * the spinner or glyph, and the grey box alone stands in.
 *
 * The caller fixes the size; an image has no dimensions until it arrives.
 *
 * @param model What Coil loads: a URL, a server-relative path or a request.
 * @param contentDescription The image's description for accessibility.
 * @param modifier Size, clip and the like, as for an [AsyncImage].
 * @param alignment Where the image sits when it does not fill the box.
 * @param contentScale How the image is scaled into the box.
 * @param shape The placeholder's outline, matching the caller's own clip.
 * @param onClick Called on a tap once the image has loaded; null for none.
 */
@Composable
fun MochiImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RectangleShape,
    onClick: (() -> Unit)? = null,
) {
    var state by remember(model) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    // Recreating the painter under a new key is what makes Coil fetch again;
    // recomposing the same model would replay the remembered failure.
    var retry by remember(model) { mutableIntStateOf(0) }
    val loaded = state is AsyncImagePainter.State.Success
    BoxWithConstraints(
        modifier = modifier.then(
            if (onClick != null && loaded) Modifier.clickable { onClick() } else Modifier
        ),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < COMPACT_SIZE || maxHeight < COMPACT_SIZE
        if (!loaded) {
            ImagePlaceholder(shape = shape, modifier = Modifier.matchParentSize())
        }
        if (state is AsyncImagePainter.State.Error) {
            if (!compact) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            state = AsyncImagePainter.State.Empty
                            retry++
                        },
                )
            }
        } else {
            key(retry) {
                AsyncImage(
                    model = model,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    alignment = alignment,
                    onState = { next -> state = next },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!loaded && !compact) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/**
 * The box an image shows before it arrives or after it fails: the theme's
 * muted surface on its faint outline, as the web's bg-muted follows the theme.
 *
 * @param shape The box's outline.
 * @param modifier Usually a size or matchParentSize.
 */
@Composable
fun ImagePlaceholder(shape: Shape, modifier: Modifier = Modifier) {
    Box(modifier = modifier.imagePlaceholder(shape))
}

/**
 * The [ImagePlaceholder] fill and edge drawn on an element's own modifier, for
 * an image whose size comes from the image itself.
 *
 * @param shape The outline of the fill and edge.
 */
@Composable
fun Modifier.imagePlaceholder(shape: Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    return background(colors.surfaceVariant, shape)
        .border(1.dp, colors.outlineVariant, shape)
}

/** Below this on either side an image is too small for a spinner or glyph. */
private val COMPACT_SIZE = 48.dp

