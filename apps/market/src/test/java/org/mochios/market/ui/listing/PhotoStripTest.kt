// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.market.model.Photo
import org.mochios.market.ui.components.PhotoCarousel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The listing's photo carousel asks for no original: its pager, a square no
 * wider than the screen, draws the 1280 px preview, and every tile of its
 * strip in view loads at once, so the strip draws thumbnails. Drawn from the
 * originals, opening a six-photo listing fetched six full uploads (1.1-1.45 MB
 * each). The full-screen lightbox alone shows originals.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class PhotoStripTest {

    @get:Rule
    val rule = createComposeRule()

    private val loaded = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    loaded.add(chain.request.data.toString())
                    SuccessResult(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.MEMORY)
                })
            }.build()
        )
    }

    @After
    fun end() = SingletonImageLoader.reset()

    @Test
    fun `the pager asks for previews and the strip for thumbnails`() {
        val photos = List(6) { index -> Photo(id = "p$index") }
        val carousel = carouselUrls(photos, null)
        rule.setContent {
            PhotoCarousel(photoUrls = carousel.pages, thumbnailUrls = carousel.tiles)
        }
        // Six tiles and the pager's page: seven addresses, or six when the
        // strip shares the pager's (tile 0 is page 0).
        rule.waitUntil(5_000) { loaded.toSet().size >= 6 }
        runCatching { rule.waitUntil(2_000) { loaded.toSet().size >= 7 } }
        rule.waitForIdle()
        val requested = loaded.toSet()
        assertEquals(
            "THE POINT: every strip tile in view asks for the photo's thumbnail",
            (0 until 6).map { "/market/-/photo/p$it/thumbnail" }.toSet(),
            requested.filter { it.endsWith("/thumbnail") }.toSet(),
        )
        assertEquals(
            "THE POINT: the pager asks for its page's preview, and no original is fetched",
            setOf("/market/-/photo/p0/preview"),
            requested.filterNot { it.endsWith("/thumbnail") }.toSet(),
        )
    }
}
