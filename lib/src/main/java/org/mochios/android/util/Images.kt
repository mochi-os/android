// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

/** How an image is written: its type as an upload declares it, and its file extension. */
enum class ImageFormat(val type: String, val extension: String, internal val compress: Bitmap.CompressFormat) {
    JPEG("image/jpeg", "jpg", Bitmap.CompressFormat.JPEG),
    PNG("image/png", "png", Bitmap.CompressFormat.PNG),
}

/**
 * The image [open] reads, upright, its longest side at most [edge] pixels;
 * null when it does not decode. [open] gives a fresh stream over the same
 * image each time it is called: once for the size, once for the orientation
 * and once for the pixels. The pixels are decoded at the largest power-of-two
 * reduction that keeps [edge], so a photo far larger than it never needs its
 * full resolution in memory. A phone camera stores most photos on their side
 * with an EXIF tag saying which way is up, which [BitmapFactory] leaves
 * unapplied.
 */
fun decodeImage(open: () -> InputStream?, edge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val measured = open() ?: return null
    measured.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val orientation = orientation(open)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), edge)
    }
    val stream = open() ?: return null
    val decoded = stream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    return oriented(scaled(decoded, edge), orientation)
}

/**
 * [bitmap] written as [format], at [quality] for a JPEG; a PNG is lossless.
 * A JPEG has no transparency, and [Bitmap.compress] writes a transparent
 * pixel black, so a JPEG is drawn onto white first, as the web's canvas is.
 */
fun encodeImage(bitmap: Bitmap, format: ImageFormat, quality: Int = 90): ByteArray {
    val out = ByteArrayOutputStream()
    if (format == ImageFormat.JPEG && bitmap.hasAlpha()) {
        val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        opaque.compress(format.compress, quality, out)
        opaque.recycle()
    } else {
        bitmap.compress(format.compress, quality, out)
    }
    return out.toByteArray()
}

/** The largest power-of-two reduction of [longest] pixels that leaves at least [edge]. */
internal fun sampleSize(longest: Int, edge: Int): Int {
    var sample = 1
    while (longest / (sample * 2) >= edge) sample *= 2
    return sample
}

/** The EXIF orientation, normal for an image that has none or whose metadata does not read. */
private fun orientation(open: () -> InputStream?): Int = try {
    open()?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
} catch (_: Exception) {
    ExifInterface.ORIENTATION_NORMAL
}

private fun scaled(bitmap: Bitmap, edge: Int): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= edge) return bitmap
    val ratio = edge.toFloat() / longest
    val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
    val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
    val result = Bitmap.createScaledBitmap(bitmap, width, height, true)
    if (result !== bitmap) bitmap.recycle()
    return result
}

/** [bitmap] turned and mirrored as its EXIF [orientation] says. */
private fun oriented(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (result !== bitmap) bitmap.recycle()
    return result
}
