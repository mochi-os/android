// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.mochios.android.api.OwnedAddress
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import kotlin.math.max

/** The long edge of the medium image variant, in pixels (core's medium_size). */
const val MEDIUM_EDGE = 640

/**
 * Whether a tile drawn at [width] x [height] pixels is served by the medium:
 * its longer side fits within the medium's long edge. A tile with no bounded
 * size takes the preview.
 */
fun tileTakesMedium(width: Int, height: Int): Boolean =
    width in 1 until Constraints.Infinity &&
        height in 1 until Constraints.Infinity &&
        max(width, height) <= MEDIUM_EDGE

/**
 * The medium address of an attachment whose server gave its preview's: the
 * same route with `medium` for `preview`. Null with no preview address, from
 * a server whose app predates the variants.
 */
fun mediumAddress(preview: OwnedAddress?): OwnedAddress? {
    val address = preview?.address ?: return null
    val query = address.indexOf('?').let { if (it < 0) address.length else it }
    val path = address.substring(0, query)
    if (!path.endsWith("/preview")) return null
    return OwnedAddress.of(path.removeSuffix("/preview") + "/medium" + address.substring(query))
}

/**
 * A tile's image address, chosen by the size the tile is drawn at: [medium]
 * when the tile fits within it, [preview] otherwise or with no medium. The
 * size is known before the first request, so the tile fetches once. [content]
 * gets the address and, for a medium, the preview to fall back to: a server
 * whose app has no medium route yet refuses it. Each is a Coil model: an
 * [OwnedAddress], fetched with a credential, or a URL fetched without.
 */
@Composable
fun TileVariant(
    medium: Any?,
    preview: Any,
    modifier: Modifier = Modifier,
    content: @Composable (model: Any, fallback: Any?) -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (medium != null && tileTakesMedium(constraints.maxWidth, constraints.maxHeight)) {
            content(medium, preview)
        } else {
            content(preview, null)
        }
    }
}
