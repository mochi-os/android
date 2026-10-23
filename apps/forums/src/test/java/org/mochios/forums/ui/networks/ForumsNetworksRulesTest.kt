// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.networks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.forums.R
import org.mochios.forums.model.Forum
import org.mochios.forums.model.PostEmbed
import org.mochios.forums.model.postEmbed
import org.mochios.forums.ui.components.linkHost
import org.mochios.forums.ui.forum.blockableCommunity
import org.mochios.forums.ui.newpost.languageName
import org.mochios.forums.ui.newpost.linkError
import org.mochios.forums.ui.post.memberServer
import org.mochios.forums.ui.post.remoteMember
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rules the Android forums share with the web client for posts and communities from other networks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForumsNetworksRulesTest {

    @Test
    fun `a post's link is a web address of 2048 characters at most, or nothing`() {
        assertNull(linkError(""))
        assertNull(linkError("  "))
        assertNull(linkError("https://there.example/a"))
        assertNull(linkError("HTTP://there.example"))
        assertEquals(R.string.forums_link_not_web, linkError("ftp://there.example"))
        assertEquals(R.string.forums_link_not_web, linkError("there.example"))
        assertEquals(R.string.forums_link_too_long, linkError("https://there.example/" + "a".repeat(2048)))
    }

    @Test
    fun `a link's card is read from its embed, and a broken one is empty`() {
        assertEquals(PostEmbed(title = "Page", site = "There"), postEmbed("""{"title": "Page", "site": "There"}"""))
        assertEquals(PostEmbed(), postEmbed("{not json"))
        assertEquals(PostEmbed(), postEmbed(""))
        assertEquals("there.example", linkHost("https://www.there.example/page"))
    }

    @Test
    fun `an author from another network is known by their page, and their server by its domain`() {
        assertTrue(remoteMember("https://lemmy.example/u/someone"))
        assertFalse(remoteMember("1localperson"))
        assertEquals("lemmy.example", memberServer("https://lemmy.example/u/someone"))
        assertNull(memberServer("1localperson"))
    }

    @Test
    fun `a community read through a linked account may be blocked, one joined as a hosted account may not`() {
        val linked = Forum(id = "f", account = "l1", address = "!books@lemmy.example")
        assertTrue(blockableCommunity(linked))
        assertFalse(blockableCommunity(linked.copy(hosted = "h1")))
        assertFalse(blockableCommunity(linked.copy(account = "")))
        assertFalse(blockableCommunity(Forum(id = "f")))
    }

    @Test
    fun `a language is offered in its own name`() {
        assertEquals("Français", languageName("fr"))
        assertEquals("Deutsch", languageName("de"))
    }
}
