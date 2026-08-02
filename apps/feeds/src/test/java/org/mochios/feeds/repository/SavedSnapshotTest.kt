// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.repository

import org.junit.Assert.assertEquals
import org.mochios.feeds.model.Post
import org.mochios.feeds.model.PostData
import org.mochios.feeds.model.PostSource
import org.mochios.feeds.model.RssData
import org.mochios.feeds.model.Tag
import org.junit.Test

/**
 * The snapshot is what the Saved screen renders, here and on web, so its
 * field mapping has to survive the server's misleading names: `body` is the
 * raw source and `body_markdown` is the RENDERED HTML (feeds.star sets it to
 * markdown(body)). Storing them the other way round feeds raw source into an
 * HTML renderer.
 */
class SavedSnapshotTest {

    private val post = Post(
        id = "post1",
        feed = "feed1",
        feedFingerprint = "abc123def",
        feedName = "Announcements",
        body = "**bold** source",
        bodyMarkdown = "<p><strong>bold</strong> source</p>",
        created = 1700,
        tags = listOf(Tag(id = "t1", label = "Release")),
        data = PostData(rss = RssData(title = "Headline", image = "https://example.test/hero.png")),
    )

    @Test
    fun `the rendered html lands in bodyHtml and the source stays in body`() {
        val snapshot = snapshotOf(post)
        assertEquals("<p><strong>bold</strong> source</p>", snapshot.bodyHtml)
        assertEquals("**bold** source", snapshot.body)
    }

    /** The Saved screen needs data.rss for its title and hero, and tags for the chip row. */
    @Test
    fun `data and tags are carried into the snapshot`() {
        val snapshot = snapshotOf(post)
        assertEquals("Headline", snapshot.data?.rss?.title)
        assertEquals("https://example.test/hero.png", snapshot.data?.rss?.image)
        assertEquals(listOf("Release"), snapshot.tags.map { it.label })
    }

    /** Feeds posts have no author of their own, so the source name stands in. */
    @Test
    fun `an external source names the author`() {
        val fromRss = post.copy(source = PostSource(name = "Example News"))
        assertEquals("Example News", snapshotOf(fromRss).author)
    }

    @Test
    fun `without a source the feed names the author`() {
        assertEquals("Announcements", snapshotOf(post).author)
        assertEquals("Announcements", snapshotOf(post.copy(source = PostSource(name = ""))).author)
    }
}
