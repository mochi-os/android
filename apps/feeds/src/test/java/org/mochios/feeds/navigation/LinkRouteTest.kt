// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkRouteTest {

    @Test
    fun `a post link opens the post`() {
        assertEquals("feeds/post/abc/p1", FeedsApp.linkRoute("abc", "p1", null))
    }

    @Test
    fun `a feed link opens the feed`() {
        assertEquals("feeds/feed/abc", FeedsApp.linkRoute("abc", null, null))
        assertEquals("feeds/feed/abc", FeedsApp.linkRoute("abc", "", null))
    }

    @Test
    fun `a discovery link carries its share link`() {
        assertEquals(
            "feeds/findFeeds?link=mochi%3A%2F%2F12D3KooWPeer%2Fabc",
            FeedsApp.linkRoute("find", null, "mochi://12D3KooWPeer/abc"),
        )
        assertEquals("feeds/findFeeds", FeedsApp.linkRoute("find", null, null))
    }

    @Test
    fun `the bare app link names no screen`() {
        assertNull(FeedsApp.linkRoute(null, null, null))
        assertNull(FeedsApp.linkRoute("", null, null))
    }
}
