// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Each profile image slot is written as the web writes it: the avatar at 256
 * pixels and the banner at 1600 as JPEG with white beneath any transparency,
 * the favicon at 128 as PNG keeping its transparency. Every slot used to be a
 * JPEG, with a transparent PNG's clear parts black, the favicon at 256 and
 * the banner at 1200.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePickerTest {

    /** A fully transparent PNG, [size] pixels square. */
    private fun clear(size: Int): ByteArray {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun process(image: ByteArray, slot: ImageSlot): Bitmap {
        val bytes = processImage({ ByteArrayInputStream(image) }, SLOT_SPECS.getValue(slot))!!
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun written(image: ByteArray, slot: ImageSlot): ByteArray =
        processImage({ ByteArrayInputStream(image) }, SLOT_SPECS.getValue(slot))!!

    @Test
    fun `a favicon keeps its transparency, at 128 pixels, as PNG`() {
        val bytes = written(clear(300), ImageSlot.FAVICON)
        assertEquals(0x89.toByte(), bytes[0])
        val image = process(clear(300), ImageSlot.FAVICON)
        assertEquals(128, image.width)
        assertEquals(0, Color.alpha(image.getPixel(64, 64)))
    }

    @Test
    fun `an avatar is white beneath a transparent image, at 256 pixels, as JPEG`() {
        val bytes = written(clear(300), ImageSlot.AVATAR)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val image = process(clear(300), ImageSlot.AVATAR)
        assertEquals(256, image.width)
        val pixel = image.getPixel(128, 128)
        assertTrue(Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240)
    }

    @Test
    fun `a banner is written at 1600 pixels`() {
        assertEquals(1600, process(clear(2000), ImageSlot.BANNER).width)
    }
}
