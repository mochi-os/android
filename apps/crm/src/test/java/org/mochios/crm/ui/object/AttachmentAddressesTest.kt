// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.model.Attachment
import org.mochios.android.model.Comment
import org.mochios.android.ui.components.CommentDrafts
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Attachment ids can reach the phone from another server, and an image request
 * carries the session and the token of whatever app its path names. Each
 * address is built from the ids escaped as one segment; an id no segment can
 * carry has no image and asks for nothing.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class AttachmentAddressesTest {

    @get:Rule
    val rule = createComposeRule()

    private val loaded = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                // Answered with a picture, so a tile shows as it would.
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

    // An action, a slash, both dot segments, one to escape and a plain id.
    private val images = listOf("x/-/delete?", "a/b", "..", ".", "a?b", "a1").map(::image)

    private val address = Regex("/crm/x1/-/attachments/(a1|a%3Fb)(/thumbnail|/preview)?")

    private fun shown(content: @Composable () -> Unit): List<String> {
        rule.setContent { Column { content() } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        Thread.sleep(300)
        rule.waitForIdle()
        return loaded.toList()
    }

    // An image with no address asks for "", which resolves to the server's
    // root, or for a null model; either is an attachment shown broken.
    private fun blank(request: String) = request.isEmpty() || request == "/" || "NullRequestData" in request

    /** Every request is a plain or escaped id's address, and both were asked for. */
    private fun assertEscaped(requests: List<String>) {
        assertTrue("an attachment with no address asked for nothing: $requests", requests.none(::blank))
        assertTrue("every request is an escaped address: $requests", requests.all { address.matches(it) })
        assertTrue("the plain id was asked for: $requests", requests.any { it.startsWith("/crm/x1/-/attachments/a1") })
        assertTrue("the escaped id was asked for: $requests", requests.any { it.startsWith("/crm/x1/-/attachments/a%3Fb") })
    }

    @Test
    fun `the attachments section asks for no attachment a peer's id points elsewhere`() {
        assertEscaped(shown {
            AttachmentsSection(attachments = images, crmId = "x1", upload = null, onAddAttachments = {}, onDeleteAttachment = {})
        })
    }

    @Test
    fun `a comment asks for no attachment a peer's id points elsewhere`() {
        assertEscaped(shown {
            CommentsTab(
                comments = listOf(Comment(id = "c1", author = "ann", authorName = "Ann", body = "Hi", attachments = images)),
                currentUserId = null,
                crmId = "x1",
                drafts = CommentDrafts(),
                target = "ticket",
                onCreateComment = { _, _, _ -> },
                resolveFileName = { "" },
                onUpdateComment = { _, _ -> },
                onDeleteComment = {},
            )
        })
    }
}
