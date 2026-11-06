// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui

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
import org.mochios.android.model.Comment
import org.mochios.feeds.model.Permissions
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PostData
import org.mochios.feeds.model.RssData
import org.mochios.feeds.model.SavedItem
import org.mochios.feeds.model.SavedSnapshot
import org.mochios.feeds.ui.component.CommentItem
import org.mochios.feeds.ui.feed.PostCard
import org.mochios.feeds.ui.post.PostContent
import org.mochios.feeds.ui.saved.SavedPostCardContent
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Attachment ids reach the phone from the feed's owner, in a remote view, and
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

    private val address = Regex("/feeds/f1/-/attachments/(a1|a2%3Fx%3D1|a3%23z)(/thumbnail|/preview)?")

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
        assertTrue("the plain id was asked for: $attachments", attachments.any { it.startsWith("/feeds/f1/-/attachments/a1") })
        assertTrue("the escaped id was asked for: $attachments", attachments.any { it.startsWith("/feeds/f1/-/attachments/a2%3Fx%3D1") })
    }

    private fun card(post: Post) = shown {
        PostCard(
            post = post, fallbackFeedId = "f1", upgradedRssImage = null, isSaved = false,
            onClick = {}, onComments = {}, onViewComments = {}, onReact = {}, onToggleSave = {},
            onAddTag = {}, onAdjustInterest = { _, _ -> }, canManage = false, onEdit = {}, onDelete = {},
        )
    }

    @Test
    fun `a gallery card asks for no attachment a peer's id points elsewhere`() {
        assertEscaped(card(Post(id = "p1", feed = "f1", body = "Short", attachments = images)))
    }

    @Test
    fun `a card's hero and grid ask for no attachment a peer's id points elsewhere`() {
        // Past the gallery's caption limit, the card shows a hero and a grid.
        assertEscaped(card(Post(id = "p1", feed = "f1", body = "Long ".repeat(60), attachments = images)))
    }

    @Test
    fun `a feed id no segment can carry addresses no attachment`() {
        val requests = card(Post(id = "p1", feed = "../../people", body = "Short", attachments = images))
        assertEquals(emptyList<String>(), requests.filter { blank(it) || "attachments" in it || "people" in it })
    }

    @Test
    fun `a comment's anchor a peer chose points the preview at no other action`() {
        val comments = listOf("../../../../people/-/x?y=1", "..", "a2?x=1").mapIndexed { n, anchor ->
            Comment(id = "c$n", authorName = "Ann", body = "About it", attachment = anchor)
        }
        val requests = card(Post(id = "p1", feed = "f1", body = "Long ".repeat(60), comments = comments))
        assertEquals(
            listOf("/feeds/f1/-/attachments/a2%3Fx%3D1/thumbnail"),
            requests.filter { blank(it) || "attachments" in it || "people" in it }.distinct(),
        )
    }

    @Test
    fun `a post's page asks for no attachment a peer's id points elsewhere`() {
        assertEscaped(shown {
            PostContent(
                post = Post(id = "p1", feed = "f1", body = "Body", attachments = images),
                tags = emptyList(), permissions = Permissions(), serverUrl = "https://server", feedId = "f1",
                onReact = {}, onAddTag = {}, onAdjustInterest = { _, _ -> },
            )
        })
    }

    @Test
    fun `a comment asks for no attachment or anchor a peer's id points elsewhere`() {
        assertEscaped(shown {
            CommentItem(
                comment = Comment(id = "c1", authorName = "Ann", body = "Hi", attachments = images, attachment = "../../people/-/x"),
                depth = 0, avatarUrl = null, feedId = "f1", isEditing = false, editText = "",
                onEditTextChange = {}, onSaveEdit = {}, onCancelEdit = {}, onReply = {}, onEdit = {}, onDelete = {},
                onReact = {}, canManage = false, onModerate = {},
            )
            CommentItem(
                comment = Comment(id = "c2", authorName = "Bob", body = "Hi", attachment = "a1"),
                depth = 0, avatarUrl = null, feedId = "f1", isEditing = false, editText = "",
                onEditTextChange = {}, onSaveEdit = {}, onCancelEdit = {}, onReply = {}, onEdit = {}, onDelete = {},
                onReact = {}, canManage = false, onModerate = {},
            )
        })
        assertHidden()
    }

    @Test
    fun `a saved post's one image is its hero, at its escaped address`() {
        val one = images.take(4) + image("a2?x=1")
        val requests = shown {
            SavedPostCardContent(
                item = SavedItem(post = SavedSnapshot(id = "p1", feedId = "f1", body = "Body", attachments = one)),
                onClick = {}, onUnsave = {},
            )
        }
        assertEquals(
            listOf("/feeds/f1/-/attachments/a2%3Fx%3D1"),
            requests.filter { blank(it) || "attachments" in it || "people" in it }.distinct(),
        )
    }

    // An article's image is asked for only at a web address: a path is taken
    // for one of the app's own addresses, fetched with the session and the
    // feeds token.
    private fun article(image: String) =
        card(Post(id = "p1", feed = "f1", body = "An article", data = PostData(rss = RssData(image = image, link = "https://example.com/a"))))

    @Test
    fun `an article's image that is a path is not asked for`() {
        val requests = article("/settings/-/p2ptestarticle")
        assertTrue("THE POINT: no request for the path: $requests", requests.none { "p2ptestarticle" in it })
    }

    @Test
    fun `an article's image at a web address is asked for`() {
        val requests = article("https://example.com/a.png")
        assertTrue("control: the web address is asked for: $requests", "https://example.com/a.png" in requests)
    }

    @Test
    fun `a saved post asks for no attachment a peer's id points elsewhere`() {
        assertEscaped(shown {
            SavedPostCardContent(
                item = SavedItem(post = SavedSnapshot(id = "p1", feedId = "f1", body = "Body", attachments = images)),
                onClick = {}, onUnsave = {},
            )
        })
    }
}
