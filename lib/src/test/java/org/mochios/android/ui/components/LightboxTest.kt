// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.network.NetworkResponse
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.model.Attachment
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The lightbox: an abandoned gesture settles back, a swipe closes it once and
 * only as the finger lifts, a failed image says why and can be tried again,
 * the image is announced by its caption or name, and the comment count is
 * written in the reader's number format.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LightboxTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val loads = AtomicInteger()

    /** Every image load answers with [answer]. */
    private fun images(answer: (Interceptor.Chain) -> coil3.request.ImageResult) {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    loads.incrementAndGet()
                    answer(chain)
                })
            }.build()
        )
    }

    private fun loaded() = images { chain ->
        SuccessResult(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.MEMORY)
    }

    @After
    fun end() = SingletonImageLoader.reset()

    private fun page(zoom: ZoomState, onDismiss: () -> Unit = {}) {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("page")) {
                ZoomableImage(url = "/feeds/f1/-/attachments/a1", description = "Sunset", onDismiss = onDismiss, zoom = zoom)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a swipe down that stops short of closing settles back`() {
        loaded()
        val zoom = ZoomState()
        var dismissed = 0
        page(zoom) { dismissed++ }
        compose.onNodeWithTag("page").performTouchInput {
            down(center)
            repeat(3) { moveBy(Offset(0f, 50f)) }
        }
        compose.runOnIdle { assertTrue("the image follows the finger", zoom.offset.y > 0f) }
        compose.onNodeWithTag("page").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("THE POINT: back on centre", Offset.Zero, zoom.offset)
        assertEquals("and no longer dimmed", 0f, zoom.dismissProgress)
        assertEquals(0, dismissed)
    }

    @Test
    fun `a swipe past the threshold closes once, as the finger lifts`() {
        loaded()
        val zoom = ZoomState()
        var dismissed = 0
        page(zoom) { dismissed++ }
        compose.onNodeWithTag("page").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(0f, 50f)) }
        }
        compose.runOnIdle { assertEquals("not while the finger is down", 0, dismissed) }
        compose.onNodeWithTag("page").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("THE POINT: once", 1, dismissed)
    }

    @Test
    fun `a swipe carried back short of the threshold before lifting does not close`() {
        loaded()
        val zoom = ZoomState()
        var dismissed = 0
        page(zoom) { dismissed++ }
        compose.onNodeWithTag("page").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(0f, 50f)) }
            repeat(6) { moveBy(Offset(0f, -50f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(0, dismissed)
        assertEquals(Offset.Zero, zoom.offset)
    }

    @Test
    fun `a pinch below full size springs back to it`() {
        loaded()
        val zoom = ZoomState()
        page(zoom)
        compose.onNodeWithTag("page").performTouchInput {
            down(0, center - Offset(100f, 0f))
            down(1, center + Offset(100f, 0f))
            updatePointerTo(0, center - Offset(20f, 0f))
            updatePointerTo(1, center + Offset(20f, 0f))
            move()
        }
        compose.runOnIdle { assertTrue("shrunk under the fingers", zoom.scale < 1f) }
        compose.onNodeWithTag("page").performTouchInput {
            up(0)
            up(1)
        }
        compose.waitForIdle()
        assertEquals("THE POINT: not left shrunk", 1f, zoom.scale)
        assertEquals(Offset.Zero, zoom.offset)
    }

    @Test
    fun `a zoomed image pans only as far as its edges and recentres when zoomed back out`() {
        loaded()
        val zoom = ZoomState()
        page(zoom)
        compose.onNodeWithTag("page").performTouchInput {
            down(0, center - Offset(100f, 0f))
            down(1, center + Offset(100f, 0f))
            // Both fingers apart: twice the size.
            updatePointerTo(0, center - Offset(200f, 0f))
            updatePointerTo(1, center + Offset(200f, 0f))
            move()
            // Both fingers far to the right, as far apart: a pan alone.
            updatePointerTo(0, center + Offset(1800f, 0f))
            updatePointerTo(1, center + Offset(2200f, 0f))
            move()
        }
        compose.runOnIdle {
            assertTrue("zoomed in", zoom.scale > 1.5f)
            val edge = zoom.size.width * (zoom.scale - 1f) / 2f
            assertTrue("panned", zoom.offset.x > 0f)
            assertTrue("THE POINT: no further than the edge", zoom.offset.x <= edge + 0.5f)
        }
        compose.onNodeWithTag("page").performTouchInput {
            // Pinched back in, below full size, and let go.
            updatePointerTo(0, center + Offset(1950f, 0f))
            updatePointerTo(1, center + Offset(2050f, 0f))
            move()
            up(0)
            up(1)
        }
        compose.waitForIdle()
        assertEquals(1f, zoom.scale)
        assertEquals("THE POINT: the pan does not outlive the zoom", Offset.Zero, zoom.offset)
    }

    @Test
    fun `the pan is held to the image's overhang`() {
        val size = IntSize(400, 800)
        assertEquals(Offset(200f, -400f), panned(Offset(900f, -900f), 2f, size))
        assertEquals(Offset(10f, 20f), panned(Offset(10f, 20f), 2f, size))
        assertEquals("none at full size", Offset.Zero, panned(Offset(10f, 20f), 1f, size))
    }

    @Test
    fun `an image that is gone says so, and a tap tries again`() {
        images { chain -> ErrorResult(null, chain.request, HttpException(NetworkResponse(code = 404))) }
        page(ZoomState())
        compose.onNodeWithText(context.getString(R.string.common_not_found)).assertExists()
        val before = loads.get()
        compose.onNodeWithText(context.getString(R.string.common_not_found)).performClick()
        compose.waitForIdle()
        assertTrue("THE POINT: fetched again", loads.get() > before)
    }

    @Test
    fun `an image that cannot be reached says it is unavailable`() {
        images { chain -> ErrorResult(null, chain.request, IOException("offline")) }
        page(ZoomState())
        compose.onNodeWithText(context.getString(R.string.common_unavailable)).assertExists()
    }

    @Test
    fun `the image is announced by its description`() {
        loaded()
        page(ZoomState())
        compose.onNodeWithContentDescription("Sunset").assertExists()
    }

    @Test
    fun `an attachment is announced by its caption, else its name`() {
        loaded()
        compose.setContent {
            AttachmentLightbox(
                images = listOf(Attachment(id = "a1", name = "a1.png", type = "image/png")),
                urlBuilder = { "/feeds/f1/-/attachments/${it.id}" },
                initialIndex = 0,
                onDismiss = {},
            )
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("a1.png").assertExists()
    }

    @Test
    fun `a caption is what the image is announced by`() {
        loaded()
        compose.setContent {
            AttachmentLightbox(
                images = listOf(Attachment(id = "a1", name = "a1.png", type = "image/png", caption = "Beach")),
                urlBuilder = { "/feeds/f1/-/attachments/${it.id}" },
                initialIndex = 0,
                onDismiss = {},
            )
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Beach").assertExists()
    }

    @Test
    fun `a lightbox given only captions is announced by them`() {
        loaded()
        compose.setContent {
            LightboxScreen(
                images = listOf("/wikis/w1/-/attachments/a1"),
                initialIndex = 0,
                onDismiss = {},
                captions = listOf("Harbour"),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Harbour").assertExists()
    }

    @Test
    fun `the comment count is written in the reader's number format`() {
        loaded()
        compose.setContent {
            LightboxScreen(
                images = listOf("/feeds/f1/-/attachments/a1"),
                initialIndex = 0,
                onDismiss = {},
                commentCount = { 1234 },
                comments = { Text("comments") },
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText(Format(UserPreferences()).formatNumber(1234)).assertExists()
    }
}
