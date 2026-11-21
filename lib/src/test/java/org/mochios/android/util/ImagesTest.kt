// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File

/**
 * A picked or synced photo is decoded upright, reduced before it is decoded
 * and scaled to the slot, and written with white beneath its transparency as
 * a JPEG and with its transparency as a PNG. [BitmapFactory] alone leaves a
 * phone photo on its side and decodes it at full resolution, and
 * [Bitmap.compress] writes a transparent pixel black in a JPEG.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagesTest {

    private val context = RuntimeEnvironment.getApplication()

    /** A JPEG [width] by [height], its left half red and its right half blue, tagged with [orientation]. */
    private fun photo(width: Int, height: Int, orientation: Int? = null): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
        }
        val file = File.createTempFile("photo", ".jpg", context.cacheDir)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            if (orientation != null) {
                ExifInterface(file.path).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private fun decode(bytes: ByteArray, edge: Int) = decodeImage({ ByteArrayInputStream(bytes) }, edge)!!

    private fun red(pixel: Int) = Color.red(pixel) > 200 && Color.blue(pixel) < 60
    private fun blue(pixel: Int) = Color.blue(pixel) > 200 && Color.red(pixel) < 60

    @Test
    fun `a photo stored on its side with an orientation tag comes out upright`() {
        val image = decode(photo(40, 20, ExifInterface.ORIENTATION_ROTATE_90), 256)
        assertEquals(20, image.width)
        assertEquals(40, image.height)
        // Turned a quarter clockwise, the left half is now the top half.
        assertTrue(red(image.getPixel(10, 5)))
        assertTrue(blue(image.getPixel(10, 35)))
    }

    @Test
    fun `a photo with no orientation tag keeps its shape`() {
        val image = decode(photo(40, 20), 256)
        assertEquals(40, image.width)
        assertEquals(20, image.height)
        assertTrue(red(image.getPixel(5, 10)))
    }

    @Test
    fun `a photo far larger than the slot is decoded reduced, then scaled to the slot`() {
        assertEquals(8, sampleSize(3000, 256))
        assertEquals(1, sampleSize(200, 256))
        val image = decode(photo(3000, 1000), 256)
        assertEquals(256, image.width)
        assertEquals(85, image.height)
    }

    @Test
    fun `transparency is white beneath a JPEG and kept in a PNG`() {
        val clear = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val jpeg = encodeImage(clear, ImageFormat.JPEG)
        val opaque = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).getPixel(4, 4)
        assertTrue(Color.red(opaque) > 240 && Color.green(opaque) > 240 && Color.blue(opaque) > 240)
        val png = encodeImage(clear, ImageFormat.PNG)
        assertEquals(0x89.toByte(), png[0])
        assertEquals(0, Color.alpha(BitmapFactory.decodeByteArray(png, 0, png.size).getPixel(4, 4)))
    }
}
