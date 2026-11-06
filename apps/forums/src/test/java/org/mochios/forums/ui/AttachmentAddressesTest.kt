// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.model.Attachment
import org.mochios.forums.model.ForumComment
import org.mochios.forums.model.ModerationReport
import org.mochios.forums.model.Post
import org.mochios.forums.ui.moderation.ReportCard
import org.mochios.forums.ui.newpost.ExistingAttachmentChips
import org.mochios.forums.ui.post.CommentCard
import org.mochios.forums.ui.post.PostHeader
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Attachment ids reach the phone from the forum's owner, in a remote view, and
 * an image request carries the session and the token of whatever app its path
 * names. Each address is built from the ids escaped as one segment; an id no
 * segment can carry has no image and asks for nothing.
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

    private val address = Regex("/forums/f1/-/attachments/(a1|a2%3Fx%3D1|a3%23z)(/thumbnail|/preview)?")

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
        assertTrue("the plain id was asked for: $attachments", attachments.any { it.startsWith("/forums/f1/-/attachments/a1") })
        assertTrue("the escaped id was asked for: $attachments", attachments.any { it.startsWith("/forums/f1/-/attachments/a2%3Fx%3D1") })
    }

    @Test
    fun `a post asks for no attachment a peer's id points elsewhere`() {
        assertEscaped(shown {
            PostHeader(
                post = Post(id = "p1", forum = "f1", title = "Title", body = "Body", attachments = images),
                canVote = false, canEdit = false, forumId = "f1",
                onVote = {}, onComment = null, onAddTag = {}, onRemoveTag = {}, onTagInterest = { _, _ -> },
            )
        })
    }

    @Test
    fun `a comment asks for no attachment or anchor a peer's id points elsewhere`() {
        fun card(comment: ForumComment) = @Composable {
            CommentCard(
                comment = comment, depth = 0, forumId = "f1", avatars = emptyMap(),
                canEdit = false, canModerate = false, isAuthor = false,
                onVote = {}, onReply = {}, onEdit = {}, onDelete = {}, onApprove = {}, onRemove = {},
                onRestore = {}, onReport = {}, onQuote = {}, onOpenAttachment = {},
            )
        }
        val pathed = card(ForumComment(id = "c1", post = "p1", member = "ann", name = "Ann", body = "Hi", attachments = images, attachment = "../../people/-/x"))
        val anchored = card(ForumComment(id = "c2", post = "p1", member = "bob", name = "Bob", body = "Hi", attachment = "a1"))
        assertEscaped(shown {
            pathed()
            anchored()
        })
        assertHidden()
    }

    @Test
    fun `a report asks for no attachment a peer's id points elsewhere`() {
        assertEscaped(shown {
            ReportCard(
                report = ModerationReport(id = "r1", forum = "f1", type = "post", target = "p1", status = "pending", attachments = images),
                forumId = "f1", showStatus = true, onResolve = {},
            )
        })
        assertHidden()
    }

    @Test
    fun `editing a post asks for no attachment a peer's id points elsewhere`() {
        assertEscaped(shown {
            ExistingAttachmentChips(
                attachments = images, forumId = "f1", removedIds = emptySet(), captions = emptyMap(),
                onMove = { _, _ -> }, onToggleRemove = {}, onCaption = {},
            )
        })
    }
}
