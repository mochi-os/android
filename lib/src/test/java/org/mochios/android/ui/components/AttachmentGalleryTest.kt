// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.model.Attachment
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The gallery hands each consumer the caller's addresses as given, so a
 * server-relative one goes to the server as the app's own and an absolute one
 * goes without the session; and an attachment with no address is not shown,
 * since an empty one named the server's root.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class AttachmentGalleryTest {

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

    private fun image(id: String) = Attachment(id = id, name = "$id.png", type = "image/png")

    @Test
    fun `an attachment with no address asks for nothing, and the rest go as given`() {
        val addresses = mapOf(
            "a1" to "/feeds/f1/-/attachments/a1",
            "a2" to "",
            "a3" to "https://cdn.example/a3.png",
        )
        rule.setContent {
            AttachmentGallery(
                attachments = listOf(image("a1"), image("a2"), image("a3")),
                urlBuilder = { addresses.getValue(it.id) },
                compact = true,
            )
        }
        rule.waitUntil(5_000) { loaded.size >= 2 }
        rule.waitForIdle()
        assertEquals(
            "THE POINT: the server-relative address unresolved, the absolute one as given, and no empty request",
            listOf("/feeds/f1/-/attachments/a1", "https://cdn.example/a3.png"),
            loaded.toSet().sorted(),
        )
        assertTrue(
            "the attachment with no address is not on screen",
            rule.onAllNodes(hasText("a2.png") or hasContentDescription("a2.png"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }
}
