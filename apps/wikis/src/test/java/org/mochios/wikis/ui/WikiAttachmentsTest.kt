// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.ui.attachments.AttachmentsBody
import org.mochios.wikis.ui.attachments.AttachmentsUiState
import org.mochios.wikis.ui.attachments.AttachmentsViewMode
import org.mochios.wikis.ui.comments.CommentAttachments
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.WikiContextValue
import org.mochios.wikis.ui.components.lightboxImages
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A wiki's pages are written by its editors and by those of the wiki it
 * replicates, and an image request carries the session and the token of
 * whatever app its path names. An attachment address is built from the id
 * escaped as one segment, and the lightbox, which loads with the session,
 * takes only this wiki's attachments and other hosts' images.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class WikiAttachmentsTest {

    @get:Rule
    val rule = createComposeRule()

    private val loaded = ConcurrentLinkedQueue<String>()

    private val wiki = WikiContextValue("w1", WikiInfo(id = "w1"), WikiPermissions(view = true), "https://server")

    private val base = "https://server/wikis/w1/-/"

    @Before
    fun begin() {
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    loaded.add(chain.request.data.toString())
                    ErrorResult(null, chain.request, IllegalStateException("no images in tests"))
                })
            }.build()
        )
    }

    @After
    fun end() = SingletonImageLoader.reset()

    @Test
    fun `a page's attachment link resolves under this wiki`() {
        assertEquals("${base}attachments/a1", wiki.resolveAttachmentUrl("attachments/a1"))
        assertEquals("${base}attachments/a1/thumbnail", wiki.resolveAttachmentUrl("-/attachments/a1/thumbnail"))
        assertEquals("${base}attachments/a1/thumbnail", wiki.resolveAttachmentUrl("/other/-/attachments/a1/thumbnail"))
        assertEquals("https://example.com/p.png", wiki.resolveAttachmentUrl("https://example.com/p.png"))
    }

    @Test
    fun `a page's attachment link cannot point at another action`() {
        assertNull(wiki.resolveAttachmentUrl("attachments/../../../../settings/-/x?y=1"))
        assertNull(wiki.resolveAttachmentUrl("attachments/a1/../../x"))
        assertNull(wiki.resolveAttachmentUrl("attachments/.."))
        assertNull(wiki.resolveAttachmentUrl("-/attachments/."))
        assertNull(wiki.resolveAttachmentUrl("/other/-/attachments/../x"))
        // An escape in the id is escaped again: the server decodes the path
        // once, so "%2F" would otherwise divide it.
        assertEquals("${base}attachments/a%252F", wiki.resolveAttachmentUrl("/other/-/attachments/a%2F"))
        assertEquals("${base}attachments/a%252E%252E", wiki.resolveAttachmentUrl("/other/-/attachments/a%2E%2E"))
    }

    @Test
    fun `the lightbox loads this wiki's attachments and other hosts' images, and nothing else on the server`() {
        assertTrue(wiki.viewable("${base}attachments/a1"))
        assertTrue(wiki.viewable("${base}attachments/a1/thumbnail"))
        assertTrue(wiki.viewable("https://example.com/p.png"))
        assertFalse(wiki.viewable("/settings/-/x"))
        assertFalse(wiki.viewable("https://server/settings/-/x?y=1"))
        assertFalse(wiki.viewable("https://server/wikis/w1/-/delete"))
        assertFalse(wiki.viewable("${base}attachments/a1/../../../settings/-/x"))
        assertFalse(wiki.viewable("${base}attachments/%2e%2e"))
        assertFalse(wiki.viewable("${base}attachments/.."))
    }

    @Test
    fun `a page's images reach the lightbox only when it may load them`() {
        val sources = listOf(
            "attachments/a1", "attachments/../../settings/-/x", "/settings/-/x",
            "https://server/settings/-/x", "https://example.com/p.png", "attachments/a2/thumbnail",
        )
        assertEquals(
            listOf("${base}attachments/a1", "https://example.com/p.png", "${base}attachments/a2"),
            lightboxImages(wiki, sources),
        )
    }

    @Test
    fun `the lightbox asks this wiki's attachments as its own, and another host's image as given`() {
        // Only a server-relative address goes with the session: an absolute one
        // may be a page author's choice even on the user's server.
        assertEquals("/wikis/w1/-/attachments/a1", wiki.local("${base}attachments/a1"))
        assertEquals("https://example.com/p.png", wiki.local("https://example.com/p.png"))
        assertEquals("https://server/settings/-/x", wiki.local("https://server/settings/-/x"))
    }

    private fun image(id: String) = Attachment(id = id, name = "$id.png", type = "image/png")

    // A path, a slash, both dot segments, a plain id and two to escape.
    private val images = listOf("../../../../settings/-/x?y=1", "a/b", "..", ".", "a2?x=1", "a1", "a3#z").map(::image)

    private val address = Regex("/wikis/w1/-/attachments/(a1|a2%3Fx%3D1|a3%23z)(/thumbnail|/preview)?")

    private fun shown(content: @Composable () -> Unit): List<String> {
        rule.setContent {
            CompositionLocalProvider(LocalWikiContext provides wiki) { Column { content() } }
        }
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
        val attachments = requests.filter { "attachments" in it || "settings" in it || "/a/b" in it }
        assertTrue("every request is an escaped address: $attachments", attachments.all { address.matches(it) })
        // Server-relative, so the loader sends them as the wiki's own (#222).
        assertTrue("the plain id was asked for: $attachments", attachments.any { it.startsWith("/wikis/w1/-/attachments/a1") })
        assertTrue("the escaped id was asked for: $attachments", attachments.any { it.startsWith("/wikis/w1/-/attachments/a2%3Fx%3D1") })
    }

    @Test
    fun `a comment asks for no attachment an id points elsewhere`() {
        assertEscaped(shown { CommentAttachments(attachments = images) })
        assertHidden()
    }

    private fun body(mode: AttachmentsViewMode) = shown {
        AttachmentsBody(
            state = AttachmentsUiState(isLoading = false, attachments = images, wiki = WikiInfo(id = "w1"), viewMode = mode),
            baseURL = base, token = null,
            onSearchChange = {}, onClearSearch = {}, onFilterChange = {}, onSortChange = {}, onViewModeChange = {},
            onRefresh = {}, onRequestDelete = {}, onRequestCaption = {}, onRetry = {},
        )
    }

    @Test
    fun `the attachments grid asks for no attachment an id points elsewhere`() {
        assertEscaped(body(AttachmentsViewMode.GRID))
        assertHidden()
    }

    @Test
    fun `the attachments list asks for no attachment an id points elsewhere`() {
        assertEscaped(body(AttachmentsViewMode.LIST))
        assertHidden()
    }
}
