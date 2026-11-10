// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
import org.mochios.android.model.Attachment
import org.mochios.android.ui.components.mediumAddress
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The feed's photo mosaic asks each tile drawn within 640px for the medium:
 * four photos two by two in a 411dp square are about 204dp tiles, 613px at
 * 3x. A lone photo spans the mosaic and keeps the preview.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp-xxhdpi")
class MosaicVariantTest {

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

    private fun mosaic(count: Int) {
        val media = (0 until count).map {
            Attachment(id = "a$it", name = "a$it.jpg", type = "image/jpeg", previewUrl = "/feeds/f1/-/attachments/a$it/preview")
        }
        rule.setContent {
            GalleryMosaic(
                media = media,
                tileModel = { it.previewUrl.orEmpty() },
                mediumModel = { mediumAddress(it.previewUrl) },
                onTap = {},
                modifier = Modifier.size(411.dp),
            )
        }
        rule.waitUntil(5_000) { loaded.isNotEmpty() }
        runCatching { rule.waitUntil(2_000) { loaded.toSet().size >= count } }
        rule.waitForIdle()
    }

    @Test
    fun `tiles two by two ask for the medium`() {
        mosaic(4)
        assertEquals(
            "THE POINT: each tile drawn within 640px asks for its medium",
            (0 until 4).map { "/feeds/f1/-/attachments/a$it/medium" }.toSet(),
            loaded.toSet(),
        )
    }

    @Test
    fun `a lone photo across the mosaic asks for the preview`() {
        mosaic(1)
        assertEquals(setOf("/feeds/f1/-/attachments/a0/preview"), loaded.toSet())
    }
}
