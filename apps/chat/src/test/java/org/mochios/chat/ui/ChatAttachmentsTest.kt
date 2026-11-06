// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.SuccessResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.model.Attachment
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.ui.chat.MessageBubble
import org.mochios.chat.ui.chat.ThreadMessage
import org.mochios.chat.ui.chat.messageAttachments
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The server files a peer's attachment only under a 32-character id, so no
 * hostile one reaches the phone today; the address still escapes the id as one
 * segment, since an image request carries the session and the token of
 * whatever app its path names. An id no segment can carry asks for nothing.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class ChatAttachmentsTest {

    @get:Rule
    val rule = createComposeRule()

    private val loaded = ConcurrentLinkedQueue<String>()

    @Before
    fun begin() {
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                // Answered with a picture, so a tile shows as it would, its
                // description included.
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

    // A path, a slash, both dot segments, a plain id and two to escape.
    private val images = listOf("../../../../people/-/x?y=1", "a/b", "..", ".", "a2?x=1", "a1", "a3#z").map(::image)

    private val message = ChatMessage(id = "m1", member = "ann", name = "ann", body = "Look", attachments = images)

    private val address = Regex("/chat/c1/-/attachments/(a1|a2%3Fx%3D1|a3%23z)(/thumbnail|/preview)?")

    private fun shown(content: @Composable () -> Unit): List<String> {
        rule.setContent { Column { content() } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        Thread.sleep(300)
        rule.waitForIdle()
        return loaded.toList()
    }

    // An image with no address asks for "", which the gallery resolves to the
    // server's root, or for a null model, which the loader names
    // NullRequestData; any of them is an attachment shown broken.
    private fun blank(request: String) = request.isEmpty() || request == "/" || "NullRequestData" in request

    // No attachment without an address is on screen, and a plain one is.
    private fun assertHidden() {
        for (name in images.take(4).map { it.name }) {
            rule.onAllNodes(hasText(name) or hasContentDescription(name), useUnmergedTree = true).assertCountEquals(0)
        }
        assertTrue(rule.onAllNodes(hasText("a1.png") or hasContentDescription("a1.png"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    /** Every attachment request is a plain one's escaped address, and each was asked for. */
    private fun assertEscaped(requests: List<String>) {
        assertTrue("an attachment with no address asked for nothing: $requests", requests.none(::blank))
        val attachments = requests.filter { "attachments" in it || "people" in it || "/a/b" in it }
        assertTrue("every request is an escaped address: $attachments", attachments.all { address.matches(it) })
        assertTrue("the plain id was asked for: $attachments", attachments.any { it.startsWith("/chat/c1/-/attachments/a1") })
        assertTrue("the escaped id was asked for: $attachments", attachments.any { it.startsWith("/chat/c1/-/attachments/a2%3Fx%3D1") })
    }

    @Test
    fun `an attachment no segment can carry is left out`() {
        assertEquals(listOf("a2?x=1", "a1", "a3#z"), messageAttachments("c1", images).map { it.id })
        assertEquals(emptyList<String>(), messageAttachments("../c1", images).map { it.id })
    }

    @Test
    fun `a message asks for no attachment an id points elsewhere`() {
        assertEscaped(shown {
            MessageBubble(
                message = message, isOwn = false, receipt = null, seen = null, onThread = null, isGroup = true,
                chatId = "c1", selectionMode = false, isSelected = false, isSearchMatch = false, searchQuery = "",
                replyToMessage = null, onStartSelect = {}, onToggleSelect = {}, onReply = {}, onDelete = {},
                onReact = {}, onForward = {}, onEdit = {}, avatar = null,
            )
        })
        assertHidden()
    }

    @Test
    fun `a thread's message asks for no attachment an id points elsewhere`() {
        assertEscaped(shown { ThreadMessage(message, "c1") })
        assertHidden()
    }
}
