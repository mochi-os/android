// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mochios.android.util.ImageFormat
import org.mochios.android.util.decodeImage
import org.mochios.android.util.encodeImage
import java.io.InputStream

/**
 * How a slot's image is written: its longest side at most [edge] pixels, as
 * [format], first at [quality], and no more than [maximum] bytes, the
 * server's cap for the slot.
 */
data class ImageSlotSpec(val edge: Int, val format: ImageFormat, val quality: Int, val maximum: Int)

/** The web's `SLOT_RESIZE`, with the server's caps. */
val SLOT_SPECS: Map<ImageSlot, ImageSlotSpec> = mapOf(
    ImageSlot.AVATAR to ImageSlotSpec(edge = 256, format = ImageFormat.JPEG, quality = 90, maximum = 2 * 1024 * 1024),
    ImageSlot.BANNER to ImageSlotSpec(edge = 1600, format = ImageFormat.JPEG, quality = 85, maximum = 10 * 1024 * 1024),
    // A favicon keeps its transparency.
    ImageSlot.FAVICON to ImageSlotSpec(edge = 128, format = ImageFormat.PNG, quality = 100, maximum = 64 * 1024),
)

/**
 * Launches the system image picker and hands back the picked image written
 * to the slot's spec.
 */
class ImagePicker(
    val launch: () -> Unit,
)

@Composable
fun rememberImagePicker(
    slot: ImageSlot,
    onPicked: (ByteArray) -> Unit,
    onError: (Throwable) -> Unit = {},
    onTooLarge: () -> Unit = {},
): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = SLOT_SPECS[slot] ?: error("No spec for $slot")

    var pending by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pending = uri
    }

    LaunchedEffect(pending) {
        val uri = pending ?: return@LaunchedEffect
        pending = null
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    processImage({ context.contentResolver.openInputStream(uri) }, spec)
                } ?: run {
                    onError(IllegalStateException("Could not decode image"))
                    return@launch
                }
                if (bytes.size > spec.maximum) {
                    onTooLarge()
                } else {
                    onPicked(bytes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    return remember(launcher) {
        ImagePicker(launch = { launcher.launch("image/*") })
    }
}

/**
 * The image [open] reads, upright, scaled to the slot and written as its
 * format; null when it does not decode. A JPEG steps its quality down until
 * it fits under the slot's cap, and comes back at the lowest when even that
 * does not, for the caller to refuse.
 */
internal fun processImage(open: () -> InputStream?, spec: ImageSlotSpec): ByteArray? {
    val bitmap = decodeImage(open, spec.edge) ?: return null
    try {
        var quality = spec.quality
        var bytes = encodeImage(bitmap, spec.format, quality)
        while (spec.format == ImageFormat.JPEG && bytes.size > spec.maximum && quality > 30) {
            quality -= 15
            bytes = encodeImage(bitmap, spec.format, quality)
        }
        return bytes
    } finally {
        bitmap.recycle()
    }
}
