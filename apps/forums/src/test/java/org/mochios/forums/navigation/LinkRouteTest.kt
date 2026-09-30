// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkRouteTest {

    @Test
    fun `a post link opens the post`() {
        assertEquals("forums/forum/abc/post/p1", ForumsApp.linkRoute("abc", "p1", null))
    }

    @Test
    fun `a forum link opens the forum`() {
        assertEquals("forums/forum/abc", ForumsApp.linkRoute("abc", null, null))
        assertEquals("forums/forum/abc", ForumsApp.linkRoute("abc", "", ""))
    }

    @Test
    fun `a forum reached through its owner's server opens discovery with the share link`() {
        assertEquals(
            "forums/discover?link=mochi%3A%2F%2F12D3KooWPeer%2Fabc",
            ForumsApp.linkRoute("abc", null, "12D3KooWPeer"),
        )
    }

    @Test
    fun `the bare app link names no screen`() {
        assertNull(ForumsApp.linkRoute(null, null, null))
        assertNull(ForumsApp.linkRoute("", null, "12D3KooWPeer"))
    }
}
