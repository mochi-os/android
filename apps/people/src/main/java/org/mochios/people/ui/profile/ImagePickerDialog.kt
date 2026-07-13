// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Caps + target dimensions per slot. Mirrors `SLOT_RESIZE` on the web (see
 * `apps/people/web/src/lib/resize-image.ts`), tuned for the mobile bandwidth
 * profile: avatars / favicons resize to a 256px square, banners to 1200px
 * wide. The byte cap is the spec we pass back to the ViewModel after
 * iterating JPEG quality.
 */
data class ImageSlotSpec(val maxDimension: Int, val maxBytes: Int)

val SLOT_SPECS: Map<ImageSlot, ImageSlotSpec> = mapOf(
    ImageSlot.AVATAR to ImageSlotSpec(maxDimension = 256, maxBytes = 2 * 1024 * 1024),     //  2 MB
    ImageSlot.BANNER to ImageSlotSpec(maxDimension = 1200, maxBytes = 10 * 1024 * 1024),   // 10 MB
    ImageSlot.FAVICON to ImageSlotSpec(maxDimension = 256, maxBytes = 64 * 1024),          // 64 KB
)

/**
 * Wraps an [ActivityResultContracts.GetContent] launcher so a screen-level
 * "Change avatar" / "Change banner" / "Upload favicon" button can resolve to
 * a resized JPEG [ByteArray] without any further composable plumbing.
 *
 * `trigger.launch()` opens the system image picker. After the user picks an
 * image we decode it on a background thread, scale it down to the slot's
 * `maxDimension`, then encode JPEG starting at quality 85 and step down by 15
 * until the result fits under `maxBytes` (floor 30). The resulting bytes go
 * to `onPicked`; errors land in `onError`.
 *
 * Note: the system picker IS the dialog — no Compose surface is shown here.
 * The name follows the spec (ImagePickerDialog) and the helpers used by the
 * screen mirror that affordance.
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
                    processImage(context, uri, spec)
                } ?: run {
                    onError(IllegalStateException("Could not decode image"))
                    return@launch
                }
                if (bytes.size > spec.maxBytes) {
                    onTooLarge()
                } else {
                    onPicked(bytes)
                }
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
 * Decode → scale → JPEG-compress an image URI down to a byte array that fits
 * under `spec.maxBytes`. Returns `null` when the URI can't be decoded.
 */
private fun processImage(
    context: Context,
    uri: Uri,
    spec: ImageSlotSpec,
): ByteArray? {
    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
    val scaled = scale(bitmap, spec.maxDimension)
    // Try quality 85 → 70 → 55 → 40 → 30; return whatever fits, or the
    // smallest if even quality 30 is still over the cap (caller decides
    // whether to reject).
    var quality = 85
    var lastBytes: ByteArray = encodeJpeg(scaled, quality)
    while (lastBytes.size > spec.maxBytes && quality > 30) {
        quality -= 15
        lastBytes = encodeJpeg(scaled, quality)
    }
    if (scaled !== bitmap) scaled.recycle()
    bitmap.recycle()
    return lastBytes
}

private fun scale(src: Bitmap, maxDim: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= maxDim && h <= maxDim) return src
    val ratio = maxDim.toFloat() / maxOf(w, h)
    val nw = (w * ratio).toInt().coerceAtLeast(1)
    val nh = (h * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, nw, nh, true)
}

private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}
