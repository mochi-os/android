// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A photo cell asks for the 640px medium when it is drawn small enough for
 * it, and for the 1280px preview otherwise: a feed or forum grid cell is a
 * third of the preview's bytes as a medium. A server whose app has no medium
 * route yet refuses it, and the cell then loads the preview instead.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp-xxhdpi")
class TileVariantTest {

    @get:Rule
    val rule = createComposeRule()

    private val loaded = ConcurrentLinkedQueue<String>()
    private var refused = ""

    @Before
    fun begin() {
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    val address = chain.request.data.toString()
                    loaded.add(address)
                    if (refused.isNotEmpty() && address.endsWith(refused)) {
                        ErrorResult(null, chain.request, IllegalStateException("404"))
                    } else {
                        SuccessResult(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.MEMORY)
                    }
                })
            }.build()
        )
    }

    @After
    fun end() = SingletonImageLoader.reset()

    private fun grid(count: Int) {
        val ids = (0 until count).map { "a$it" }
        rule.setContent {
            MediaGrid(
                urls = ids.map { "/feeds/f1/-/attachments/$it" },
                thumbnailUrls = ids.map { "/feeds/f1/-/attachments/$it/preview" },
                mediumUrls = ids.map { "/feeds/f1/-/attachments/$it/medium" },
                onClick = {},
                modifier = Modifier.width(411.dp),
            )
        }
    }

    // Waits for the first requests, then briefly for the rest, so a missing
    // request fails on the assertion that names it rather than on a timeout.
    private fun settle(expected: Int) {
        rule.waitUntil(5_000) { loaded.isNotEmpty() }
        runCatching { rule.waitUntil(2_000) { loaded.toSet().size >= expected } }
        rule.waitForIdle()
    }

    @Test
    fun `a half-width cell asks for the medium`() {
        // Two cells of about 203 x 160 dp: 610 x 480 px at 3x.
        grid(2)
        settle(2)
        assertEquals(
            "THE POINT: each cell drawn within 640px asks for the medium",
            setOf("/feeds/f1/-/attachments/a0/medium", "/feeds/f1/-/attachments/a1/medium"),
            loaded.toSet(),
        )
    }

    @Test
    fun `a full-width cell asks for the preview`() {
        // One cell of 411 x 200 dp: 1233 px wide at 3x, past the medium.
        grid(1)
        settle(1)
        assertEquals(setOf("/feeds/f1/-/attachments/a0/preview"), loaded.toSet())
    }

    @Test
    fun `a refused medium falls back to the preview`() {
        refused = "/medium"
        grid(2)
        settle(4)
        val requested = loaded.toSet()
        assertTrue(
            "THE POINT: a cell whose medium is refused loads its preview: $requested",
            requested.containsAll(listOf("/feeds/f1/-/attachments/a0/preview", "/feeds/f1/-/attachments/a1/preview")),
        )
    }

    @Test
    fun `the medium address is the preview's route with medium`() {
        assertEquals("/feeds/f1/-/attachments/a1/medium", mediumAddress("/feeds/f1/-/attachments/a1/preview"))
        assertEquals(
            "/forums/f1/-/attachments/a1/medium?server=x",
            mediumAddress("/forums/f1/-/attachments/a1/preview?server=x"),
        )
        assertNull("no preview address, no medium", mediumAddress(null))
        assertNull("not a preview address", mediumAddress("/feeds/f1/-/attachments/a1/thumbnail"))
    }

    @Test
    fun `the size rule counts the longer side`() {
        assertTrue(tileTakesMedium(610, 480))
        assertTrue(tileTakesMedium(640, 300))
        assertFalse(tileTakesMedium(641, 300))
        assertFalse(tileTakesMedium(300, 700))
        assertFalse("no bounded size", tileTakesMedium(0, 0))
    }
}
